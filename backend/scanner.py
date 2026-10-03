import os
import hashlib
import time
import asyncio
from typing import List, Dict, Any, Callable, Optional
from PIL import Image, ExifTags
import cv2
import imagehash
from database import upsert_photo, get_connection

THUMBNAIL_DIR = os.path.join(os.path.expanduser("~"), ".photo_organizer", "thumbnails")
os.makedirs(THUMBNAIL_DIR, exist_ok=True)

SUPPORTED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}

def compute_xxhash_or_sha256(filepath: str) -> str:
    hasher = hashlib.sha256()
    with open(filepath, 'rb') as f:
        while chunk := f.read(65536):
            hasher.update(chunk)
    return hasher.hexdigest()

def calculate_blur_score(filepath: str) -> float:
    try:
        image = cv2.imread(filepath)
        if image is None:
            return 0.0
        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        score = cv2.Laplacian(gray, cv2.CV_64F).var()
        return float(score)
    except Exception:
        return 0.0

def generate_thumbnail_and_phash(filepath: str) -> tuple[str, str, int, int, float, Optional[int]]:
    try:
        with Image.open(filepath) as img:
            width, height = img.size

            # Exif date extraction
            taken_at = None
            exif_data = img._getexif() if hasattr(img, '_getexif') and img._getexif() else None
            if exif_data:
                for tag_id, value in exif_data.items():
                    tag_name = ExifTags.TAGS.get(tag_id, tag_id)
                    if tag_name == 'DateTimeOriginal' or tag_name == 'DateTime':
                        try:
                            # Format: "YYYY:MM:DD HH:MM:SS"
                            taken_time = time.strptime(str(value), "%Y:%m:%d %H:%M:%S")
                            taken_at = int(time.mktime(taken_time))
                            break
                        except Exception:
                            pass

            # Calculate pHash
            phash_str = str(imagehash.phash(img))

            # Generate WebP thumbnail
            filename_hash = hashlib.md5(filepath.encode('utf-8')).hexdigest()
            thumb_path = os.path.join(THUMBNAIL_DIR, f"{filename_hash}.webp")

            if not os.path.exists(thumb_path):
                img_copy = img.copy()
                img_copy.thumbnail((300, 300))
                img_copy.save(thumb_path, "WEBP", quality=80)

            blur_score = calculate_blur_score(filepath)
            return thumb_path, phash_str, width, height, blur_score, taken_at
    except Exception as e:
        filename_hash = hashlib.md5(filepath.encode('utf-8')).hexdigest()
        thumb_path = os.path.join(THUMBNAIL_DIR, f"{filename_hash}.webp")
        return thumb_path, "", 0, 0, 0.0, None

async def scan_folders(folder_paths: List[str], progress_callback: Callable[[int, int, str], None]):
    all_files = []
    for folder in folder_paths:
        if not os.path.exists(folder):
            continue
        for root, _, files in os.walk(folder):
            for file in files:
                ext = os.path.splitext(file)[1].lower()
                if ext in SUPPORTED_EXTENSIONS:
                    all_files.append(os.path.join(root, file))

    total = len(all_files)
    await progress_callback(0, total, "Scanning photo files...")

    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT file_path, modified_at FROM photos")
    existing_records = {row[0]: row[1] for row in cursor.fetchall()}
    conn.close()

    for idx, filepath in enumerate(all_files, start=1):
        try:
            stat = os.stat(filepath)
            mtime = int(stat.st_mtime)
            size = stat.st_size
            folder = os.path.dirname(filepath)

            # Differential scan check
            if filepath in existing_records and existing_records[filepath] == mtime:
                if idx % 50 == 0 or idx == total:
                    await progress_callback(idx, total, f"Skipping unchanged ({idx}/{total})")
                continue

            exact_hash = compute_xxhash_or_sha256(filepath)
            thumb_path, phash_str, width, height, blur_score, taken_at = generate_thumbnail_and_phash(filepath)

            photo_data = {
                "file_path": filepath,
                "folder_path": folder,
                "file_size": size,
                "modified_at": mtime,
                "taken_at": taken_at or mtime,
                "width": width,
                "height": height,
                "thumbnail_path": thumb_path,
                "exact_hash": exact_hash,
                "phash": phash_str,
                "blur_score": blur_score,
                "group_id": None,
                "status": "ACTIVE"
            }
            upsert_photo(photo_data)

            if idx % 10 == 0 or idx == total:
                await progress_callback(idx, total, f"Processed {idx}/{total}")
                await asyncio.sleep(0)
        except Exception as e:
            print(f"Error processing {filepath}: {e}")
