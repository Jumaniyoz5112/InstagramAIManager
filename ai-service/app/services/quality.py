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


# Everyday Uzbek + allowed SMM loanwords (latin). Used to catch hallucinated mashups.
_UZ_LEXICON = {
    "baxt", "katta", "narsa", "narsalarda", "emas", "mayda", "samimiy", "lahza", "lahzalar",
    "lahzalarda", "yashiringan", "siz", "uchun", "bugun", "eng", "yoqimli", "qaysi", "boldi",
    "bo'ldi", "boldi", "izoh", "izohlarda", "yozing", "fikringizni", "javob", "savol",
    "qahva", "gul", "yorug", "yoruglik", "iliq", "sekin", "zoom", "close", "plan", "yaqin",
    "umumiy", "tabassum", "qahramon", "rang", "pastel", "pushti", "estetik", "ko'rinish",
    "korinish", "matn", "ekranda", "chiqadi", "asosiy", "fikr", "hook", "cta", "reel",
    "reels", "story", "instagram", "video", "kadr", "montaj", "musiqa", "romantik", "akustik",
    "lofi", "mehr", "go'zallik", "gozallik", "oddiy", "daqiqa", "daqiqalarda", "unutmang",
    "ulashish", "ozingizga", "o'zingizga", "haqiqiy", "iliqlik", "yonimizdagi", "doim",
    "mahsulot", "sahna", "obyekt", "kamera", "harakat", "fon", "nur", "kayfiyat", "auditoriy",
    "auditoriya", "post", "caption", "hashtag", "qisqa", "aniq", "yangi", "keyingi", "soniya",
    "sekund", "birinchi", "ikkinchi", "uchinchi", "oxirida", "boshida", "diqqat", "e'tibor",
    "etibor", "tortish", "chaqiruv", "like", "save", "share", "comment", "yuz", "kulgu",
    "tabiiy", "sodda", "tushunarli", "chiroyli", "yorqin", "qorongu", "yumshoq", "sevgili",
    "do'st", "dost", "oila", "ona", "ota", "bola", "uy", "oshxona", "stol", "deraza", "shahar",
    "tabiat", "daraxt", "osmon", "quyosh", "kechqurun", "ertalab", "tushlik", "kechki",
    "ovqat", "shirinlik", "kitob", "telefon", "kompyuter", "ish", "ofis", "maktab", "universitet",
    "sport", "yugurish", "mashq", "soglik", "sog'liq", "energiya", "dam", "olish", "sayohat",
    "joy", "joylar", "burchak", "xona", "devor", "gilam", "chiroq", "sham", "guldon",
    "sovga", "sovg'a", "paket", "quti", "ochish", "ko'rsatish", "kursatish", "tanishtirish",
    "foyda", "narx", "chegirma", "aksya", "aksiya", "sotib", "oling", "boshlash", "tugatish",
    "va", "yoki", "lekin", "chunki", "agar", "bilan", "uchun", "kabi", "ham", "juda", "ancha",
    "biroz", "yana", "endi", "hozir", "keyin", "avval", "keyinroq", "shu", "u", "bu", "o‘sha",
    "osha", "men", "biz", "ular", "uning", "bizning", "sizning", "qilish", "qiling", "qiladi",
    "bo'ladi", "boladi", "bor", "yo'q", "yoq", "kerak", "mumkin", "shart", "yaxshi", "yomon",
    "zor", "ajoyib", "a'lo", "alo", "sevgi", "muhabbat", "do'stlik", "dostlik", "baxtli",
    "xursand", "xafa", "tinich", "tinch", "osoyishta", "issiq", "sovuq", "yashil", "oq",
    "qora", "qizil", "ko'k", "kok", "sariq", "kulrang", "hashtaglar", "teg", "tag",
    "in", "out", "up", "story", "stories", "carousel", "image", "feed",
    "minnatdormiz", "tinchlik", "ulashing", "detal", "pan", "reaksiya", "piyola",
    "lahza", "lahzada", "qadrli", "joriy", "montaj", "ssenariya", "overlay",
    "setup", "detail", "emotion", "payoff", "transition", "note", "purpose",
}

