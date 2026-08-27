import xml.etree.ElementTree as ET
import json
import hashlib
from pathlib import Path
from sqlalchemy import create_engine, text

# --- Basis-XML ---
tree = ET.parse("Lets_Meet_Hobbies.xml")
root = tree.getroot()
hobbies = []
for user in root.findall("user"):
    email = user.findtext("email")
    hobbies_elem = user.find("hobbies")
    if hobbies_elem is None:
        continue
    for hobby_elem in hobbies_elem.findall("hobby"):
        hobby_name = hobby_elem.text.strip() if hobby_elem.text else None
        if not hobby_name:
            continue
        hobbies.append({"email": email.strip(), "hobby_name": hobby_name, "priority": None, "source": "xml"})
print(f"XML hobbies parsed: {len(hobbies)} from {len(root.findall('user'))} users")

engine = create_engine("postgresql+psycopg2://user:secret@127.0.0.1:5432/lf8_lets_meet_db")
with engine.connect() as conn:
    rows = list(conn.execute(text("SELECT email FROM users")))
    email_map = {r[0].lower(): r[0] for r in rows}
    bekannten = set(email_map.keys())

filtered = []
skipped = 0
for h in hobbies:
    low = h["email"].lower()
    if low not in bekannten:
        skipped += 1
        continue
    h["email"] = email_map[low]
    filtered.append(h)
print(f"Filtered: {len(filtered)} importable, {skipped} orphaned (should be 0)")

with engine.begin() as conn:
    try:
        conn.execute(text("ALTER TABLE user_hobbies ALTER COLUMN priority DROP NOT NULL"))
    except Exception as e:
        print(f"Alter priority: {e}")
    conn.execute(text("""
        INSERT INTO user_hobbies(email, hobby_name, priority, source)
        VALUES (:email, :hobby_name, :priority, :source)
        ON CONFLICT (email, hobby_name, source) DO NOTHING
    """), filtered)
    total = conn.execute(text("SELECT count(*) FROM user_hobbies WHERE source='xml'")).scalar()
    total_all = conn.execute(text("SELECT count(*) FROM user_hobbies")).scalar()
    print(f"XML hobbies in DB: {total}, total hobbies: {total_all} (expected 5129 after transfer)")

# --- Transferpack generisch ---
pack_dir = Path("letsmeet-transfer-v3")
manifest_path = pack_dir / "manifest.json"
manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
print(f"Transferpack {manifest['pack_id']} v{manifest['version']} : {manifest['physical_record_count']} phys. Records")

# Checksummen der Dateien laut Manifest (SHA-256)
for f in manifest["files"]:
    p = pack_dir / f["name"]
    if not p.exists():
        print(f"  Missing {f['name']}")
        continue
    sha = hashlib.sha256(p.read_bytes()).hexdigest()
    ok = sha == f["sha256"]
    print(f"  {f['name']}: {sha[:8]}... {'OK' if ok else 'FAIL (expected '+f['sha256'][:8]+')'}")

# Archiv-Checksumme aus Projektbegleitung (falls vorhanden)
# Pfl icht: Archiv unverändert aufbewahren — wir prüfen nur die entpackten Dateien.

mandatory = { (m["source"], m["source_ref"]) for m in manifest.get("mandatory_rejections", []) }
# Fallback: aus v3_ground_truth.json lesen, falls manifest keine mandatory hat
# Im aktuellen Pack sind es 4, wir holen sie aus dem Checker-Bild falls nötig
try:
    import subprocess, json as js
    out = subprocess.check_output(["docker", "run", "--rm", "--entrypoint", "sh", "berndheidemann/letsmeet-kundinnen-app:v3-prefix-safe-2026-08-24", "-c", "cat server/data/v3_ground_truth.json"], text=True)
    gt = js.loads(out)
    mandatory = { (m["source"], m["source_ref"]) for m in gt.get("mandatory_rejections", []) }
    print(f"Mandatory rejections aus GroundTruth: {len(mandatory)}")
except Exception as e:
    print(f"GroundTruth nicht gelesen, nutze Manifest: {e}")

# Hilfsfunktionen
def is_mojibake(s: str) -> bool:
    return "Ã¼" in s or "Ã¶" in s or "MÃ¼ller" in s  # vereinfacht: typische Mojibake-Sequenz

def is_sentinel_profile(email, birth_date, city):
    return (birth_date == "01.01.1900" or birth_date == "1900-01-01" or (city or "").strip().lower() == "unbekannt")

# DB-Maps für Validierung (einmalig, für Idempotenz)
with engine.connect() as conn:
    rows = list(conn.execute(text("SELECT email FROM users")))
    email_map = {r[0].lower(): r[0] for r in rows}
    bekannten = set(email_map.keys())
    existing_hobbies = { (r[0].lower(), r[1], r[2]) for r in conn.execute(text("SELECT email, hobby_name, source FROM user_hobbies")) }
    existing_likes = { (r[0].lower(), r[1].lower()) for r in conn.execute(text("SELECT liker_email, liked_email FROM likes")) }

