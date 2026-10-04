import sqlite3
import os
from typing import Optional, List, Dict, Any

DB_NAME = os.environ.get("PHOTO_ORGANIZER_DB", "photo_organizer.db")

def get_connection():
    conn = sqlite3.connect(DB_NAME, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    # Enable WAL mode for concurrent access without locking issues
    conn.execute("PRAGMA journal_mode=WAL;")
    return conn

def init_db():
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS photos (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        file_path TEXT UNIQUE NOT NULL,
        folder_path TEXT NOT NULL,
        file_size INTEGER NOT NULL,
        modified_at INTEGER NOT NULL,
        taken_at INTEGER,
        width INTEGER,
        height INTEGER,
        thumbnail_path TEXT,
        exact_hash TEXT,
        phash TEXT,
        blur_score REAL,
        embedding BLOB,
        group_id INTEGER,
        status TEXT DEFAULT 'ACTIVE'
    );
    """)
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_photos_folder ON photos(folder_path);")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_photos_exact_hash ON photos(exact_hash);")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_photos_blur_score ON photos(blur_score);")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_photos_group_id ON photos(group_id);")
    conn.commit()
    conn.close()

def upsert_photo(photo_data: Dict[str, Any]):
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("""
    INSERT INTO photos (
        file_path, folder_path, file_size, modified_at, taken_at,
        width, height, thumbnail_path, exact_hash, phash, blur_score, group_id, status
    ) VALUES (
        :file_path, :folder_path, :file_size, :modified_at, :taken_at,
        :width, :height, :thumbnail_path, :exact_hash, :phash, :blur_score, :group_id, :status
    ) ON CONFLICT(file_path) DO UPDATE SET
        file_size=excluded.file_size,
        modified_at=excluded.modified_at,
        taken_at=excluded.taken_at,
        width=excluded.width,
        height=excluded.height,
        thumbnail_path=excluded.thumbnail_path,
        exact_hash=excluded.exact_hash,
        phash=excluded.phash,
        blur_score=excluded.blur_score,
        group_id=excluded.group_id,
        status=excluded.status;
    """, photo_data)
    conn.commit()
    conn.close()

def get_photos(
    folder_path: Optional[str] = None,
    extension: Optional[str] = None,
    min_size: Optional[int] = None,
    max_size: Optional[int] = None,
    sort_by: str = "taken_at",
    order: str = "DESC",
    limit: int = 500,
    offset: int = 0
) -> List[Dict[str, Any]]:
    conn = get_connection()
    cursor = conn.cursor()

    query = "SELECT * FROM photos WHERE status = 'ACTIVE'"
    params = []

    if folder_path:
        query += " AND folder_path = ?"
        params.append(folder_path)

    if extension:
        ext = extension.strip().lower()
        if not ext.startswith("."):
            ext = f".{ext}"
        query += " AND LOWER(file_path) LIKE ?"
        params.append(f"%{ext}")

    if min_size is not None:
        query += " AND file_size >= ?"
        params.append(min_size)

    if max_size is not None:
        query += " AND file_size <= ?"
        params.append(max_size)

    allowed_sorts = {
        "taken_at": "taken_at",
        "modified_at": "modified_at",
        "file_path": "file_path",
        "file_name": "file_path",
        "blur_score": "blur_score",
        "file_size": "file_size"
    }
    sort_column = allowed_sorts.get(sort_by, "taken_at")
    sort_order = "ASC" if order.upper() == "ASC" else "DESC"

    query += f" ORDER BY {sort_column} {sort_order} LIMIT ? OFFSET ?"
    params.extend([limit, offset])

    cursor.execute(query, params)
    rows = cursor.fetchall()
    conn.close()
    return [dict(r) for r in rows]

def get_exact_duplicates() -> List[Dict[str, Any]]:
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("""
    SELECT exact_hash, COUNT(*) as count
    FROM photos
    WHERE status = 'ACTIVE' AND exact_hash IS NOT NULL AND exact_hash != ''
    GROUP BY exact_hash
    HAVING count > 1
    """)
    duplicate_hashes = [row["exact_hash"] for row in cursor.fetchall()]

    result = []
    for h in duplicate_hashes:
        cursor.execute("SELECT * FROM photos WHERE exact_hash = ? AND status = 'ACTIVE'", (h,))
        photos = [dict(r) for r in cursor.fetchall()]
        result.append({
            "hash": h,
            "photos": photos
        })
    conn.close()
    return result

def update_photo_status(file_path: str, status: str):
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("UPDATE photos SET status = ? WHERE file_path = ?", (status, file_path))
    conn.commit()
    conn.close()
