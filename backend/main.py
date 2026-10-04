import os
import asyncio
import json

import sys
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from fastapi import FastAPI, WebSocket, WebSocketDisconnect, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
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

class ScanRuleSetModel(BaseModel):
    id: Optional[str] = None
    name: str = "ルールセット"
    folder_paths: List[str] = Field(default_factory=list)
    file_filter_type: str = "ALL"  # "ALL", "CUSTOM_EXT", "NAME_CONTAINS"
    custom_extensions: List[str] = Field(default_factory=list)
    name_substring: str = ""
    include_subfolders: bool = True

class TrashRequest(BaseModel):
    file_path: str

@app.get("/health")
def health_check():
    return {"status": "ok", "service": "photo-organizer-engine"}

@app.get("/photos")
def get_photos(
    folder_path: Optional[str] = None,
    sort_by: str = Query("taken_at", pattern="^(taken_at|modified_at|file_path|blur_score|file_size)$"),
    order: str = Query("DESC", pattern="^(ASC|DESC|asc|desc)$"),
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
        rule_sets_raw = request_data.get("rule_sets", [])

        rule_sets = []
        for rs in rule_sets_raw:
            if isinstance(rs, dict):
                rule_sets.append(ScanRuleSetModel(**rs))

        async def progress_cb(current: int, total: int, message: str):
            await websocket.send_json({
                "type": "progress",
                "current": current,
                "total": total,
                "message": message
            })

        await scanner.scan_folders(rule_sets, progress_cb)
        await websocket.send_json({"type": "complete", "message": "Scan finished successfully"})
    except WebSocketDisconnect:
        print("Scan WebSocket disconnected")
    except Exception as e:
        await websocket.send_json({"type": "error", "message": str(e)})

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="127.0.0.1", port=8000, reload=False)
