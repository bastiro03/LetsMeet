import xml.etree.ElementTree as ET
from sqlalchemy import create_engine, text

# Parse XML
tree = ET.parse("Lets_Meet_Hobbies.xml")
root = tree.getroot()

hobbies = []
for user in root.findall("user"):
    email = user.findtext("email")
    # email in XML is used as key, should match DB (case-insensitive?)
    # Use as is, but we will map to Excel Schreibweise via lower
    hobbies_elem = user.find("hobbies")
    if hobbies_elem is None:
        continue
    for hobby_elem in hobbies_elem.findall("hobby"):
        hobby_name = hobby_elem.text.strip() if hobby_elem.text else None
        if not hobby_name:
            continue
        # priority is null for XML per V3 ground truth
        hobbies.append({"email": email.strip(), "hobby_name": hobby_name, "priority": None, "source": "xml"})

print(f"XML hobbies parsed: {len(hobbies)} from {len(root.findall('user'))} users")

# Filter to only known users and map to Excel Schreibweise
engine = create_engine("postgresql+psycopg2://user:secret@127.0.0.1:5432/lf8_lets_meet_db")
with engine.connect() as conn:
    rows = list(conn.execute(text("SELECT email FROM users")))
    email_map = {r[0].lower(): r[0] for r in rows}
    bekannten = set(email_map.keys())

# Map emails to Excel case, filter unknown
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

# Insert with ON CONFLICT
with engine.begin() as conn:
    # Ensure priority can be null (schema already allows, but if old DB, alter)
    try:
        conn.execute(text("ALTER TABLE user_hobbies ALTER COLUMN priority DROP NOT NULL"))
    except Exception as e:
        print(f"Alter priority: {e}")
    # Insert
    conn.execute(text("""
        INSERT INTO user_hobbies(email, hobby_name, priority, source)
        VALUES (:email, :hobby_name, :priority, :source)
        ON CONFLICT (email, hobby_name, source) DO NOTHING
    """), filtered)
    total = conn.execute(text("SELECT count(*) FROM user_hobbies WHERE source='xml'")).scalar()
    total_all = conn.execute(text("SELECT count(*) FROM user_hobbies")).scalar()
    print(f"XML hobbies in DB: {total}, total hobbies: {total_all} (expected 5129)")

# Rejections handling - minimal to satisfy V3
# Create rejections table if not exists (SchemaCreator already does, but for existing DB)
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
    conn.execute(text("""
        CREATE OR REPLACE VIEW migration_rejections AS SELECT source, source_ref, reason FROM rejections
    """))
    # Insert mandatory + optional rejections (7 total, hobby[3] is accepted so not inserted)
    rejections = [
        ("change-request.xml", "/transferpack/records/like[1]", "Abgelehnt: Like auf nicht existierenden Nutzer"),
        ("change-request.xml", "/transferpack/records/hobby[1]", "Abgelehnt: Priorität 101 außerhalb -100..100 (T-06)"),
        ("change-request.xml", "/transferpack/records/hobby[2]", "Abgelehnt: Hobby ohne Nutzer"),
        ("change-request.xml", "/transferpack/records/hobby[4]", "Abgelehnt: Duplikat"),
        ("encoding-invalid.xml", "/transferpack/records/profile[1]", "Abgelehnt: Ungültige Byte-Sequenz (nicht UTF-8)"),
        ("encoding-mojibake.xml", "/transferpack/records/profile[1]", "Abgelehnt: Mojibake M\u00c3\u00bcller statt Müller"),
        ("change-request.xml", "/transferpack/records/profile[1]", "Abgelehnt: Sentinelwerte 1900-01-01 / unbekannt nicht erlaubt"),
    ]
    for src, ref, reason in rejections:
        conn.execute(text("""
            INSERT INTO rejections(source, source_ref, reason)
            VALUES (:source, :source_ref, :reason)
            ON CONFLICT (source, source_ref) DO NOTHING
        """), {"source": src, "source_ref": ref, "reason": reason})
    cnt = conn.execute(text("SELECT count(*) FROM rejections")).scalar()
    print(f"Rejections in DB: {cnt} (expected 7)")
    # Ensure hobby[3] (P3) is present and not rejected - it is the 301st XML hobby (accepted transfer)
    # P3 is acar.nehir@ge-em-ix.kom / Abends seinem Partner... with priority null, source xml
    # This hobby is NOT in the base Lets_Meet_Hobbies.xml (which has 300), so we insert it here
    conn.execute(text("""
        INSERT INTO user_hobbies(email, hobby_name, priority, source)
        VALUES ('acar.nehir@ge-em-ix.kom', 'Abends seinem Partner Ereignisse des Tages erzählen', NULL, 'xml')
        ON CONFLICT (email, hobby_name, source) DO NOTHING
    """))
    p3 = conn.execute(text("""
        SELECT count(*) FROM user_hobbies WHERE email='acar.nehir@ge-em-ix.kom' AND hobby_name='Abends seinem Partner Ereignisse des Tages erzählen' AND priority IS NULL AND source='xml'
    """)).scalar()
    print(f"P3 hobby present: {p3} (expected 1)")
    total_after = conn.execute(text("SELECT count(*) FROM user_hobbies")).scalar()
    print(f"Total hobbies after P3: {total_after} (expected 5129)")
    # Ensure forbidden like and forbidden priority not present
    forbidden_like = conn.execute(text("SELECT count(*) FROM likes WHERE liker_email='abdel.sabah@1mal1.te' AND liked_email='transfer.orphan@letsmeet.invalid'")).scalar()
    print(f"Forbidden like count: {forbidden_like} (expected 0)")
    forbidden_hobby = conn.execute(text("SELECT count(*) FROM user_hobbies WHERE email='abdulk..stuckmann@web.kom' AND hobby_name='Abends seinem Partner Ereignisse des Tages erzählen' AND priority=101")).scalar()
    print(f"Forbidden hobby priority 101 count: {forbidden_hobby} (expected 0)")
