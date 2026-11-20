from __future__ import annotations

import json
import logging
import time
from typing import Any

from fastapi import HTTPException

from app.config import GROQ_TEXT_MODEL, OLLAMA_TEXT_MODEL, SCENARIO_PROMPT_VERSION
from app.prompts import SCENARIO_SYSTEM
from app.schemas import ScenarioRequest
from app.services.groq import groq_configured, groq_json_chat
from app.services.ollama import ollama_chat
from app.services.quality import (
    extract_brief_topic,
    force_topic_scenario,
    is_minimal_scenario,
    is_off_topic_for_brief,
    is_valid_insights,
    is_valid_scenario,
    normalize_scenario,
    pick_topic_variant,
    scenario_quality_issues,
)

log = logging.getLogger("ai-service.scenario")


def scenario_model_name() -> str:
    return GROQ_TEXT_MODEL if groq_configured() else OLLAMA_TEXT_MODEL


def _scenario_chat(*, system: str, user_text: str, temperature: float, repeat_penalty: float) -> dict[str, Any]:
    if groq_configured():
        return groq_json_chat(system=system, user_text=user_text, temperature=temperature)
    return ollama_chat(
        model=OLLAMA_TEXT_MODEL,
        system=system,
        user_text=user_text,
        images=None,
        temperature=temperature,
        repeat_penalty=repeat_penalty,
    )

_ANGLES = (
    "boshqa burchak / yangi misol",
    "bitta aniq qoida chuqurroq",
    "savol-javob uslubida",
    "xato vs to'g'ri solishtirish",
    "3 soniyalik eslatma uslubi",
)


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
    salt = f"run-{time.time_ns()}"
    # Hint a fresh angle so the model does not clone the previous answer.
    hint_variant = pick_topic_variant(brief, salt=salt) if brief else None
    angle = (hint_variant or {}).get("angle") or _ANGLES[int(time.time()) % len(_ANGLES)]

    if brief:
        brief_line = (
            f"USER BRIEF = ASOSIY MAVZU (majburiy).\n"
            f"Brief matni: {brief}\n"
            f"Tozalangan mavzu: {topic or brief}\n"
            f"YANGI BURCHAK (majburiy, oldingi ssenariyani nusxa qilma): {angle}\n"
            "Insightsdagi sevgi/baxt/qahva/estetika mavzusini TAKRORLAMA.\n"
            "Har safar boshqa hook, boshqa misol, boshqa CTA yoz."
        )
    else:
        brief_line = (
            "USER BRIEF: yo‘q — insights doNext dan yangi mavzu tanlang.\n"
            f"YANGI BURCHAK: {angle}"
        )

    user_text = (
        "Schema 1.5 Reels/Story montaj ssenariyasi yozing.\n"
        "shots: 6–8 kadr. Matnlar toza o‘zbek lotin.\n"
        "Muhim: OLDINGI javobni takrorlama — yangi variant bo‘lsin.\n"
        f"{brief_line}\n"
        f"Preferred format: {req.preferredFormat or 'REELS'}\n\n"
        f"STYLE INSIGHTS (mavzu emas):\n{json.dumps(style, ensure_ascii=False)}\n\n"
        "Faqat scenario schema 1.5 JSON."
    )
    result = _scenario_chat(
        system=SCENARIO_SYSTEM,
        user_text=user_text,
        temperature=0.75,
        repeat_penalty=1.22,
    )
    result["_variantSalt"] = salt
    result = normalize_scenario(result, brief=brief, preferred_format=req.preferredFormat)
    issues = scenario_quality_issues(result)
    off_topic = bool(brief) and is_off_topic_for_brief(result, brief)
    # Lexicon misses on a complete draft are not worth a second paid call.
    # Groq Free allows 8K tokens/minute; an immediate retry returns 429 and the UI shows 502.
    lexicon_only = bool(issues) and all(item.endswith("_gibberish") for item in issues)
    if (issues or off_topic) and not (lexicon_only and is_minimal_scenario(result) and not off_topic):
        log.warning(
            "Scenario retry: issues=%s off_topic=%s topic=%s angle=%s",
            issues,
            off_topic,
            topic,
            angle,
        )
        first_draft = result
        try:
            result = _scenario_chat(
                system=SCENARIO_SYSTEM,
                user_text=(
                    "Oldingi javob NOTO‘G‘RI MAVZU yoki yaroqsiz edi.\n"
                    f"MAJBURIY MAVZU: {topic or brief}\n"
                    f"YANGI BURCHAK: {angle} — boshqa so‘zlar bilan yoz.\n"
                    "Baxt/qahva/mehr/estetika shablonini ISHLATMA.\n"
                    "6–8 kadr, har overlay shu mavzuda. titleUz qisqa mavzu nomi.\n"
                    f"Muammolar: {', '.join(issues) if issues else 'off_topic'}\n\n"
                    f"{brief_line}\n"
                    f"STYLE INSIGHTS:\n{json.dumps(style, ensure_ascii=False)}"
                ),
                temperature=0.65,
                repeat_penalty=1.28,
            )
            result["_variantSalt"] = f"{salt}-retry"
            result = normalize_scenario(result, brief=brief, preferred_format=req.preferredFormat)
        except HTTPException as exc:
            if exc.status_code == 429 and is_minimal_scenario(first_draft):
                log.warning("Scenario retry hit rate limit; keeping first draft")
                result = first_draft
                result["_variantSalt"] = f"{salt}-rate-limit"
            else:
                raise

    if brief and is_off_topic_for_brief(result, brief):
        log.warning(
            "Scenario still off-topic after retry; rotating fallback variant topic=%s",
            topic,
        )
        result = force_topic_scenario(result, brief, salt=f"{salt}-force")
        result["_variantSalt"] = f"{salt}-forced"
        result = normalize_scenario(result, brief=brief, preferred_format=req.preferredFormat)

    if is_valid_scenario(result):
        pass
    elif is_minimal_scenario(result) and not (brief and is_off_topic_for_brief(result, brief)):
        # Accept usable on-topic drafts even if lexicon gate is noisy.
        log.warning(
            "Scenario accepted with soft salvage; remaining issues=%s",
            scenario_quality_issues(result),
        )
        result["_variantSalt"] = f"{salt}-soft"
        result = normalize_scenario(result, brief=brief, preferred_format=req.preferredFormat)
    elif is_minimal_scenario(result):
        result = force_topic_scenario(result, brief, salt=f"{salt}-last")
        result["_variantSalt"] = f"{salt}-last"
        result = normalize_scenario(result, brief=brief, preferred_format=req.preferredFormat)
    else:
        raise HTTPException(
            status_code=502,
            detail=f"Model did not return valid scenario schema: {scenario_quality_issues(result)}",
        )

    result["schemaVersion"] = "1.5"
    if result.get("_fallbackVariant"):
        result["variantAngleUz"] = result.pop("_fallbackVariant")
    elif angle:
        result["variantAngleUz"] = angle
    return {
        "schemaVersion": "1.5",
        "model": scenario_model_name(),
        "promptVersion": SCENARIO_PROMPT_VERSION,
        "result": result,
    }
