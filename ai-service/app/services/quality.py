from __future__ import annotations

import random
import time
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


# Everyday Uzbek + SMM / ta'lim / biznes loanwords (latin). Gibberish filtr uchun.
_UZ_LEXICON = {
    # --- asosiy / bog'lovchi ---
    "va", "yoki", "lekin", "ammo", "chunki", "agar", "bilan", "uchun", "kabi", "ham", "juda",
    "ancha", "biroz", "yana", "endi", "hozir", "keyin", "avval", "keyinroq", "shu", "u", "bu",
    "o'sha", "osha", "men", "biz", "ular", "uning", "bizning", "sizning", "mening", "sen",
    "siz", "kim", "nima", "qayerda", "qachon", "qanday", "qaysi", "necha", "nega", "chunki",
    "bor", "yo'q", "yoq", "kerak", "mumkin", "shart", "lozim", "zarur", "muhim", "foydali",
    "emas", "edi", "ekan", "ekanligi", "bo'lsa", "bolsa", "bo'lib", "bolib",
    # --- fe'llar / harakat ---
    "qilish", "qiling", "qiladi", "qilmoq", "qilib", "qilaylik", "qilamiz", "bo'ladi", "boladi",
    "bo'lish", "bolish", "olish", "oling", "berish", "bering", "ko'rish", "korish", "ko'ring",
    "eshitish", "aytish", "ayting", "yozish", "yozing", "o'qish", "oqish", "o'qing", "bilish",
    "bilib", "biling", "eslab", "eslang", "unutmang", "unutish", "boshlash", "tugatish",
    "ochish", "yopish", "kirish", "chiqish", "chiqadi", "kelish", "ketish", "turish", "o'tirish",
    "yugurish", "yurish", "yuring", "to'xtash", "toxtash", "kutish", "qidirish", "topish",
    "tanlash", "tanlang", "ulashish", "ulashing", "saqlash", "saqlang", "yuborish", "yuboring",
    "tushuntirish", "tushuntiramiz", "tushuning", "o'rganish", "organish", "o'rgating",
    "ko'rsatish", "kursatish", "ko'rsating", "tanishtirish", "taklif", "taklifqiling",
    "sotib", "sotish", "buyurtma", "to'lash", "tolash", "tekshirish", "tekshiring",
    "o'ylash", "oylash", "hisoblash", "solishtirish", "tayyorlash", "tayyor", "ishga",
    # --- sifat / holat ---
    "yaxshi", "yomon", "zor", "ajoyib", "a'lo", "alo", "zo'r", "mukammal", "toza", "sodda",
    "tushunarli", "aniq", "qisqa", "uzun", "katta", "kichik", "mayda", "yuqori", "past",
    "tez", "sekin", "sekinroq", "tezroq", "kuchli", "zaif", "yumshoq", "qattiq", "yorqin",
    "qorong'u", "qorongu", "iliq", "issiq", "sovuq", "salqin", "tabiiy", "samimiy", "haqiqiy",
    "yangi", "eski", "keyingi", "oldingi", "joriy", "maxsus", "oddiy", "murakkab", "qulay",
    "qiyin", "oson", "xavfsiz", "xavfli", "foydali", "zararli", "chiroyli", "estetik",
    "romantika", "romantik", "baxtli", "xursand", "xafa", "tinch", "tinich", "osoyishta",
    "erkin", "band", "bo'sh", "bosh", "to'liq", "toliq", "yarim", "butun", "yagona",
    "birinchi", "ikkinchi", "uchinchi", "to'rtinchi", "beshinchi", "oxirgi", "oxirida",
    "boshida", "o'rtada", "ortada", "yuqorida", "pastda", "yonida", "ichida", "tashqarida",
    # --- odam / munosabat ---
    "odam", "inson", "bola", "qiz", "o'g'il", "ogil", "ona", "ota", "aka", "opa", "uka",
    "singil", "do'st", "dost", "do'stlik", "dostlik", "sevgili", "oila", "oilaviy",
    "jamoa", "guruh", "auditoriy", "auditoriya", "mijoz", "foydalanuvchi", "obuna", "obunachi",
    "hamkor", "ustoz", "o'qituvchi", "oqituvchi", "talaba", "o'quvchi", "oquvchi", "mutaxassis",
    "haydovchi", "piyoda", "mehmon", "xo'jayin", "xodim", "rahbar", "asoschi",
    # --- his-tuyg'u / qiymat ---
    "baxt", "baxtli", "sevgi", "muhabbat", "mehr", "minnatdormiz", "rahmat", "iltimos",
    "uzr", "kechirasiz", "ishonch", "umid", "orzu", "maqsad", "natija", "muvaffaqiyat",
    "muammo", "yechim", "sabab", "oqibat", "tajriba", "maslahat", "qoida", "qoidalari",
    "qadrli", "qadr", "hurmat", "e'tibor", "etibor", "diqqat", "e'tiborni", "tortish",
    "chaqiruv", "taklifnoma", "ilhom", "motivatsiya", "energiya", "kayfiyat", "hissiyot",
    "tinchlik", "xotirjamlik", "hayot", "lahza", "lahzalar", "lahzada", "lahzalarda",
    "daqiqa", "daqiqalar", "daqiqalarda", "soniya", "sekund", "soat", "kun", "hafta",
    "oy", "yil", "ertalab", "tushlik", "kechki", "kechqurun", "tun", "bugun", "kecha",
    "ertaga", "doim", "hech", "har", "ba'zan", "bazan", "ko'pincha", "kopincha",
    # --- joy / narsa ---
    "joy", "joylar", "joylash", "burchak", "xona", "uy", "kvartira", "ofis", "maktab",
    "universitet", "dokon", "do'kon", "bozor", "restoran", "kafe", "oshxona", "zal",
    "zalda", "sahna", "fon", "devor", "gilam", "stol", "stul", "deraza", "eshik",
    "shahar", "qishloq", "ko'cha", "kocha", "park", "tabiat", "daraxt", "gul", "guldon",
    "osmon", "quyosh", "bulut", "yomg'ir", "yomgir", "qor", "sham", "chiroq", "yorug",
    "yorug'lik", "yoruglik", "nur", "soya", "rang", "pastel", "pushti", "yashil", "oq",
    "qora", "qizil", "ko'k", "kok", "sariq", "kulrang", "jigarrang", "binafsha",
    "narsa", "narsalar", "narsalarda", "obyekt", "mahsulot", "tovar", "brend", "logo",
    "quti", "paket", "sovga", "sovg'a", "kitob", "daftar", "qalam", "telefon", "kompyuter",
    "noutbuk", "planshet", "ekran", "ekranda", "matn", "rasm", "foto", "surat", "video",
    "audio", "musiqa", "qoshiq", "ashula", "ovoz", "ovozli", "mikrofon", "kamera",
    # --- ovqat / kundalik ---
    "ovqat", "non", "gosht", "go'sht", "sabzavot", "meva", "sut", "choy", "qahva",
    "piyola", "stakan", "suv", "shirinlik", "tort", "salat", "sho'rva", "shorva",
    "kechki", "nonushta", "tushlik", "pishiriq", "taom", "mazali", "yangilik",
    # --- sport / sog'liq ---
    "sport", "mashq", "yugurish", "yurish", "fitnes", "zal", "sog'liq", "soglik",
    "sog'lom", "soglom", "dori", "shifokor", "klinika", "dam", "damolish", "uyqu",
    "charchoq", "kuch", "vazn", "bo'y", "boy", "nafas", "yurak", "tanasi", "tana",
    # --- biznes / savdo ---
    "biznes", "ish", "loyiha", "reja", "strategiya", "marketing", "sotuv", "sotish",
    "narx", "chegirma", "aksiya", "aksya", "foiz", "pul", "byudjet", "daromad", "foyda",
    "zarar", "mijozlar", "xizmat", "servis", "sifat", "kafolat", "yetkazib", "yetkazish",
    "buyurtma", "to'lov", "tolov", "karta", "naqd", "online", "onlayn", "offline",
    "startap", "investitsiya", "reklama", "kontent", "kontentlar", "post", "postlar",
    "stories", "story", "reels", "reel", "carousel", "karusel", "feed", "image", "caption",
    "hashtag", "hashtaglar", "teg", "tag", "blog", "bloger", "influencer", "trend",
    "viral", "algoritm", "reach", "engagement", "view", "views", "like", "likes",
    "comment", "comments", "share", "save", "follow", "unfollow", "dm", "inbox",
    # --- ta'lim / izoh ---
    "dars", "mavzu", "mavzuda", "saboq", "bilim", "malaka", "ko'nikma", "konikma",
    "misol", "misollar", "qadam", "qadamlar", "bosqich", "bosqichlar", "ro'yxat", "royxat",
    "jadval", "grafik", "diagramma", "statistika", "raqam", "foizda", "o'rtacha", "ortacha",
    "eng", "kam", "ko'p", "kop", "yetarli", "kamida", "ko'pi", "kopi", "bilan",
    "savol", "javob", "izoh", "izohlarda", "fikr", "fikringizni", "fikringiz", "takliflar",
    "xulosa", "kirish", "asosiy", "qo'shimcha", "qoshimcha", "muhimligi", "ahamiyat",
    "sababi", "natijasi", "yutuq", "xato", "to'g'ri", "togri", "noto'g'ri", "notogri",
    # --- SMM / montaj ---
    "instagram", "facebook", "telegram", "youtube", "tiktok", "hook", "cta", "kadr",
    "kadrlar", "montaj", "ssenariya", "senariya", "scenario", "overlay", "setup", "detail",
    "emotion", "payoff", "transition", "note", "purpose", "visual", "vizual", "uslub",
    "stil", "kompozitsiya", "plan", "yaqin", "umumiy", "close", "closeup", "zoom",
    "pan", "tilt", "reaksiya", "tabassum", "kulgu", "yuz", "qo'l", "qol", "qahramon",
    "sahna", "svet", "yoritish", "akustik", "lofi", "beat", "ritm",
    "temp", "davomiylik", "format", "vertikal", "gorizontal", "square", "thumbnail",
    "cover", "intro", "outro", "cut", "transition", "effect", "filter", "filtr",
    "in", "out", "up", "down", "left", "right", "center", "markaz",
    # --- yo'l / xavfsizlik ---
    "yo'l", "yol", "yo'llar", "yollar", "harakat", "harakatlanish", "svetofor", "belgi",
    "belgiga", "belgilar", "mashina", "avtomobil", "mototsikl", "velosiped", "avtobus",
    "taksi", "piyoda", "otish", "o'tish", "chiziq", "chizig'i", "chizigini", "chizigi",
    "to'xtash", "toxtash", "tezlik", "sekinlash", "gaz", "tormoz", "rul", "faro",
    "xavf", "xavfsizlik", "manbai", "modda", "moddani", "moddalar", "qonun", "jarima",
    "qoidabuzarlik", "avariya", "baxtsiz", "hodisa", "sug'urta", "sugurta", "hujjat",
    "guvohnoma", "passport", "haydovchilik", "litsenziya", "yo'nalish", "yonalish",
    "chap", "o'ng", "ong", "oldinga", "orqaga", "aylanma", "chorraha", "ko'prik",
    "koprik", "tonnel", "parking", "avtoturargoh", "benzin", "zapravka",
    # --- go'zallik / lifestyle (lekin shablon emas — so'zlar) ---
    "go'zallik", "gozallik", "iliqlik", "yashiringan", "yonimizdagi", "o'zingizga",
    "ozingizga", "o'zingiz", "ozingiz", "bizga", "sizga", "ularga", "mayda", "katta",
    "yoqimli", "yoqtirish", "yoqadi", "yoqdi", "boldi", "bo'ldi", "boldi", "bo'ldi",
    "chiqadi", "ko'rinish", "korinish", "ko'rinadi", "korinadi", "tuyuladi", "his",
    "hisoblang", "hisoblaymiz", "esda", "qoling", "qoldiring", "saqlang", "ulashing",
    "yozing", "bosing", "o'ting", "oting", "qarang", "tinglang", "sinab", "ko'ring",
    # --- qo'shimcha fe'l shakllari ---
    "aytamiz", "deymiz", "deyman", "dedim", "aytdi", "bo'ldi", "boldi", "ketdi", "keldi",
    "qoldi", "turibdi", "yotibdi", "ishlayapti", "o'qiyapti", "yozmoqda", "kutyapti",
    "boshladik", "tugatdik", "topdik", "bildik", "tushundik", "o'rgandik", "ko'rdik",
    "eshitdik", "qildik", "berdik", "oldik", "yubordik", "saqladik", "ulashdik",
    # --- sonlar / miqdor (so'z) ---
    "bir", "ikki", "uch", "to'rt", "tort", "besh", "olti", "yetti", "sakkiz", "to'qqiz",
    "toqqiz", "o'n", "on", "yigirma", "o'ttiz", "ottiz", "qirq", "ellik", "oltmish",
    "yetmish", "sakson", "to'qson", "toqson", "yuz", "ming", "million", "yarim",
    "foiz", "gramm", "kilogram", "litr", "metr", "kilometr", "sm", "mm",
    # --- boshqa foydali ---
    "nima", "negaki", "shuning", "shunday", "shunda", "unda", "demak", "ya'ni", "yani",
    "masalan", "jumladan", "xususan", "asosan", "umuman", "hatto", "balki",
    "albatta", "tabiiyki", "aniqki", "ehtimol", "imkon", "imkoniyat", "sharoit",
    "muhit", "vaziyat", "holat", "jarayon", "tizim", "usul", "uslub", "yo'l-yo'riq",
    "yol-yoriq", "qo'llanma", "qollanma", "instruksiya", "qadam-baqadam", "bosqichma",
    "variant", "tanlov", "imkoniyatlar", "afzallik", "kamchilik", "farq", "o'xshash",
    "oxshash", "boshqa", "birga", "yolg'iz", "yolgiz", "birgalikda",
    "qoidasi", "moddasi", "tezligi", "xavfsizligi", "belgisi", "chizig'i", "chizigi",
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
        "si",
        "gi",
        "ni",
    ):
        if t.endswith(suf) and len(t) - len(suf) >= 3:
            stem = t[: -len(suf)]
            if stem in _UZ_LEXICON or (stem + "i") in _UZ_LEXICON or (stem + "a") in _UZ_LEXICON:
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


