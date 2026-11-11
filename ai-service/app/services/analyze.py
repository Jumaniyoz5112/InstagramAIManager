from __future__ import annotations

import logging
from typing import Any

from fastapi import HTTPException

from app.config import ANALYZE_PROMPT_VERSION, MAX_IMAGES, OLLAMA_VISION_MODEL
from app.prompts import ANALYZE_SYSTEM
from app.schemas import AnalyzeRequest
from app.services.ollama import download_as_b64, ollama_chat
from app.services.quality import normalize_nullish

log = logging.getLogger("ai-service.analyze")


def run_analyze(req: AnalyzeRequest) -> dict[str, Any]:
    if not req.imageUrls:
        raise HTTPException(status_code=400, detail="imageUrls required")

    images_b64: list[str] = []
    for url in req.imageUrls[:MAX_IMAGES]:
        try:
            images_b64.append(download_as_b64(url))
        except Exception as e:
            log.warning("Failed to download image %s: %s", url[:80], e)

    if not images_b64:
        raise HTTPException(status_code=400, detail="Could not download any image")

    caption = (req.caption or "").strip()
    user_text = (
        f"Media turi: {req.mediaType}\n"
        f"Caption:\n{caption if caption else '(bo‘sh)'}\n\n"
        "Rasm(lar) va captionni tahlil qiling. "
        "Javob faqat JSON. Barcha qiymatlar o‘zbek lotin yozuvida."
    )

    result = ollama_chat(
        model=OLLAMA_VISION_MODEL,
        system=ANALYZE_SYSTEM,
        user_text=user_text,
        images=images_b64,
        temperature=0.2,
    )
    result.setdefault("schemaVersion", "1.1")
    result = normalize_nullish(result)

    return {
        "schemaVersion": str(result.get("schemaVersion", "1.1")),
        "model": OLLAMA_VISION_MODEL,
        "promptVersion": ANALYZE_PROMPT_VERSION,
        "mediaId": req.mediaId,
        "result": result,
    }
