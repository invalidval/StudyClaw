import sqlite3
from pathlib import Path
from flask import g

# Always use a DB file next to this module, independent of process working directory.
DATABASE = str(Path(__file__).resolve().parent / 'users.db')

def get_db():
    db = getattr(g, '_database', None)
    if db is None:
        db = g._database = sqlite3.connect(DATABASE)
    return db

def init_db():
    with sqlite3.connect(DATABASE) as db:
        db.execute('''
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT UNIQUE NOT NULL,
                password TEXT NOT NULL
            )
        ''')
        db.execute('''
            CREATE TABLE IF NOT EXISTS questions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                image_path TEXT NOT NULL,
                ocr_text TEXT NOT NULL,
                ai_analysis TEXT NOT NULL,
                summary TEXT NOT NULL,
                subject TEXT NOT NULL,
                difficulty INTEGER NOT NULL,
                create_time INTEGER NOT NULL,
                is_archived INTEGER NOT NULL,
                archive_type TEXT,
                deleted_at INTEGER,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY(user_id) REFERENCES users(id)
            )
        ''')
        db.execute('''
            CREATE INDEX IF NOT EXISTS idx_questions_user_updated_id
            ON questions(user_id, updated_at, id)
        ''')

        # Lightweight migration for existing databases created before deleted_at.
        cols = {row[1] for row in db.execute("PRAGMA table_info(questions)").fetchall()}
        if 'deleted_at' not in cols:
            db.execute('ALTER TABLE questions ADD COLUMN deleted_at INTEGER')