_BRIEF_NOISE = {
    "boshqa",
    "mavzuda",
    "mavzu",
    "ish",
    "qilamiz",
    "qilaylik",
    "qilib",
    "qil",
    "ber",
    "bering",
    "tuzib",
    "tuz",
    "ssenariya",
    "senariya",
    "scenario",
    "asosiy",
    "asoan",
    "asosan",
    "buyicha",
    "boyicha",
    "bo'yicha",
    "boyicha",
    "degandim",
    "deganman",
    "iltimos",
    "yozib",
    "yozing",
    "yoz",
    "kerak",
    "biz",
    "bilan",
    "uchun",
    "tushuntiramiz",
    "tushuntirish",
}

_LIFESTYLE_MARKERS = (
    "baxt katta",
    "samimiy lahz",
    "mehr ulash",
    "yoqimli lahza",
    "iliq yorug",
    "qahva yoki gul",
    "qahva",
    "pastel",
    "romantik",
    "estetika",
    "kunliklahza",
    "oddiy daqiqalar",
    "shu lahzada",
    "tinchlik bor",
    "shaxsiy burchak",
)

_TRAFFIC_KEYS = (
    "yo'l",
    "yol",
    "harakat",
    "svetofor",
    "piyoda",
    "haydovchi",
    "belgi",
    "mashina",
    "modda",
    "qoida",
)