_ALLOWED_TECH = {
    "zoom-in", "zoom-out", "close-up", "closeup", "reels", "story", "stories", "instagram",
    "cta", "hook", "lo-fi", "lofi", "mp4", "9:16", "1080x1920",
}

_BAD_PHRASE_FRAGMENTS = (
    "sevmoning",
    "o'quvchisi bilan qoncha",
    "oquvchisi bilan qoncha",
    "yuzlar bo'sh joylari",
    "yuzlar bosh joylari",
    "chuqurlaymiz",
    "kuchtirish",
    "oquvda",
    "o'quvda",
    "erkalar",
    "erkalarning",
    "kopiya iliq",
    "qoncha",
    "seki zoom",
    "kopiya",
)

# Common model typos / broken stems → clean Uzbek.
_TYPO_FIXES = (
    ("seki zoom", "sekin zoom"),
    ("seki ", "sekin "),
    ("oquvda", "yorug‘likda"),
    ("o'quvda", "yorug‘likda"),
    ("kopiya", "piyola"),
    ("erkalarning", "lahzalarning"),
    ("  ", " "),
)

_ALLOWED_PURPOSES = {
    "hook",
    "setup",
    "detail",
    "body",
    "emotion",
    "payoff",
    "cta",
    "transition",
}

MIN_SHOTS = 6
MAX_SHOTS = 8
DEFAULT_DURATION_SEC = 15


def _canon_apostrophe(text: str) -> str:
    return (
        (text or "")
        .replace("‘", "'")
        .replace("’", "'")
        .replace("ʻ", "'")
        .replace("ʼ", "'")
        .replace("`", "'")
        .replace("o'", "o'")
        .replace("g'", "g'")
    )


def _raw_tokens(text: str) -> list[str]:
    t = _canon_apostrophe(text).lower()
    raw = "".join(ch if ch.isalnum() or ch.isspace() or ch in "'-:" else " " for ch in t)
    return [tok for tok in raw.split() if tok]


def _in_lexicon(token: str) -> bool:
    t = _canon_apostrophe(token).lower().strip(".,!?:;\"'«»")
    if not t:
        return False
    if t in _ALLOWED_TECH or t in _UZ_LEXICON:
        return True
    # Strip common Uzbek endings and retry (daqiqalar → daqiqa, matni → matn).
    for suf in (
        "larning",
        "ning",
        "lari",
        "lar",
        "dan",
        "ga",
        "ni",
        "da",
        "ib",
        "ish",
        "ydi",
        "adi",
        "miz",
        "siz",
        "imi",
    ):
        if t.endswith(suf) and len(t) - len(suf) >= 3:
            stem = t[: -len(suf)]
            if stem in _UZ_LEXICON or stem + "i" in _UZ_LEXICON:
                return True
    return False


def looks_like_gibberish_uz(text: str) -> bool:
    """Heuristic: invented compounds / MT word-salad in supposed Uzbek copy."""
    t = _canon_apostrophe(text).strip().lower()
    if not t:
        return True
    for frag in _BAD_PHRASE_FRAGMENTS:
        if frag in t:
            return True
    tokens = _raw_tokens(t)
    if not tokens:
        return True
    # Overlay/copy should stay short and readable.
    if len(tokens) > 18:
        return True
    weird = 0
    for tok in tokens:
        base = tok.strip(".,!?:;\"'«»+/")
        if not base:
            continue
        if _in_lexicon(base):
            continue
        # long invented mash
        if len(base) >= 14:
            weird += 2
            continue
        # consonant pile unusual for Uzbek latin
        vowels = sum(1 for ch in base if ch in "aeiouo'")
        if len(base) >= 6 and vowels == 0:
            weird += 2
            continue
        # english-ish stem + uzbek-ish ending mash often hallucinated
        if any(base.endswith(suf) for suf in ("lash", "tirish", "moqda", "aymiz", "amiz")) and any(
            eng in base for eng in ("phone", "office", "face", "space", "reader", "force")
        ):
            weird += 3
            continue
        if base.isascii() and base.isalpha() and len(base) > 3:
            weird += 1
    return (weird / max(len(tokens), 1)) >= 0.4 or weird >= 4


def _as_int(val: Any, default: int) -> int:
    try:
        return int(val)
    except (TypeError, ValueError):
        return default


