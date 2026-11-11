from __future__ import annotations

from typing import Any

_NULLISH = {
    "null",
    "none",
    "n/a",
    "na",
    "cta not provided",
    "not provided",
    "yo'q",
    "yo‘q",
    "mavjud emas",
}


def as_str_list(val: Any) -> list[str]:
    if val is None:
        return []
    if isinstance(val, str):
        t = val.strip()
        return [t] if t else []
    if isinstance(val, list):
        out: list[str] = []
        for item in val:
            if item is None:
                continue
            if isinstance(item, dict):
                text = item.get("topic") or item.get("whyItWorks") or item.get("text") or ""
                text = str(text).strip()
            else:
                text = str(item).strip()
            if text:
                out.append(text)
        return out
    return [str(val).strip()] if str(val).strip() else []


def normalize_tokens(text: str) -> list[str]:
    raw = "".join(
        ch.lower() if ch.isalnum() or ch.isspace() or ch in "ʻʼ'" else " " for ch in (text or "")
    )
    return [t for t in raw.split() if len(t) > 2]


def token_overlap_ratio(a: str, b: str) -> float:
    ta = set(normalize_tokens(a))
    tb = set(normalize_tokens(b))
    if not ta or not tb:
        return 0.0
    return len(ta & tb) / min(len(ta), len(tb))


def has_cross_field_repetition(fields: list[str]) -> bool:
    """True if the same 3+ word phrase appears in 2+ fields."""
    phrase_owners: dict[str, int] = {}
    for idx, field in enumerate(fields):
        tokens = normalize_tokens(field)
        if len(tokens) < 3:
            continue
        seen_in_field: set[str] = set()
        for i in range(len(tokens) - 2):
            phrase = " ".join(tokens[i : i + 3])
            if phrase in seen_in_field:
                continue
            seen_in_field.add(phrase)
            if phrase in phrase_owners and phrase_owners[phrase] != idx:
                return True
            phrase_owners[phrase] = idx
    for i in range(len(fields)):
        for j in range(i + 1, len(fields)):
            if fields[i] and fields[i] == fields[j]:
                return True
            if token_overlap_ratio(fields[i], fields[j]) > 0.75:
                return True
    return False


def looks_like_latin_topic_echo(text: str) -> bool:
    t = (text or "").strip()
    if not t:
        return True
    ascii_letters = sum(1 for ch in t if ("A" <= ch <= "Z") or ("a" <= ch <= "z") or ch.isspace())
    return ascii_letters / max(len(t), 1) > 0.85 and len(t.split()) <= 4


def is_valid_insights(result: dict[str, Any] | None) -> bool:
    if not isinstance(result, dict):
        return False
    if "mediaId" in result or "postIndex" in result:
        return False
    if "engagementScore" in result and "executiveSummaryUz" not in result:
        return False
    summary = result.get("executiveSummaryUz")
    if not isinstance(summary, str) or len(summary.strip()) < 20:
        return False
    wins = result.get("winningPatterns")
    return isinstance(wins, list) and len(wins) >= 1


def is_valid_scenario(result: dict[str, Any] | None) -> bool:
    if not isinstance(result, dict):
        return False
    title = result.get("titleUz")
    caption = result.get("captionPrimaryUz")
    hook = result.get("hookUz")
    cta = result.get("ctaUz")
    if not isinstance(title, str) or not title.strip():
        return False
    if not isinstance(caption, str) or len(caption.strip()) < 24:
        return False
    if not isinstance(hook, str) or not hook.strip():
        return False
    if len(hook.split()) > 12:
        return False
    if not isinstance(cta, str) or len(cta.strip()) < 3:
        return False
    shots = as_str_list(result.get("shotListUz"))
    if len(shots) < 2:
        return False
    alts = as_str_list(result.get("captionAlternativesUz"))
    fields = [
        hook.strip().lower(),
        caption.strip().lower(),
        cta.strip().lower(),
        *[alts[i].lower() for i in range(min(2, len(alts)))],
        *[s.lower() for s in shots[:3]],
    ]
    if has_cross_field_repetition(fields):
        return False
    if len(cta.split()) > 14:
        return False
    if token_overlap_ratio(cta, caption) > 0.55 or token_overlap_ratio(cta, hook) > 0.55:
        return False
    if token_overlap_ratio(hook, caption) > 0.5:
        return False
    return True


def normalize_nullish(result: dict[str, Any]) -> dict[str, Any]:
    for key in ("cta", "productOrService", "hook"):
        val = result.get(key)
        if isinstance(val, str) and val.strip().lower() in _NULLISH:
            result[key] = None
    return result


def normalize_insights(result: dict[str, Any]) -> dict[str, Any]:
    for key in (
        "winningPatterns",
        "losingPatterns",
        "hookInsights",
        "ctaInsights",
        "visualInsights",
        "audienceInsights",
        "metricInsights",
        "doNext",
        "avoidNext",
    ):
        result[key] = as_str_list(result.get(key))
    if not isinstance(result.get("topicInsights"), list):
        result["topicInsights"] = []
    if not result["doNext"] and result["winningPatterns"]:
        result["doNext"] = [f"Keyingi postda qo‘llang: {p}" for p in result["winningPatterns"][:3]]
    if not result["avoidNext"] and result["losingPatterns"]:
        result["avoidNext"] = [f"Takrorlamang: {p}" for p in result["losingPatterns"][:3]]
    if not result.get("confidence"):
        result["confidence"] = "medium"
    return result


def normalize_scenario(
    result: dict[str, Any],
    *,
    brief: str,
    preferred_format: str | None,
) -> dict[str, Any]:
    if preferred_format and not result.get("format"):
        result["format"] = preferred_format
    if brief:
        title = str(result.get("titleUz") or "")
        if looks_like_latin_topic_echo(title):
            result["titleUz"] = brief[:90]
        goal = str(result.get("goalUz") or "").strip()
        if not goal or looks_like_latin_topic_echo(goal):
            result["goalUz"] = brief[:200]
        if "reel" in brief.lower():
            result["format"] = "REELS"
    for key in ("captionAlternativesUz", "shotListUz", "hashtagsUz", "risksUz", "postingTipsUz"):
        result[key] = as_str_list(result.get(key))
    shots = result.get("shotListUz") or []
    fixed_shots: list[str] = []
    for i, shot in enumerate(shots[:5], start=1):
        s = shot.strip()
        if not s.lower().startswith(("1-", "2-", "3-", "4-", "5-", "kadr")):
            s = f"{i}-kadr: {s}"
        fixed_shots.append(s)
    result["shotListUz"] = fixed_shots
    cta = str(result.get("ctaUz") or "").strip()
    if not cta or token_overlap_ratio(cta, str(result.get("captionPrimaryUz") or "")) > 0.55:
        result["ctaUz"] = "Izohlarda o‘z fikringizni yozing"
    return result


def slim_analysis(analysis: dict[str, Any] | None) -> dict[str, Any] | None:
    if not analysis:
        return None
    keys = (
        "mainTopic",
        "contentCategory",
        "targetAudience",
        "hook",
        "cta",
        "summaryUz",
        "correctedSummary",
        "strengths",
        "weaknesses",
        "visualStyle",
        "userNote",
    )
    out: dict[str, Any] = {}
    for k in keys:
        if k in analysis and analysis[k] is not None:
            out[k] = analysis[k]
    return out or None