# Multiple fallback variants — force/salvage never repeats the exact same copy.
_TRAFFIC_VARIANTS: list[dict[str, Any]] = [
    {
        "title": "Yo'l harakati qoidalari",
        "goal": "Asosiy yo'l qoidalari va moddalarni sodda tushuntirish",
        "music": "Aniqlik beruvchi ritm, oddiy fon",
        "tags": ["#yolqoidalari", "#xavfsizlik", "#haydovchi", "#reels"],
        "caption": (
            "Yo'l qoidasi — hayot qoidasi. Asosiy moddalarni bilib yuring, "
            "o'zingiz va boshqalar xavfsizligi uchun. "
            "Qaysi qoida sizga eng muhim? Izohlarda yozing."
        ),
        "angle": "svetofor + to'xtash chizig'i",
        "shots": [
            {"visualUz": "Svetofor yaqin plan, sekin zoom-in", "overlayTextUz": "Yo'l qoidasi — hayot qoidasi", "purpose": "hook", "noteUz": "E'tibor"},
            {"visualUz": "To'xtash chizig'i va piyoda o'tish joyi", "overlayTextUz": "To'xtash chizig'ini biling", "purpose": "setup", "noteUz": "Kirish"},
            {"visualUz": "Yo'l belgisi close-up, qo'l ko'rsatadi", "overlayTextUz": "Belgiga qarab harakat qiling", "purpose": "detail", "noteUz": "Detal"},
            {"visualUz": "Haydovchi diqqat bilan yo'lga qaraydi", "overlayTextUz": "Tezlik — xavf manbai", "purpose": "emotion", "noteUz": "Ogohlantirish"},
            {"visualUz": "Asosiy qoida matni ekranda", "overlayTextUz": "Asosiy moddani eslab qoling", "purpose": "payoff", "noteUz": "Xulosa"},
            {"visualUz": "Kamera auditoriyaga + CTA", "overlayTextUz": "Qaysi qoida eng muhim?", "purpose": "cta", "noteUz": "Izoh"},
        ],
    },
    {
        "title": "3 soniyada 1 ta yo'l qoidasi",
        "goal": "Bitta aniq qoidani tez va esda qolarli o'rgatish",
        "music": "Qisqa urishli ritm, urg'uli",
        "tags": ["#yolqoidalari", "#3soniya", "#xavfsizlik", "#reels"],
        "caption": (
            "Bugun bitta qoida: piyodaga yo'l bering. "
            "Oddiy, lekin hayot saqlaydi. Siz qaysi qoidani ko'proq buzasiz? Yozing."
        ),
        "angle": "piyoda ustuvorligi",
        "shots": [
            {"visualUz": "Piyoda o'tish joyi, sekin pan", "overlayTextUz": "Piyoda — birinchi o'rinda", "purpose": "hook", "noteUz": "Hook"},
            {"visualUz": "Mashina to'xtayotgan kadr", "overlayTextUz": "Chiziq oldida to'xtang", "purpose": "setup", "noteUz": "Kirish"},
            {"visualUz": "Piyoda yo'ldan o'tayotgani", "overlayTextUz": "Ularga yo'l bering", "purpose": "detail", "noteUz": "Detal"},
            {"visualUz": "Haydovchi bosh irg'aydi", "overlayTextUz": "Hurmat — xavfsizlik", "purpose": "emotion", "noteUz": "Hissiyot"},
            {"visualUz": "Qoida matni ekranda", "overlayTextUz": "Bu oddiy, lekin muhim", "purpose": "payoff", "noteUz": "Xulosa"},
            {"visualUz": "CTA matn + yuz", "overlayTextUz": "Siz buni doim qilasizmi?", "purpose": "cta", "noteUz": "Savol"},
        ],
    },
    {
        "title": "Tezlik limitti — nima uchun?",
        "goal": "Tezlik qoidasini sabab bilan tushuntirish",
        "music": "Keskin, ogohlantiruvchi ritm",
        "tags": ["#tezlik", "#yolqoidalari", "#xavfsizlik", "#reels"],
        "caption": (
            "Tezlik limi — jarima uchun emas, hayot uchun. "
            "Shahar ichida sekinroq boring. Izohlarda: shahringizda limit necha?"
        ),
        "angle": "tezlik limi sababi",
        "shots": [
            {"visualUz": "Spidometr yaqin plan", "overlayTextUz": "Tezlik limi bormi?", "purpose": "hook", "noteUz": "Hook"},
            {"visualUz": "Shahar ko'chasi, mashinalar", "overlayTextUz": "Shahar ichida sekinroq", "purpose": "setup", "noteUz": "Kirish"},
            {"visualUz": "Belgi: tezlik limiti close-up", "overlayTextUz": "Belgida yozilgan raqam", "purpose": "detail", "noteUz": "Detal"},
            {"visualUz": "Bolalar o'tish joyi uzoqdan", "overlayTextUz": "Bir soniya kechikish — xavf", "purpose": "emotion", "noteUz": "Ogohlantirish"},
            {"visualUz": "Haydovchi gazni kamaytiradi", "overlayTextUz": "Limit — himoya", "purpose": "payoff", "noteUz": "Xulosa"},
            {"visualUz": "CTA ekranda", "overlayTextUz": "Shahringizda limit necha?", "purpose": "cta", "noteUz": "Izoh"},
        ],
    },
    {
        "title": "Svetofor ranglari — eslatma",
        "goal": "Svetofor signallarini qisqa eslatish",
        "music": "Yengil elektron ritm",
        "tags": ["#svetofor", "#yolqoidalari", "#eslatma", "#reels"],
        "caption": (
            "Qizil — to'xta. Sariq — tayyorlan. Yashil — ehtiyot bilan. "
            "Oddiy eslatma, lekin har kuni kerak. Saqlab qo'ying."
        ),
        "angle": "svetofor 3 rang",
        "shots": [
            {"visualUz": "Qizil chiroq yonadi, zoom-in", "overlayTextUz": "Qizil — to'xtang", "purpose": "hook", "noteUz": "Hook"},
            {"visualUz": "Sariq chiroq", "overlayTextUz": "Sariq — tayyorlaning", "purpose": "setup", "noteUz": "Kirish"},
            {"visualUz": "Yashil chiroq", "overlayTextUz": "Yashil — ehtiyot bilan", "purpose": "detail", "noteUz": "Detal"},
            {"visualUz": "Mashina sekin harakatlanadi", "overlayTextUz": "Shoshilmang", "purpose": "emotion", "noteUz": "Eslatma"},
            {"visualUz": "Uch rang birga grafik", "overlayTextUz": "3 rang — 3 qoida", "purpose": "payoff", "noteUz": "Xulosa"},
            {"visualUz": "CTA", "overlayTextUz": "Do'stingizga yuboring", "purpose": "cta", "noteUz": "Share"},
        ],
    },
    {
        "title": "Chap/o'ng burilish qoidalari",
        "goal": "Burilishda asosiy xatolarni ko'rsatish",
        "music": "Oddiy puk-puk ritm",
        "tags": ["#burilish", "#yolqoidalari", "#haydovchi", "#reels"],
        "caption": (
            "Burilishdan oldin signal, chiziq va piyodani tekshiring. "
            "Qaysi xatoni ko'p ko'rasiz? Izohlarda yozing."
        ),
        "angle": "burilish + signal",
        "shots": [
            {"visualUz": "Signal chirog'i yonadi", "overlayTextUz": "Avval signal bering", "purpose": "hook", "noteUz": "Hook"},
            {"visualUz": "Chorrahaga yaqinlashish", "overlayTextUz": "Chorraha oldida sekinlang", "purpose": "setup", "noteUz": "Kirish"},
            {"visualUz": "Oyna orqali qarash (mirror)", "overlayTextUz": "Orqa va yonni tekshiring", "purpose": "detail", "noteUz": "Detal"},
            {"visualUz": "Piyoda yo'lda", "overlayTextUz": "Piyodani o'tkazing", "purpose": "emotion", "noteUz": "Xavfsizlik"},
            {"visualUz": "Toza burilish kadr", "overlayTextUz": "Keyin buriling", "purpose": "payoff", "noteUz": "Xulosa"},
            {"visualUz": "CTA", "overlayTextUz": "Qaysi xato ko'p uchraydi?", "purpose": "cta", "noteUz": "Izoh"},
        ],
    },
]