def polish_uzbek_text(text: str, *, max_words: int | None = None) -> str:
    """Fix common model typos and tighten wording."""
    t = _canon_apostrophe(text).strip()
    if not t:
        return ""
    low = t
    for bad, good in _TYPO_FIXES:
        # case-insensitive replace
        idx = low.lower().find(bad)
        while idx >= 0:
            low = low[:idx] + good + low[idx + len(bad) :]
            idx = low.lower().find(bad, idx + len(good))
    low = " ".join(low.split())
    if max_words is not None:
        words = low.split()
        if len(words) > max_words:
            low = " ".join(words[:max_words])
    return low.strip(" ,;:")


def default_montage_shots() -> list[dict[str, Any]]:
    """6-shot / 15s professional Reels skeleton with clean Uzbek copy."""
    return [
        {
            "startSec": 0,
            "endSec": 2,
            "visualUz": "Iliq yorug‘likdagi shaxsiy burchak, sekin zoom-in",
            "overlayTextUz": "Baxt katta narsalarda emas...",
            "purpose": "hook",
            "noteUz": "E’tiborni tortish",
        },
        {
            "startSec": 2,
            "endSec": 4,
            "visualUz": "Qahva yoki gul yaqin plan, yumshoq fon",
            "overlayTextUz": "...u mayda, samimiy lahzalarda",
            "purpose": "setup",
            "noteUz": "Mavzuga kirish",
        },
        {
            "startSec": 4,
            "endSec": 7,
            "visualUz": "Qo‘l harakati / detal close-up",
            "overlayTextUz": "Oddiy daqiqalar ham qadrli",
            "purpose": "detail",
            "noteUz": "Detal ko‘rsatish",
        },
        {
            "startSec": 7,
            "endSec": 10,
            "visualUz": "Tabassum yoki iliq pastel kadr",
            "overlayTextUz": "Shu lahzada tinchlik bor",
            "purpose": "emotion",
            "noteUz": "Hissiyot",
        },
        {
            "startSec": 10,
            "endSec": 13,
            "visualUz": "Estetik umumiy ko‘rinish, sekin pan",
            "overlayTextUz": "O‘zingizga mehr ulashing",
            "purpose": "payoff",
            "noteUz": "Asosiy fikr",
        },
        {
            "startSec": 13,
            "endSec": 15,
            "visualUz": "Yuz / reaksiya + ekranda CTA matni",
            "overlayTextUz": "Bugun eng yoqimli lahza qaysi?",
            "purpose": "cta",
            "noteUz": "Izohga chaqirish",
        },
    ]


def _redistribute_timeline(shots: list[dict[str, Any]], duration_sec: int) -> list[dict[str, Any]]:
    n = max(len(shots), 1)
    duration_sec = max(duration_sec, n * 2)
    step = duration_sec / n
    for i, shot in enumerate(shots):
        start = int(round(i * step))
        end = int(round((i + 1) * step)) if i < n - 1 else duration_sec
        if end <= start:
            end = start + 2
        shot["startSec"] = start
        shot["endSec"] = end
    shots[-1]["endSec"] = duration_sec
    return shots


