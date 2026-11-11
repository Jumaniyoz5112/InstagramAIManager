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
        f"USER BRIEF (ASOSIY MAQSAD — shu mavzuda 15s to‘liq montaj): {brief}"
        if brief
        else "USER BRIEF: yo‘q — insights doNext dan yangi mavzu tanlang."
    )
    user_text = (
        "15 soniyalik Reels/Story TO‘LIQ montaj ssenariyasini yozing (schema 1.4).\n"
        "shots: KAMIDA 6, ko‘pi bilan 8 kadr (3 ta YETMAS). Har kadr ~2s.\n"
        "Matnlarni MUHARRIR kabi tekshiring — faqat toza, tushunarli o‘zbek lotin.\n"
        "Ma’nosiz/g‘alati iboralar YO‘Q. overlayTextUz 4–9 so‘z.\n"
        "Har kadr visualUz boshqacha. Oxirgi kadr = CTA.\n"
        "Insightsdan faqat USLUB/PATTERN.\n"
        f"{brief_line}\n"
        f"Preferred format: {req.preferredFormat or 'REELS'}\n\n"
        f"Insights JSON:\n{json.dumps(req.insights, ensure_ascii=False)}\n\n"
        "Faqat scenario schema 1.4 JSON."
    )
    result = ollama_chat(
        model=OLLAMA_TEXT_MODEL,
        system=SCENARIO_SYSTEM,
        user_text=user_text,
        images=None,
        temperature=0.5,
        repeat_penalty=1.28,
    )
    result = normalize_scenario(result, brief=brief, preferred_format=req.preferredFormat)
    issues = scenario_quality_issues(result)
    if issues:
        log.warning("Scenario quality issues after normalize: %s; retrying", issues)
        result = ollama_chat(
            model=OLLAMA_TEXT_MODEL,
            system=SCENARIO_SYSTEM,
            user_text=(
                "Oldingi javob yaroqsiz. Qayta yozing — TOZA o‘zbek, 15s, 6–8 shot.\n"
                "Har shot: startSec,endSec,visualUz,overlayTextUz,purpose,noteUz.\n"
                "So‘zlarni to‘g‘ri tuzing. Gallyutsinatsiya taqiqlanadi.\n"
                "3 kadr yetarli emas — kamida 6 ta yozing.\n"
                f"Muammolar: {', '.join(issues)}\n\n"
                f"{brief_line}\n"
                f"Insights:\n{json.dumps(req.insights, ensure_ascii=False)}"
            ),
            images=None,
            temperature=0.35,
            repeat_penalty=1.32,
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

    result["schemaVersion"] = "1.4"
    return {
        "schemaVersion": "1.4",
        "model": OLLAMA_TEXT_MODEL,
        "promptVersion": SCENARIO_PROMPT_VERSION,
        "result": result,
    }