_TOPIC_PACKS: list[tuple[tuple[str, ...], list[dict[str, Any]]]] = [
    (_TRAFFIC_KEYS, _TRAFFIC_VARIANTS),
]


def extract_brief_topic(brief: str) -> str:
    """Strip instruction fluff; keep topic words for title/overlays."""
    raw = _canon_apostrophe(brief or "").strip()
    if not raw:
        return ""
    words = [w for w in raw.replace(",", " ").replace(".", " ").split() if w]
    kept: list[str] = []
    for w in words:
        base = w.lower().strip(".,!?:;\"'«»")
        if base in _BRIEF_NOISE or len(base) < 3:
            continue
        # keep "qoida(lar)" when part of traffic topic context
        kept.append(w)
    if not kept:
        # fallback: first 8 words of brief
        return " ".join(words[:8])
    return " ".join(kept[:10])


def resolve_topic_variants(brief: str) -> list[dict[str, Any]] | None:
    b = _canon_apostrophe(brief or "").lower()
    if not b:
        return None
    for keys, variants in _TOPIC_PACKS:
        if any(k in b for k in keys):
            return variants
    return None


def pick_topic_variant(brief: str, *, salt: str | None = None) -> dict[str, Any] | None:
    """Pick a rotating/random fallback variant so salvage is not identical every run."""
    variants = resolve_topic_variants(brief)
    if not variants:
        return None
    seed_src = f"{salt or ''}|{brief}|{time.time_ns()}"
    rng = random.Random(seed_src)
    return dict(rng.choice(variants))