def coerce_timed_shots(result: dict[str, Any], *, duration_sec: int = DEFAULT_DURATION_SEC) -> list[dict[str, Any]]:
    """Normalize to 6–8 timed shots across durationSec. Accepts objects or legacy strings."""
    defaults = default_montage_shots()
    raw = result.get("shots")
    out: list[dict[str, Any]] = []

    if isinstance(raw, list) and raw:
        for i, item in enumerate(raw[:MAX_SHOTS]):
            base = dict(defaults[min(i, len(defaults) - 1)])
            if isinstance(item, dict):
                visual = polish_uzbek_text(str(item.get("visualUz") or item.get("visual") or ""), max_words=16)
                overlay = polish_uzbek_text(
                    str(item.get("overlayTextUz") or item.get("overlay") or item.get("text") or ""),
                    max_words=9,
                )
                note = polish_uzbek_text(str(item.get("noteUz") or item.get("note") or ""), max_words=8)
                purpose = str(item.get("purpose") or base["purpose"]).strip().lower()
                if purpose not in _ALLOWED_PURPOSES:
                    purpose = base["purpose"]
                if visual:
                    base["visualUz"] = visual
                if overlay:
                    base["overlayTextUz"] = overlay
                if note:
                    base["noteUz"] = note
                base["purpose"] = purpose
                base["startSec"] = _as_int(item.get("startSec"), base["startSec"])
                base["endSec"] = _as_int(item.get("endSec"), base["endSec"])
            elif isinstance(item, str) and item.strip():
                base["visualUz"] = polish_uzbek_text(item, max_words=16)[:160]
            out.append(base)
    else:
        legacy = as_str_list(result.get("shotListUz"))
        for i, line in enumerate(legacy[:MAX_SHOTS]):
            base = dict(defaults[min(i, len(defaults) - 1)])
            base["visualUz"] = polish_uzbek_text(line, max_words=16)[:160]
            out.append(base)

    while len(out) < MIN_SHOTS:
        out.append(dict(defaults[len(out) % len(defaults)]))
    out = out[:MAX_SHOTS]

    # First/last purposes fixed; fill middle roles if missing variety.
    purpose_ladder = ["hook", "setup", "detail", "emotion", "payoff", "cta", "body", "transition"]
    for i, shot in enumerate(out):
        if i == 0:
            shot["purpose"] = "hook"
        elif i == len(out) - 1:
            shot["purpose"] = "cta"
        elif shot.get("purpose") in ("hook", "cta"):
            shot["purpose"] = purpose_ladder[min(i, len(purpose_ladder) - 1)]

    visuals = [str(s.get("visualUz") or "").strip().lower() for s in out]
    seen: set[str] = set()
    for i, shot in enumerate(out):
        visual = str(shot.get("visualUz") or "").strip()
        overlay = str(shot.get("overlayTextUz") or "").strip()
        note = str(shot.get("noteUz") or "").strip()
        key = visual.lower()
        if (
            looks_like_gibberish_uz(visual)
            or not visual
            or len(visual) < 8
            or (key in seen)
            or (len(set(v for v in visuals if len(v) >= 8)) <= 1 and i > 0)
        ):
            shot["visualUz"] = defaults[min(i, len(defaults) - 1)]["visualUz"]
        else:
            shot["visualUz"] = polish_uzbek_text(visual, max_words=16)
        seen.add(str(shot["visualUz"]).lower())

        if looks_like_gibberish_uz(overlay) or not overlay or len(overlay.split()) < 2:
            shot["overlayTextUz"] = defaults[min(i, len(defaults) - 1)]["overlayTextUz"]
        else:
            shot["overlayTextUz"] = polish_uzbek_text(overlay, max_words=9)

        if not note or looks_like_gibberish_uz(note):
            shot["noteUz"] = defaults[min(i, len(defaults) - 1)].get("noteUz", shot["purpose"])
        else:
            shot["noteUz"] = polish_uzbek_text(note, max_words=8)

    return _redistribute_timeline(out, duration_sec)