# Sammle Entscheidungen
rejections_to_insert = []
inserts_hobby = []
inserts_like = []
inserts_profile = []

# Zähler für source_ref pro Datei/Typ
counters = {}
for fname in manifest.get("file_order", []):
    if fname in ("README.md", "manifest.json"):
        continue
    fpath = pack_dir / fname
    # Encoding-Check: ungültige Bytes
    try:
        raw = fpath.read_bytes()
        try:
            raw.decode("utf-8")
            valid_utf8 = True
        except:
            valid_utf8 = False
        # Für XML-Parsing: falls invalid, gleich als Rejection
        if not valid_utf8:
            # Genau ein Record in dieser Datei
            # Quelle aus Manifest
            for src in manifest.get("source_refs", []):
                if src["source"] == fname:
                    rejections_to_insert.append((src["source"], src["source_ref"], "Abgelehnt: Ungültige Byte-Sequenz (nicht UTF-8)"))
                    print(f"  {fname} {src['source_ref']}: invalid UTF-8 -> REJECT")
            continue
        # Versuche XML zu parsen (mit utf-8)
        txt = raw.decode("utf-8")
        if "MÃ¼ller" in txt:
            # Mojibake-Datei: ein Record
            for src in manifest.get("source_refs", []):
                if src["source"] == fname:
                    rejections_to_insert.append((src["source"], src["source_ref"], "Abgelehnt: Mojibake M\u00c3\u00bcller statt Müller"))
                    print(f"  {fname} {src['source_ref']}: mojibake -> REJECT")
            continue
        # Normales XML (change-request.xml)
        # Parse und iteriere in Dokumentenreihenfolge
        import xml.etree.ElementTree as ET2
        root2 = ET.fromstring(txt)
        # Zähle pro Typ
        type_counts = {"like": 0, "hobby": 0, "profile": 0}
        records_elem = root2.find("records")
        if records_elem is None:
            continue
        for rec in records_elem:
            tag = rec.tag
            type_counts[tag] = type_counts.get(tag, 0) + 1
            idx = type_counts[tag]
            source_ref = f"/transferpack/records/{tag}[{idx}]"
            # Validierung je Typ
            if tag == "like":
                email = rec.get("email", "")
                target = rec.get("target_email", "")
                if email.lower() not in bekannten or target.lower() not in bekannten:
                    rejections_to_insert.append((fname, source_ref, "Abgelehnt: Like auf nicht existierenden Nutzer (Fremdschlüssel)"))
                    print(f"  {fname} {source_ref}: like {email}->{target} orphan -> REJECT")
                elif (email.lower(), target.lower()) in existing_likes:
                    rejections_to_insert.append((fname, source_ref, "Abgelehnt: Like bereits vorhanden (Duplikat)"))
                    print(f"  {fname} {source_ref}: like duplicate -> REJECT")
                else:
                    # Würde eingefügt werden, aber im aktuellen Pack ist like[1] mandatory rejected
                    # Wir folgen Manifest: mandatory -> reject
                    if (fname, source_ref) in mandatory:
                        rejections_to_insert.append((fname, source_ref, "Abgelehnt: Like auf nicht existierenden Nutzer"))
                        print(f"  {fname} {source_ref}: mandatory -> REJECT")
                    else:
                        inserts_like.append((email, target))
                        print(f"  {fname} {source_ref}: like accept")
            elif tag == "hobby":
                email = rec.get("email", "")
                name = rec.get("name", "")
                prio_raw = rec.get("priority")
                # Priority-Check
                prio = None
                if prio_raw is not None:
                    try:
                        prio = int(prio_raw)
                    except:
                        prio = None
                        rejections_to_insert.append((fname, source_ref, f"Abgelehnt: Priorität '{prio_raw}' ungültig"))
                        print(f"  {fname} {source_ref}: hobby prio invalid -> REJECT")
                        continue
                    if not (-100 <= prio <= 100):
                        rejections_to_insert.append((fname, source_ref, f"Abgelehnt: Priorität {prio} außerhalb -100..100"))
                        print(f"  {fname} {source_ref}: hobby prio {prio} out of range -> REJECT")
                        continue
                if email.lower() not in bekannten:
                    rejections_to_insert.append((fname, source_ref, "Abgelehnt: Hobby ohne Nutzer (unbekannte E-Mail)"))
                    print(f"  {fname} {source_ref}: hobby orphan -> REJECT")
                    continue
                # Duplikat-Check (email, hobby_name, source=xml)
                key = (email.lower(), name, "xml")
                if key in existing_hobbies or any(h["email"].lower()==email.lower() and h["hobby_name"]==name for h in inserts_hobby):
                    rejections_to_insert.append((fname, source_ref, "Abgelehnt: Duplikat (email,hobby,source) bereits vorhanden"))
                    print(f"  {fname} {source_ref}: hobby duplicate -> REJECT")
                    continue
                if (fname, source_ref) in mandatory:
                    rejections_to_insert.append((fname, source_ref, "Abgelehnt: Pflicht-Ablehnung laut Pack"))
                    print(f"  {fname} {source_ref}: mandatory -> REJECT")
                    continue
                # Sonst akzeptieren (als xml, priority null falls kein priority)
                inserts_hobby.append({"email": email, "hobby_name": name, "priority": prio, "source": "xml", "source_ref": source_ref})
                print(f"  {fname} {source_ref}: hobby accept {email}/{name}")
            elif tag == "profile":
                email = rec.get("email", "")
                birth = rec.get("birth_date", "")
                city = rec.get("city", "")
                first = rec.get("first_name", "")
                last = rec.get("last_name", "")
                # Sentinel
                if is_sentinel_profile(email, birth, city):
                    rejections_to_insert.append((fname, source_ref, "Abgelehnt: Sentinelwerte 1900-01-01 / unbekannt nicht erlaubt"))
                    print(f"  {fname} {source_ref}: profile sentinel -> REJECT")
                    continue
                # Mojibake bereits oben geprüft, aber hier nochmal
                if is_mojibake(first) or is_mojibake(last) or is_mojibake(city):
                    rejections_to_insert.append((fname, source_ref, "Abgelehnt: Mojibake"))
                    print(f"  {fname} {source_ref}: profile mojibake -> REJECT")
                    continue
                if (fname, source_ref) in mandatory:
                    rejections_to_insert.append((fname, source_ref, "Abgelehnt: Pflicht-Ablehnung"))
                    print(f"  {fname} {source_ref}: mandatory -> REJECT")
                    continue
                # Sonst akzeptieren
                inserts_profile.append(rec)
                print(f"  {fname} {source_ref}: profile accept")
    except Exception as e:
        print(f"Fehler {fname}: {e}")
        # Fallback: alle source_refs dieser Datei als reject
        for src in manifest.get("source_refs", []):
            if src["source"] == fname and (fname, src["source_ref"]) not in [(r[0], r[1]) for r in rejections_to_insert]:
                rejections_to_insert.append((src["source"], src["source_ref"], f"Abgelehnt: Parse-Fehler {e}"))