def resolve_topic_pack(brief: str) -> dict[str, Any] | None:
    """Backward-compatible: one variant (random). Prefer pick_topic_variant with salt."""
    return pick_topic_variant(brief)


def _shots_from_variant(variant: dict[str, Any]) -> list[dict[str, Any]]:
    shots: list[dict[str, Any]] = []
    for s in variant.get("shots") or []:
        shots.append(
            {
                "startSec": 0,
                "endSec": 2,
                "visualUz": s["visualUz"],
                "overlayTextUz": s["overlayTextUz"],
                "purpose": s["purpose"],
                "noteUz": s.get("noteUz", s["purpose"]),
            }
        )
    return shots


def _generic_topic_shots(brief: str, *, salt: str | None = None) -> list[dict[str, Any]]:
    topic = extract_brief_topic(brief) or "Yangi mavzu"
    short = " ".join(topic.split()[:5])
    rng = random.Random(f"{salt or ''}|generic|{brief}|{time.time_ns()}")
    hooks = [
        f"{short} — diqqat!",
        f"Bugun: {short}",
        f"{short} haqida bilasizmi?",
        f"3 soniyada {short}",
    ]
    setups = ["Mavzuga qisqa kirish", "Avval asosini biling", "Oddiy qilib tushuntiramiz"]
    details = ["Asosiy fikrni oching", "Muhim detal shu yerda", "Eslab qoling"]
    emotions = ["Nima uchun muhim?", "Bu sizga ta'sir qiladi", "Xato qimmatga tushadi"]
    payoffs = ["Esda qoladigan xulosa", "Asosiy xulosa shu", "Shu bilan eslab qoling"]
    ctas = [
        "Izohlarda fikringizni yozing",
        "Qaysi qism muhimroq?",
        "Do'stingizga yuboring",
        "Saqlab qo'ying",
    ]
    return [
        {"startSec": 0, "endSec": 2, "visualUz": f"{short} — ochilish, sekin zoom-in", "overlayTextUz": rng.choice(hooks), "purpose": "hook", "noteUz": "E'tibor"},
        {"startSec": 2, "endSec": 4, "visualUz": f"{short} yaqin plan", "overlayTextUz": rng.choice(setups), "purpose": "setup", "noteUz": "Kirish"},
        {"startSec": 4, "endSec": 7, "visualUz": "Muhim detal close-up", "overlayTextUz": rng.choice(details), "purpose": "detail", "noteUz": "Detal"},
        {"startSec": 7, "endSec": 10, "visualUz": "Inson / reaksiya", "overlayTextUz": rng.choice(emotions), "purpose": "emotion", "noteUz": "Ahamiyat"},
        {"startSec": 10, "endSec": 13, "visualUz": "Xulosa kadr, sekin pan", "overlayTextUz": rng.choice(payoffs), "purpose": "payoff", "noteUz": "Xulosa"},
        {"startSec": 13, "endSec": 15, "visualUz": "Kamera auditoriyaga + CTA", "overlayTextUz": rng.choice(ctas), "purpose": "cta", "noteUz": "Chaqiruv"},
    ]


