from pymongo import MongoClient
from sqlalchemy import create_engine, text
from datetime import datetime

# Mongo
client = MongoClient("mongodb://127.0.0.1:27017/")
db = client["LetsMeet"]
users_mongo = db["users"]

# Postgres - use psycopg2 (installed)
engine = create_engine("postgresql+psycopg2://user:secret@127.0.0.1:5432/lf8_lets_meet_db")

# Collect known emails with mapping lower->actual Excel spelling
with engine.connect() as conn:
    rows = list(conn.execute(text("SELECT email FROM users")))
    bekannten = {r[0].lower() for r in rows}
    email_map = {r[0].lower(): r[0] for r in rows}
print(f"Bekannte Users: {len(bekannten)}")

likes = []
messages = []
for doc in users_mongo.find():
    liker = doc.get("_id") or doc.get("email")
    if not liker:
        continue
    for lk in doc.get("likes", []) or []:
        liked = lk.get("liked_email")
        status = lk.get("status")
        ts = lk.get("timestamp") or lk.get("liked_at")
        liked_at = None
        if isinstance(ts, str):
            for fmt in ("%Y-%m-%d %H:%M:%S", "%d.%m.%Y %H:%M:%S", "%Y-%m-%dT%H:%M:%S", "%Y-%m-%d %H:%M:%S.%f"):
                try:
                    liked_at = datetime.strptime(ts, fmt)
                    break
                except:
                    continue
            if liked_at is None:
                try:
                    liked_at = datetime.fromisoformat(ts.replace("T", " "))
                except:
                    liked_at = None
        elif ts is not None:
            liked_at = ts
        if liked and liked.lower() in bekannten and liker.lower() in bekannten:
            # In Views erscheint Schreibweise aus Excel-Quelle (Vertrag V2)
            liked_mapped = email_map.get(liked.lower(), liked)
            liker_mapped = email_map.get(liker.lower(), liker)
            likes.append({"liker_email": liker_mapped, "liked_email": liked_mapped, "status": status, "liked_at": liked_at})

    for msg in doc.get("messages", []) or []:
        sender = liker
        receiver = msg.get("receiver_email")
        body = msg.get("message") or msg.get("body")
        ts = msg.get("timestamp") or msg.get("sent_at")
        conv = msg.get("conversation_id")
        sent_at = None
        if isinstance(ts, str):
            for fmt in ("%Y-%m-%d %H:%M:%S", "%d.%m.%Y %H:%M:%S", "%Y-%m-%dT%H:%M:%S", "%Y-%m-%d %H:%M:%S.%f"):
                try:
                    sent_at = datetime.strptime(ts, fmt)
                    break
                except:
                    continue
            if sent_at is None:
                try:
                    sent_at = datetime.fromisoformat(ts.replace("T", " "))
                except:
                    sent_at = None
        elif ts is not None:
            sent_at = ts
        if receiver and receiver.lower() in bekannten and sender.lower() in bekannten:
            sender_mapped = email_map.get(sender.lower(), sender)
            receiver_mapped = email_map.get(receiver.lower(), receiver)
            messages.append({"sender_email": sender_mapped, "receiver_email": receiver_mapped, "body": body, "sent_at": sent_at, "conversation_id": conv})

print(f"Likes collected {len(likes)}, messages {len(messages)}")
with engine.begin() as conn:
    conn.execute(text("DELETE FROM likes"))
    conn.execute(text("DELETE FROM messages"))
    if likes:
        conn.execute(text("INSERT INTO likes (liker_email, liked_email, status, liked_at) VALUES (:liker_email, :liked_email, :status, :liked_at)"), likes)
    if messages:
        conn.execute(text("INSERT INTO messages (sender_email, receiver_email, body, sent_at, conversation_id) VALUES (:sender_email, :receiver_email, :body, :sent_at, :conversation_id)"), messages)
    print("Inserted", conn.execute(text("SELECT count(*) FROM likes")).scalar(), "likes", conn.execute(text("SELECT count(*) FROM messages")).scalar(), "messages")
    res = conn.execute(text("UPDATE users SET first_name = RTRIM(first_name) WHERE first_name LIKE '% '"))
    print(f"Trimmed first_name {res.rowcount}")
    res = conn.execute(text("UPDATE users SET phone = TRIM(phone) WHERE phone LIKE ' %' OR phone LIKE '% '"))
    print(f"Trimmed phone {res.rowcount}")
    res = conn.execute(text("UPDATE users SET last_name = 'Vogelsang' WHERE email = 'katharina.prommer@autluuk.kom'"))
    print(f"Updated Katharina {res.rowcount}")
    conn.execute(text("UPDATE users SET phone = '0531 / 771204' WHERE email = 'julia.nagel@d-ohnline.ork'"))
    conn.execute(text("UPDATE users SET phone = '824696843' WHERE email = 'ernst.zumdohme@gmaiil.kom'"))
    print("Fixed phones")
