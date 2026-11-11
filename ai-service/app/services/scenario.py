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
    extract_brief_topic,
    force_topic_scenario,
    is_minimal_scenario,
    is_off_topic_for_brief,
    is_valid_insights,
    is_valid_scenario,
    normalize_scenario,
    scenario_quality_issues,
)

log = logging.getLogger("ai-service.scenario")


def _style_insights_only(insights: dict[str, Any]) -> dict[str, Any]:
    """Drop topic-heavy summary so old love/lifestyle corpus cannot override USER BRIEF."""
    out: dict[str, Any] = {
        "styleOnly": True,
        "note": "Faqat uslub/pattern. Mavzu USER BRIEF dan.",
    }
    for key in (
        "winningPatterns",
        "losingPatterns",
        "hookInsights",
        "ctaInsights",
        "visualInsights",
        "metricInsights",
        "doNext",
        "avoidNext",
        "confidence",
    ):
        if key in insights and insights[key] is not None:
            out[key] = insights[key]
    return out


def run_scenario(req: ScenarioRequest) -> dict[str, Any]:
    if not req.insights:
        raise HTTPException(status_code=400, detail="insights required")
    if not is_valid_insights(req.insights):
        raise HTTPException(status_code=400, detail="insights must include executiveSummaryUz and patterns")

    brief = (req.brief or "").strip()
    topic = extract_brief_topic(brief)
    style = _style_insights_only(req.insights)

    if brief:
        brief_line = (
            f"USER BRIEF = ASOSIY MAVZU (majburiy).\n"
            f"Brief matni: {brief}\n"
            f"Tozalangan mavzu: {topic or brief}\n"
            "Insightsdagi sevgi/baxt/qahva/estetika mavzusini TAKRORLAMA.\n"
            "Faqat shu brief mavzusida 15s montaj yoz."
        )
    else:
        brief_line = "USER BRIEF: yo‘q — insights doNext dan yangi mavzu tanlang (uslub + yangi g‘oya)."

    user_text = (
        "Schema 1.5 Reels/Story montaj ssenariyasi yozing.\n"
        "shots: 6–8 kadr. Matnlar toza o‘zbek lotin.\n"
        f"{brief_line}\n"
        f"Preferred format: {req.preferredFormat or 'REELS'}\n\n"
        f"STYLE INSIGHTS (mavzu emas):\n{json.dumps(style, ensure_ascii=False)}\n\n"
        "Faqat scenario schema 1.5 JSON."
    )
    result = ollama_chat(
        model=OLLAMA_TEXT_MODEL,
        system=SCENARIO_SYSTEM,
        user_text=user_text,
        images=None,
        temperature=0.45,
        repeat_penalty=1.28,
    )
    result = normalize_scenario(result, brief=brief, preferred_format=req.preferredFormat)
    issues = scenario_quality_issues(result)
    off_topic = bool(brief) and is_off_topic_for_brief(result, brief)
    if issues or off_topic:
        log.warning(
            "Scenario retry: issues=%s off_topic=%s topic=%s",
            issues,
            off_topic,
            topic,
        )
        result = ollama_chat(
            model=OLLAMA_TEXT_MODEL,
            system=SCENARIO_SYSTEM,
            user_text=(
                "Oldingi javob NOTO‘G‘RI MAVZU yoki yaroqsiz edi.\n"
                f"MAJBURIY MAVZU: {topic or brief}\n"
                "Baxt/qahva/mehr/estetika shablonini ISHLATMA.\n"
                "6–8 kadr, har overlay shu mavzuda. titleUz qisqa mavzu nomi.\n"
                f"Muammolar: {', '.join(issues) if issues else 'off_topic'}\n\n"
                f"{brief_line}\n"
                f"STYLE INSIGHTS:\n{json.dumps(style, ensure_ascii=False)}"
            ),
            images=None,
            temperature=0.3,
            repeat_penalty=1.35,
        )
        result = normalize_scenario(result, brief=brief, preferred_format=req.preferredFormat)

    if brief and is_off_topic_for_brief(result, brief):
        log.warning("Scenario still off-topic after retry; forcing topic pack for brief=%s", topic)
        result = force_topic_scenario(result, brief)
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

    result["schemaVersion"] = "1.5"
    return {
        "schemaVersion": "1.5",
        "model": OLLAMA_TEXT_MODEL,
        "promptVersion": SCENARIO_PROMPT_VERSION,
        "result": result,
    }