def default_montage_shots(brief: str = "", *, salt: str | None = None) -> list[dict[str, Any]]:
    """6-shot skeleton — topic-aware fallback with rotating variants."""
    variant = pick_topic_variant(brief, salt=salt)
    if variant:
        return _shots_from_variant(variant)
    return _generic_topic_shots(brief, salt=salt)


def text_matches_brief_topic(text: str, brief: str) -> bool:
    """True if text already speaks the brief topic (keep model copy instead of pack)."""
    t = _canon_apostrophe(text or "").lower()
    if not t.strip():
        return False
    if any(m in t for m in _LIFESTYLE_MARKERS):
        return False
    if resolve_topic_variants(brief):
        return any(k in t for k in ("yo'l", "yol", "qoida", "svetofor", "piyoda", "haydovchi", "belgi", "modda", "tezlik", "buril", "signal", "chorraha"))
    topic = extract_brief_topic(brief)
    tokens = [x for x in _raw_tokens(topic) if x not in _BRIEF_NOISE and len(x) > 3][:6]
    return bool(tokens) and sum(1 for x in tokens if x in t) >= 1


def scenario_text_blob(result: dict[str, Any]) -> str:
    parts: list[str] = []
    for key in ("titleUz", "goalUz", "hookUz", "captionPrimaryUz", "ctaUz"):
        val = result.get(key)
        if isinstance(val, str):
            parts.append(val)
        elif isinstance(val, list):
            parts.extend(str(x) for x in val)
    shots = result.get("shots")
    if isinstance(shots, list):
        for s in shots:
            if isinstance(s, dict):
                parts.append(str(s.get("visualUz") or ""))
                parts.append(str(s.get("overlayTextUz") or ""))
    return _canon_apostrophe(" ".join(parts)).lower()


def _shots_blob(result: dict[str, Any]) -> str:
    parts: list[str] = []
    shots = result.get("shots")
    if isinstance(shots, list):
        for s in shots:
            if isinstance(s, dict):
                parts.append(str(s.get("visualUz") or ""))
                parts.append(str(s.get("overlayTextUz") or ""))
    for key in ("hookUz", "captionPrimaryUz"):
        val = result.get(key)
        if isinstance(val, str):
            parts.append(val)
    return _canon_apostrophe(" ".join(parts)).lower()


