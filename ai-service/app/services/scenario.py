from __future__ import annotations

import json
import logging
from typing import Any

from fastapi import HTTPException

from app.config import OLLAMA_TEXT_MODEL, SCENARIO_PROMPT_VERSION
from app.prompts import SCENARIO_SYSTEM
from app.schemas import ScenarioRequest
from app.services.ollama import ollama_chat
from app.services.quality import (
    is_minimal_scenario,
    is_valid_insights,
    is_valid_scenario,
    normalize_scenario,
    scenario_quality_issues,
)

log = logging.getLogger("ai-service.scenario")


def run_scenario(req: ScenarioRequest) -> dict[str, Any]:
    if not req.insights:
        raise HTTPException(status_code=400, detail="insights required")
    if not is_valid_insights(req.insights):
        raise HTTPException(status_code=400, detail="insights must include executiveSummaryUz and patterns")

    brief = (req.brief or "").strip()
    brief_line = (
        f"USER BRIEF (ASOSIY MAQSAD — shu mavzuda 9s montaj): {brief}"
        if brief
        else "USER BRIEF: yo‘q — insights doNext dan yangi mavzu tanlang."
    )
    user_text = (
        "9 soniyalik Reels/Story montaj ssenariyasini yozing (schema 1.3).\n"
        "shots: aniq 3 kadr (0-3 hook, 3-6 body, 6-9 cta).\n"
        "Faqat sodda o‘zbek lotin. Ma’nosiz/g‘alati iboralar YO‘Q.\n"
        "overlayTextUz maksimal 10 so‘z. visualUz — kamera/obyekt.\n"
        "Insightsdan faqat USLUB/PATTERN.\n"
        f"{brief_line}\n"
        f"Preferred format: {req.preferredFormat or 'REELS'}\n\n"
        f"Insights JSON:\n{json.dumps(req.insights, ensure_ascii=False)}\n\n"
        "Faqat scenario schema 1.3 JSON."
    )
    result = ollama_chat(
        model=OLLAMA_TEXT_MODEL,
        system=SCENARIO_SYSTEM,
        user_text=user_text,
        images=None,
        temperature=0.55,
        repeat_penalty=1.25,
    )
    result = normalize_scenario(result, brief=brief, preferred_format=req.preferredFormat)
    issues = scenario_quality_issues(result)
    if issues:
        log.warning("Scenario quality issues after normalize: %s; retrying", issues)
        result = ollama_chat(
            model=OLLAMA_TEXT_MODEL,
            system=SCENARIO_SYSTEM,
            user_text=(
                "Oldingi javob yaroqsiz. Qayta yozing — SODDA o‘zbek, 9s / 3 shot.\n"
                "Har shot: startSec,endSec,visualUz,overlayTextUz,purpose.\n"
                "Gallyutsinatsiya iboralar taqiqlanadi. Overlay qisqa.\n"
                f"Muammolar: {', '.join(issues)}\n\n"
                f"{brief_line}\n"
                f"Insights:\n{json.dumps(req.insights, ensure_ascii=False)}"
            ),
            images=None,
            temperature=0.4,
            repeat_penalty=1.3,
        )
        result = normalize_scenario(result, brief=brief, preferred_format=req.preferredFormat)

    if is_valid_scenario(result):
        pass
    elif is_minimal_scenario(result):
        log.warning(
            "Scenario accepted with salvage normalize; remaining issues=%s",
            scenario_quality_issues(result),
        )
        result = normalize_scenario(result, brief=brief, preferred_format=req.preferredFormat)
    else:
        raise HTTPException(
            status_code=502,
            detail=f"Model did not return valid scenario schema: {scenario_quality_issues(result)}",
        )

    result["schemaVersion"] = "1.3"
    return {
        "schemaVersion": "1.3",
        "model": OLLAMA_TEXT_MODEL,
        "promptVersion": SCENARIO_PROMPT_VERSION,
        "result": result,
    }
