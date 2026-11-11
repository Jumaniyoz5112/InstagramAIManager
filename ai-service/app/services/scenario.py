from __future__ import annotations

import json
import logging
from typing import Any

from fastapi import HTTPException

from app.config import OLLAMA_TEXT_MODEL, SCENARIO_PROMPT_VERSION
from app.prompts import SCENARIO_SYSTEM
from app.schemas import ScenarioRequest
from app.services.ollama import ollama_chat
from app.services.quality import is_valid_insights, is_valid_scenario, normalize_scenario

log = logging.getLogger("ai-service.scenario")


def run_scenario(req: ScenarioRequest) -> dict[str, Any]:
    if not req.insights:
        raise HTTPException(status_code=400, detail="insights required")
    if not is_valid_insights(req.insights):
        raise HTTPException(status_code=400, detail="insights must include executiveSummaryUz and patterns")

    brief = (req.brief or "").strip()
    brief_line = (
        f"USER BRIEF (ASOSIY MAQSAD — shu mavzuda yangi ssenariya): {brief}"
        if brief
        else "USER BRIEF: yo‘q — insights doNext dan yangi mavzu tanlang."
    )
    user_text = (
        "Professional SMM post rejasini yozing.\n"
        "Maydonlar orasida bir xil gap/so'z birikmasini TAKRORLAMANG.\n"
        "Insightsdan faqat USLUB/PATTERN; yangi matn va kadrlar yarating.\n"
        f"{brief_line}\n"
        "winningPatterns/doNext — uslub; avoidNext — qochish.\n\n"
        f"Preferred format: {req.preferredFormat or 'auto'}\n\n"
        f"Insights JSON:\n{json.dumps(req.insights, ensure_ascii=False)}\n\n"
        "Faqat scenario schema JSON."
    )
    result = ollama_chat(
        model=OLLAMA_TEXT_MODEL,
        system=SCENARIO_SYSTEM,
        user_text=user_text,
        images=None,
        temperature=0.65,
        repeat_penalty=1.2,
    )
    if not is_valid_scenario(result):
        log.warning("Scenario schema/quality invalid; retrying. keys=%s", list(result.keys()))
        result = ollama_chat(
            model=OLLAMA_TEXT_MODEL,
            system=SCENARIO_SYSTEM,
            user_text=(
                "Oldingi javob yaroqsiz (takrorlash yoki bo'sh maydon).\n"
                "Qoida: hook ≠ caption ≠ cta ≠ kadrlar. Har biri boshqa matn.\n"
                "ctaUz faqat buyruq. shotListUz da kamera/obyekt yozing.\n"
                "MAJBURIY: titleUz, goalUz, format, hookUz, captionPrimaryUz, "
                "captionAlternativesUz (2), ctaUz, shotListUz (3), whyThisWorksUz.\n\n"
                f"{brief_line}\n"
                f"Insights:\n{json.dumps(req.insights, ensure_ascii=False)}"
            ),
            images=None,
            temperature=0.55,
            repeat_penalty=1.25,
        )
    if not is_valid_scenario(result):
        raise HTTPException(status_code=502, detail="Model did not return valid scenario schema")
    result.setdefault("schemaVersion", "1.2")
    result = normalize_scenario(result, brief=brief, preferred_format=req.preferredFormat)

    return {
        "schemaVersion": str(result.get("schemaVersion", "1.2")),
        "model": OLLAMA_TEXT_MODEL,
        "promptVersion": SCENARIO_PROMPT_VERSION,
        "result": result,
    }