def is_off_topic_for_brief(result: dict[str, Any], brief: str) -> bool:
    """True when brief asks a concrete topic but output stuck on lifestyle/romance template."""
    brief_c = _canon_apostrophe(brief or "").strip()
    if len(brief_c) < 8:
        return False
    brief_l = brief_c.lower()
    if any(m in brief_l for m in ("muhabbat", "sevgi", "romant", "baxt", "estetika", "qahva")):
        return False
    # Ignore title/goal — model often pastes the brief there while shots stay romantic.
    blob = _shots_blob(result)
    lifestyle_hits = sum(1 for m in _LIFESTYLE_MARKERS if m in blob)
    topic = extract_brief_topic(brief_c)
    topic_tokens = [
        t
        for t in _raw_tokens(topic)
        if t not in _BRIEF_NOISE and t not in {"qilib", "berish", "tushuntiramiz", "tushuntirish", "haraka"}
    ]
    topic_hits = sum(1 for t in topic_tokens if t in blob)
    pack = resolve_topic_pack(brief_c)
    if pack:
        pack_keys = ("yo'l", "yol", "qoida", "svetofor", "piyoda", "haydovchi", "belgi", "modda", "tezlik")
        pack_hits = sum(1 for k in pack_keys if k in blob)
        if lifestyle_hits >= 2:
            return True
        if pack_hits == 0:
            return True
    if topic_tokens and topic_hits == 0 and lifestyle_hits >= 1:
        return True
    if lifestyle_hits >= 2 and topic_hits <= 1:
        return True
    return False


def force_topic_scenario(
    result: dict[str, Any],
    brief: str,
    *,
    salt: str | None = None,
) -> dict[str, Any]:
    """Rebuild from a rotating topic variant (fallback only — not every request)."""
    salt = salt or f"force-{time.time_ns()}"
    variant = pick_topic_variant(brief, salt=salt)
    topic = extract_brief_topic(brief) or "Yangi mavzu"
    shots = default_montage_shots(brief, salt=salt)
    result["shots"] = shots
    result["_fallbackVariant"] = (variant or {}).get("angle") or "generic"
    if variant:
        result["titleUz"] = variant["title"]
        result["goalUz"] = variant["goal"]
        result["musicMoodUz"] = variant["music"]
        result["captionPrimaryUz"] = variant["caption"]
        result["hashtagsUz"] = list(variant["tags"])
    else:
        short = " ".join(topic.split()[:6])
        result["titleUz"] = short
        result["goalUz"] = f"{short} — auditoriyaga tushunarli tushuntirish"
        result["musicMoodUz"] = "Aniqlik beruvchi, oddiy ritm"
        result["captionPrimaryUz"] = (
            f"{short}. Asosiy fikrlarni qisqa va aniq tushuntiramiz. "
            "Foydali bilib qoling va izohlarda savolingizni yozing."
        )
        slug = "".join(ch for ch in short.lower() if ch.isalnum())[:18] or "mavzu"
        result["hashtagsUz"] = [f"#{slug}", "#reels", "#foydali"]
    result["hookUz"] = shots[0]["overlayTextUz"]
    result["ctaUz"] = shots[-1]["overlayTextUz"]
    return result


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


