from __future__ import annotations

import json
import logging
from typing import Any

from fastapi import HTTPException

from app.config import INSIGHTS_PROMPT_VERSION, OLLAMA_TEXT_MODEL
from app.prompts import INSIGHTS_SYSTEM
from app.schemas import InsightsRequest
from app.services.ollama import ollama_chat
from app.services.quality import is_valid_insights, normalize_insights, slim_analysis

log = logging.getLogger("ai-service.insights")


def run_insights(req: InsightsRequest) -> dict[str, Any]:
    if not req.posts:
        raise HTTPException(status_code=400, detail="posts required")
    if len(req.posts) < 2:
        raise HTTPException(status_code=400, detail="at least 2 posts required for corpus insights")

    compact_posts = []
    for idx, p in enumerate(req.posts[:20], start=1):
        compact_posts.append(
            {
                "postIndex": idx,
                "mediaType": p.mediaType,
                "bucket": p.bucket,
                "engagementScore": p.engagementScore,
                "likeCount": p.likeCount,
                "commentsCount": p.commentsCount,
                "sharesCount": p.sharesCount,
                "savesCount": p.savesCount,
                "caption": (p.caption or "")[:220],
                "analysis": slim_analysis(p.analysis),
            }
        )

    user_text = (
        "VAZIFA: corpus dan STRATEGY PATTERN JSON yozing.\n"
        "TAQIQLANGAN: bitta post obyektini (caption/mediaId/analysis) qaytarish.\n"
        "MAJBURIY kalitlar: executiveSummaryUz, winningPatterns, losingPatterns, doNext, avoidNext.\n\n"
        f"Stats:\n{json.dumps(req.stats, ensure_ascii=False)}\n\n"
        f"Corpus posts:\n{json.dumps(compact_posts, ensure_ascii=False)}\n\n"
        "Javob FAQAT insights schema JSON. Qiymatlar o'zbek lotin."
    )
    result = ollama_chat(
        model=OLLAMA_TEXT_MODEL,
        system=INSIGHTS_SYSTEM,
        user_text=user_text,
        images=None,
        temperature=0.2,
    )
    if not is_valid_insights(result):
        log.warning("Insights schema invalid on first pass; retrying. keys=%s", list(result.keys()))
        result = ollama_chat(
            model=OLLAMA_TEXT_MODEL,
            system=INSIGHTS_SYSTEM,
            user_text=(
                "Oldingi javob NOTO'G'RI edi (post obyektini qaytargansiz).\n"
                "Endi FAQAT quyidagi kalitlar bilan insights JSON yozing:\n"
                "executiveSummaryUz, winningPatterns (>=2), losingPatterns (>=1), "
                "doNext (>=2), avoidNext (>=1), confidence.\n"
                "doNext/avoidNext BO'SH bo'lmasin. Bitta postni nusxa qilmang.\n\n"
                f"Stats:\n{json.dumps(req.stats, ensure_ascii=False)}\n\n"
                f"Corpus posts:\n{json.dumps(compact_posts, ensure_ascii=False)}"
            ),
            images=None,
            temperature=0.1,
        )
    if not is_valid_insights(result):
        raise HTTPException(
            status_code=502,
            detail="Model did not return valid corpus insights schema",
        )
    result.setdefault("schemaVersion", "1.1")
    result = normalize_insights(result)

    return {
        "schemaVersion": str(result.get("schemaVersion", "1.1")),
        "model": OLLAMA_TEXT_MODEL,
        "promptVersion": INSIGHTS_PROMPT_VERSION,
        "result": result,
    }