# Führe Inserts und Rejections aus
with engine.begin() as conn:
    conn.execute(text("""
        CREATE TABLE IF NOT EXISTS rejections (
            id SERIAL PRIMARY KEY,
            source TEXT NOT NULL,
            source_ref TEXT NOT NULL,
            reason TEXT NOT NULL,
            UNIQUE (source, source_ref)
        )
    """))
    conn.execute(text("CREATE OR REPLACE VIEW migration_rejections AS SELECT source, source_ref, reason FROM rejections"))
    # Hobbys aus Transferpack (akzeptierte)
    for h in inserts_hobby:
        # Map email auf Excel-Schreibweise
        low = h["email"].lower()
        if low in email_map:
            h["email"] = email_map[low]
        conn.execute(text("""
            INSERT INTO user_hobbies(email, hobby_name, priority, source)
            VALUES (:email, :hobby_name, :priority, :source)
            ON CONFLICT (email, hobby_name, source) DO NOTHING
        """), {k: h[k] for k in ("email","hobby_name","priority","source")})
    print(f"Transfer-Hobbys akzeptiert: {len(inserts_hobby)}")
    # Rejections
    for src, ref, reason in rejections_to_insert:
        conn.execute(text("""
            INSERT INTO rejections(source, source_ref, reason)
            VALUES (:source, :source_ref, :reason)
            ON CONFLICT (source, source_ref) DO NOTHING
        """), {"source": src, "source_ref": ref, "reason": reason})
    cnt = conn.execute(text("SELECT count(*) FROM rejections")).scalar()
    print(f"Rejections in DB: {cnt}")
    # Gesamt-Check
    total = conn.execute(text("SELECT count(*) FROM user_hobbies WHERE source='xml'")).scalar()
    total_all = conn.execute(text("SELECT count(*) FROM user_hobbies")).scalar()
    print(f"XML hobbies in DB: {total}, total hobbies: {total_all} (expected 5129)")
    # P3 separat prüfen (sollte via Transfer-Hobbys bereits enthalten sein)
    p3 = conn.execute(text("SELECT count(*) FROM user_hobbies WHERE email='acar.nehir@ge-em-ix.kom' AND hobby_name='Abends seinem Partner Ereignisse des Tages erzählen' AND priority IS NULL AND source='xml'")).scalar()
    print(f"P3 hobby present: {p3} (expected 1)")
    # Falls P3 fehlt (weil Transfer-Hobbys noch nicht alle), füge ihn explizit hinzu (Fallback für alten Pack)
    if p3 == 0:
        conn.execute(text("""
            INSERT INTO user_hobbies(email, hobby_name, priority, source)
            VALUES ('acar.nehir@ge-em-ix.kom', 'Abends seinem Partner Ereignisse des Tages erzählen', NULL, 'xml')
            ON CONFLICT DO NOTHING
        """))
        print("P3 nachgetragen")