def coerce_timed_shots(
    result: dict[str, Any],
    *,
    duration_sec: int = DEFAULT_DURATION_SEC,
    brief: str = "",
    salt: str | None = None,
) -> list[dict[str, Any]]:
    """Normalize to 6–8 timed shots. Keep on-topic model copy; pack only fills holes."""
    salt = salt or f"coerce-{time.time_ns()}"
    defaults = default_montage_shots(brief, salt=salt)
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
        lifestyle_overlay = any(m in overlay.lower() for m in _LIFESTYLE_MARKERS)
        lifestyle_visual = any(m in visual.lower() for m in _LIFESTYLE_MARKERS)
        brief_wants_other = bool(brief.strip()) and not any(
            m in _canon_apostrophe(brief).lower() for m in ("muhabbat", "sevgi", "romant", "baxt", "qahva")
        )
        on_topic_visual = text_matches_brief_topic(visual, brief) if brief else False
        on_topic_overlay = text_matches_brief_topic(overlay, brief) if brief else False

        replace_visual = (
            not visual
            or len(visual) < 8
            or (key in seen)
            or (len(set(v for v in visuals if len(v) >= 8)) <= 1 and i > 0)
            or (brief_wants_other and lifestyle_visual)
            or (looks_like_gibberish_uz(visual) and not on_topic_visual)
        )
        if replace_visual:
            shot["visualUz"] = defaults[min(i, len(defaults) - 1)]["visualUz"]
        else:
            shot["visualUz"] = polish_uzbek_text(visual, max_words=16)
        seen.add(str(shot["visualUz"]).lower())

        replace_overlay = (
            not overlay
            or len(overlay.split()) < 2
            or (brief_wants_other and lifestyle_overlay)
            or (looks_like_gibberish_uz(overlay) and not on_topic_overlay)
        )
        if replace_overlay:
            shot["overlayTextUz"] = defaults[min(i, len(defaults) - 1)]["overlayTextUz"]
        else:
            shot["overlayTextUz"] = polish_uzbek_text(overlay, max_words=9)

        if not note or (looks_like_gibberish_uz(note) and not text_matches_brief_topic(note, brief)):
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
    result["schemaVersion"] = "1.5"
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

    topic = extract_brief_topic(brief)
    salt = str(result.get("_variantSalt") or f"norm-{time.time_ns()}")
    pack = pick_topic_variant(brief, salt=salt)  # fallback pool only

    title = polish_uzbek_text(str(result.get("titleUz") or ""), max_words=10)
    title_is_instruction = bool(title) and any(
        w in title.lower() for w in ("tuzib", "ssenariya", "senariya", "yozib", "qilamiz", "ber")
    )
    if (
        not title
        or looks_like_latin_topic_echo(title)
        or looks_like_gibberish_uz(title)
        or title_is_instruction
        or (brief and title.strip().lower() == brief.strip().lower()[: len(title)])
    ):
        result["titleUz"] = (pack["title"] if pack else topic)[:90] or "Yangi Reels ssenariysi"
    else:
        result["titleUz"] = title

    goal = polish_uzbek_text(str(result.get("goalUz") or ""), max_words=20)
    if (
        not goal
        or looks_like_latin_topic_echo(goal)
        or looks_like_gibberish_uz(goal)
        or (brief and "auditoriyani jalb" in goal.lower())
    ):
        result["goalUz"] = pack["goal"] if pack else (
            f"{topic} — auditoriyaga aniq va foydali tushuntirish" if topic else "Auditoriyaga foydali kontent"
        )
    else:
        result["goalUz"] = goal

    for key in ("hashtagsUz", "risksUz", "postingTipsUz", "captionAlternativesUz"):
        result[key] = as_str_list(result.get(key))

    shots = coerce_timed_shots(result, duration_sec=duration, brief=brief, salt=salt)
    result["shots"] = shots

    # Only if still lifestyle/off-topic — rebuild with a NEW rotating variant.
    if brief and is_off_topic_for_brief(result, brief):
        result = force_topic_scenario(result, brief, salt=f"{salt}-force")
        shots = coerce_timed_shots(result, duration_sec=duration, brief=brief, salt=f"{salt}-force2")
        result["shots"] = shots

    result["durationSec"] = int(shots[-1]["endSec"]) if shots else duration
    result["shotListUz"] = [
        f"{s['startSec']}-{s['endSec']}s · {s['visualUz']} · «{s['overlayTextUz']}»" for s in shots
    ]

    mood = polish_uzbek_text(str(result.get("musicMoodUz") or ""), max_words=8)
    mood_lifestyle = any(m in mood.lower() for m in ("romantik", "iliq lo-fi", "akustik, iliq"))
    if not mood or looks_like_gibberish_uz(mood) or (brief and pack and mood_lifestyle):
        result["musicMoodUz"] = pack["music"] if pack else "Aniqlik beruvchi, oddiy ritm"
    else:
        result["musicMoodUz"] = mood

    hook = polish_uzbek_text(
        str(result.get("hookUz") or shots[0].get("overlayTextUz") or ""),
        max_words=9,
    )
    if (
        not hook
        or any(m in hook.lower() for m in _LIFESTYLE_MARKERS)
        or (looks_like_gibberish_uz(hook) and not text_matches_brief_topic(hook, brief))
    ):
        hook = str(shots[0].get("overlayTextUz") or "Diqqat — muhim fikr")
    result["hookUz"] = hook

    caption = polish_uzbek_text(str(result.get("captionPrimaryUz") or ""), max_words=45)
    caption_l = caption.lower()
    caption_is_cta_like = bool(caption) and len(caption.split()) <= 6 and any(
        w in caption_l for w in ("izoh", "yozing", "like", "comment", "saqlang")
    )
    caption_lifestyle = sum(1 for m in _LIFESTYLE_MARKERS if m in caption_l) >= 1
    caption_has_raw_brief = bool(brief) and brief.strip().lower()[:40] in caption_l
    caption_ok_topic = text_matches_brief_topic(caption, brief) if brief else True
    if (
        not caption
        or len(caption) < 24
        or caption_is_cta_like
        or (brief and caption_lifestyle)
        or caption_has_raw_brief
        or (looks_like_gibberish_uz(caption) and not caption_ok_topic)
    ):
        if pack:
            result["captionPrimaryUz"] = pack["caption"]
        elif topic:
            result["captionPrimaryUz"] = (
                f"{topic}. Asosiy fikrlarni qisqa va aniq tushuntiramiz. "
                "Foydali bilib qoling — savolingizni izohlarda yozing."
            )
        else:
            result["captionPrimaryUz"] = (
                "Bugungi postda asosiy fikrni sodda tushuntiramiz. "
                "Foydali bo'lsa — izohlarda fikringizni yozing."
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
    else:
        result["whyThisWorksUz"] = "Qisqa hook, aniq kadrlar va CTA — insightsdagi ishlagan uslub."
    tips = [polish_uzbek_text(t, max_words=16) for t in as_str_list(result.get("postingTipsUz"))]
    result["postingTipsUz"] = [t for t in tips if t and not looks_like_gibberish_uz(t)][:4] or [
        "Birinchi 2 soniyada asosiy savolni qo'ying",
        "Har kadrda bitta fikr",
    ]

    tags = as_str_list(result.get("hashtagsUz"))
    cleaned_tags: list[str] = []
    for tag in tags[:8]:
        t = tag if tag.startswith("#") else f"#{tag}"
        t = "".join(ch for ch in t if ch.isalnum() or ch == "#" or ch in "ʻʼ'")
        if len(t) > 2:
            cleaned_tags.append(t.lower())
    lifestyle_tags = {"#estetika", "#muhabbat", "#baxt", "#kunliklahzalar"}
    if cleaned_tags and not (set(cleaned_tags) & lifestyle_tags):
        result["hashtagsUz"] = cleaned_tags
    elif pack:
        result["hashtagsUz"] = list(pack["tags"])
    else:
        slug = "".join(ch for ch in (topic or "mavzu").lower() if ch.isalnum())[:18] or "mavzu"
        result["hashtagsUz"] = [f"#{slug}", "#reels", "#foydali"]
    result.pop("_variantSalt", None)
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
