import os
import asyncio
import json

# Add parent directory / current directory to sys.path if needed
import sys
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from fastapi import FastAPI, WebSocket, WebSocketDisconnect, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Optional
import database
import scanner

app = FastAPI(title="Photo Organizer Backend Engine")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.on_event("startup")
def startup_event():
    database.init_db()

class ScanRequest(BaseModel):
    folders: List[str]

class TrashRequest(BaseModel):
    file_path: str

@app.get("/health")
def health_check():
    return {"status": "ok", "service": "photo-organizer-engine"}

@app.get("/photos")
def get_photos(
    folder_path: Optional[str] = None,
    sort_by: str = Query("taken_at", regex="^(taken_at|modified_at|file_path|blur_score|file_size)$"),
    order: str = Query("DESC", regex="^(ASC|DESC|asc|desc)$"),
    limit: int = Query(500, ge=1, le=5000),
    offset: int = Query(0, ge=0)
):
    return database.get_photos(folder_path=folder_path, sort_by=sort_by, order=order, limit=limit, offset=offset)

@app.get("/duplicates/exact")
def get_exact_duplicates():
    return database.get_exact_duplicates()

@app.get("/photos/blur")
def get_blurry_photos(threshold: float = 100.0, limit: int = 200):
    photos = database.get_photos(sort_by="blur_score", order="ASC", limit=limit)
    filtered = [p for p in photos if p.get("blur_score") is not None and p["blur_score"] <= threshold]
    return filtered

@app.post("/photos/trash")
def trash_photo(req: TrashRequest):
    if not os.path.exists(req.file_path):
        database.update_photo_status(req.file_path, "TRASHED")
        return {"status": "success", "message": "File marked as trashed"}

    # Try sending to Windows Recycle Bin / send2trash if installed, or mark in DB
    try:
        import send2trash
        send2trash.send2trash(req.file_path)
    except Exception:
        pass
    database.update_photo_status(req.file_path, "TRASHED")
    return {"status": "success", "file_path": req.file_path}

@app.websocket("/ws/scan")
async def websocket_scan(websocket: WebSocket):
    await websocket.accept()
    try:
        data = await websocket.receive_text()
        request_data = json.loads(data)
        folders = request_data.get("folders", [])

        async def progress_cb(current: int, total: int, message: str):
            await websocket.send_json({
                "type": "progress",
                "current": current,
                "total": total,
                "message": message
            })

        def sync_progress_cb(current: int, total: int, message: str):
            asyncio.run_coroutine_threadsafe(
                progress_cb(current, total, message),
                loop=asyncio.get_event_loop()
            )

        await scanner.scan_folders(folders, progress_cb)
        await websocket.send_json({"type": "complete", "message": "Scan finished successfully"})
    except WebSocketDisconnect:
        print("Scan WebSocket disconnected")
    except Exception as e:
        await websocket.send_json({"type": "error", "message": str(e)})

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="127.0.0.1", port=8000, reload=False)