def scenario_quality_issues(result: dict[str, Any] | None) -> list[str]:
    """Human-readable reasons a scenario draft is weak (empty = OK)."""
    if not isinstance(result, dict):
        return ["not_object"]
    issues: list[str] = []
    title = result.get("titleUz")
    caption = result.get("captionPrimaryUz")
    hook = result.get("hookUz")
    cta = result.get("ctaUz")
    if not isinstance(title, str) or not title.strip():
        issues.append("missing_title")
    if not isinstance(caption, str) or len(caption.strip()) < 20:
        issues.append("weak_caption")
    elif looks_like_gibberish_uz(caption):
        issues.append("caption_gibberish")
    if not isinstance(hook, str) or not hook.strip():
        issues.append("missing_hook")
    elif len(hook.split()) > 12:
        issues.append("hook_too_long")
    elif looks_like_gibberish_uz(hook):
        issues.append("hook_gibberish")
    if not isinstance(cta, str) or len(cta.strip()) < 3:
        issues.append("missing_cta")
    elif looks_like_gibberish_uz(cta):
        issues.append("cta_gibberish")

    shots = result.get("shots")
    if not isinstance(shots, list) or len(shots) < MIN_SHOTS:
        if len(as_str_list(result.get("shotListUz"))) < MIN_SHOTS:
            issues.append("few_shots")
    else:
        overlays: list[str] = []
        visuals: list[str] = []
        for i, shot in enumerate(shots[:MAX_SHOTS]):
            if not isinstance(shot, dict):
                issues.append(f"shot_{i}_invalid")
                continue
            visual = str(shot.get("visualUz") or "").strip()
            overlay = str(shot.get("overlayTextUz") or "").strip()
            if not visual:
                issues.append(f"shot_{i}_no_visual")
            elif looks_like_gibberish_uz(visual):
                issues.append(f"shot_{i}_visual_gibberish")
            else:
                visuals.append(visual.lower())
            if not overlay:
                issues.append(f"shot_{i}_no_overlay")
            elif len(overlay.split()) > 12:
                issues.append(f"shot_{i}_overlay_long")
            elif looks_like_gibberish_uz(overlay):
                issues.append(f"shot_{i}_overlay_gibberish")
            else:
                overlays.append(overlay.lower())
        if len(overlays) >= 2 and has_cross_field_repetition(overlays):
            issues.append("overlay_repetition")
        if len(visuals) >= 3 and len(set(visuals)) == 1:
            issues.append("duplicate_visuals")

    if isinstance(hook, str) and isinstance(caption, str) and isinstance(cta, str):
        text_fields = [hook.strip().lower(), caption.strip().lower(), cta.strip().lower()]
        if has_cross_field_repetition(text_fields):
            issues.append("text_repetition")
        if len(cta.split()) > 16:
            issues.append("cta_too_long")
        if token_overlap_ratio(cta, caption) > 0.7 or token_overlap_ratio(cta, hook) > 0.7:
            issues.append("cta_overlaps_copy")
        if token_overlap_ratio(hook, caption) > 0.65:
            issues.append("hook_overlaps_caption")
    return issues


def is_valid_scenario(result: dict[str, Any] | None) -> bool:
    return not scenario_quality_issues(result)


def is_minimal_scenario(result: dict[str, Any] | None) -> bool:
    """Enough structure to salvage after normalize (avoid hard 502 on 7B quirks)."""
    if not isinstance(result, dict):
        return False
    title = result.get("titleUz")
    caption = result.get("captionPrimaryUz")
    shots = result.get("shots")
    has_shots = isinstance(shots, list) and len(shots) >= 4
    return (
        isinstance(title, str)
        and bool(title.strip())
        and isinstance(caption, str)
        and len(caption.strip()) >= 12
        and has_shots
    )


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
    result["schemaVersion"] = "1.4"
    result["aspectRatio"] = "9:16"
    duration = _as_int(result.get("durationSec"), DEFAULT_DURATION_SEC)
    if duration < 12:
        duration = DEFAULT_DURATION_SEC
    if duration > 20:
        duration = 18
    result["durationSec"] = duration

    fmt = str(result.get("format") or preferred_format or "REELS").upper()
    if fmt not in ("REELS", "STORY", "IMAGE", "CAROUSEL"):
        fmt = "REELS"
    if preferred_format:
        pf = preferred_format.upper()
        if pf in ("REELS", "STORY"):
            fmt = pf
    if brief and "stor" in brief.lower():
        fmt = "STORY"
    elif brief and "reel" in brief.lower():
        fmt = "REELS"
    result["format"] = fmt

    if brief:
        title = polish_uzbek_text(str(result.get("titleUz") or ""), max_words=12)
        if looks_like_latin_topic_echo(title) or looks_like_gibberish_uz(title):
            result["titleUz"] = brief[:90]
        else:
            result["titleUz"] = title or brief[:90]
        goal = polish_uzbek_text(str(result.get("goalUz") or ""), max_words=20)
        if not goal or looks_like_latin_topic_echo(goal) or looks_like_gibberish_uz(goal):
            result["goalUz"] = f"{brief[:160].rstrip('.')} — auditoriyani jalb qilish"
        else:
            result["goalUz"] = goal

    mood = polish_uzbek_text(str(result.get("musicMoodUz") or ""), max_words=8)
    if not mood or looks_like_gibberish_uz(mood):
        result["musicMoodUz"] = "Sekin akustik yoki iliq lo-fi"
    else:
        result["musicMoodUz"] = mood

    for key in ("hashtagsUz", "risksUz", "postingTipsUz", "captionAlternativesUz"):
        result[key] = as_str_list(result.get(key))

    shots = coerce_timed_shots(result, duration_sec=duration)
    result["shots"] = shots
    result["durationSec"] = int(shots[-1]["endSec"]) if shots else duration
    # Legacy string list for older UI/clients.
    result["shotListUz"] = [
        f"{s['startSec']}-{s['endSec']}s · {s['visualUz']} · «{s['overlayTextUz']}»" for s in shots
    ]

    hook = polish_uzbek_text(
        str(result.get("hookUz") or shots[0].get("overlayTextUz") or ""),
        max_words=9,
    )
    if not hook or looks_like_gibberish_uz(hook):
        hook = str(shots[0].get("overlayTextUz") or "Diqqat — keyingi lahza muhim")
    result["hookUz"] = hook

    caption = polish_uzbek_text(str(result.get("captionPrimaryUz") or ""), max_words=40)
    caption_is_cta_like = bool(caption) and len(caption.split()) <= 6 and any(
        w in caption.lower() for w in ("izoh", "yozing", "like", "comment", "saqlang")
    )
    if (
        not caption
        or looks_like_gibberish_uz(caption)
        or len(caption) < 24
        or caption_is_cta_like
    ):
        if brief and len(brief) > 8:
            result["captionPrimaryUz"] = (
                f"{brief.strip()[:100].rstrip('.')}. "
                "Haqiqiy iliqlik oddiy daqiqalarda yashaydi. "
                "O'zingizga mehr ulashishni unutmang."
            )
        else:
            result["captionPrimaryUz"] = (
                "Haqiqiy iliqlik va go'zallik yonimizdagi oddiy daqiqalarda bo'ladi. "
                "O'zingizga mehr ulashishni unutmang."
            )
    else:
        result["captionPrimaryUz"] = caption

    cta_raw = result.get("ctaUz")
    if isinstance(cta_raw, list):
        cta_raw = " ".join(str(x).strip() for x in cta_raw if x)
    cta = polish_uzbek_text(str(cta_raw or ""), max_words=12)
    caption_now = str(result.get("captionPrimaryUz") or "")
    hook_now = str(result.get("hookUz") or "")
    last_overlay = str(shots[-1].get("overlayTextUz") or "").strip()
    ascii_ratio = sum(1 for ch in cta if ("A" <= ch <= "Z") or ("a" <= ch <= "z") or ch.isspace()) / max(
        len(cta), 1
    )
    if (
        not cta
        or looks_like_gibberish_uz(cta)
        or len(cta.split()) > 14
        or ascii_ratio > 0.75
        or token_overlap_ratio(cta, caption_now) > 0.55
        or token_overlap_ratio(cta, hook_now) > 0.55
    ):
        result["ctaUz"] = (
            last_overlay
            if last_overlay and not looks_like_gibberish_uz(last_overlay)
            else "Izohlarda o'z fikringizni yozing"
        )
    else:
        result["ctaUz"] = cta

    why = polish_uzbek_text(str(result.get("whyThisWorksUz") or ""), max_words=28)
    if why and not looks_like_gibberish_uz(why):
        result["whyThisWorksUz"] = why
    tips = [polish_uzbek_text(t, max_words=16) for t in as_str_list(result.get("postingTipsUz"))]
    result["postingTipsUz"] = [t for t in tips if t and not looks_like_gibberish_uz(t)][:4]

    tags = as_str_list(result.get("hashtagsUz"))
    cleaned_tags: list[str] = []
    for tag in tags[:8]:
        t = tag if tag.startswith("#") else f"#{tag}"
        t = "".join(ch for ch in t if ch.isalnum() or ch == "#" or ch in "ʻʼ'")
        if len(t) > 2:
            cleaned_tags.append(t.lower())
    if not cleaned_tags:
        cleaned_tags = ["#estetika", "#muhabbat", "#baxt", "#kunliklahzalar"]
    result["hashtagsUz"] = cleaned_tags
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
