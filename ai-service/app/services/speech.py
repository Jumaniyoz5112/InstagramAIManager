"""Uzbek voices (VITS, 16 kHz). CPU only, so the vision GPU stays free."""

from __future__ import annotations

import io
import logging
import threading
import wave
from pathlib import Path

log = logging.getLogger("ai-service.speech")

_ROOT = Path(__file__).resolve().parents[3] / "BE" / "tools" / "voices"
VOICES = {
    "uz-mms": _ROOT / "mms-tts-uzb-script_cyrillic",
    "uz-girl": _ROOT / "mms-tts-uzbek-girl-voice_cyrillic",
}
DEFAULT_VOICE = "uz-mms"

_lock = threading.Lock()
_loaded: dict[str, tuple[object, object]] = {}

_DIGRAPHS = {
    "sh": "ш",
    "ch": "ч",
    "ng": "нг",
    "yo": "ё",
    "yu": "ю",
    "ya": "я",
    "ts": "ц",
    "o'": "ў",
    "g'": "ғ",
}
_LETTERS = {
    "a": "а",
    "b": "б",
    "c": "с",
    "d": "д",
    "e": "е",
    "f": "ф",
    "g": "г",
    "h": "ҳ",
    "i": "и",
    "j": "ж",
    "k": "к",
    "l": "л",
    "m": "м",
    "n": "н",
    "o": "о",
    "p": "п",
    "q": "қ",
    "r": "р",
    "s": "с",
    "t": "т",
    "u": "у",
    "v": "в",
    "w": "в",
    "x": "х",
    "y": "й",
    "z": "з",
}
_KEEP = set(" .,!?-0123456789")
_APOSTROPHES = "’‘ʻʼ`´ʹ′‛"


def latin_to_cyrillic(text: str) -> str:
    normalized = text.lower()
    for mark in _APOSTROPHES:
        normalized = normalized.replace(mark, "'")
    out: list[str] = []
    index = 0
    while index < len(normalized):
        pair = normalized[index : index + 2]
        if pair in _DIGRAPHS:
            out.append(_DIGRAPHS[pair])
            index += 2
            continue
        char = normalized[index]
        if char in _LETTERS:
            out.append(_LETTERS[char])
        elif char in _KEEP or "\u0400" <= char <= "\u04ff":
            out.append(char)
        index += 1
    return " ".join("".join(out).split())


def _load(voice: str):
    cached = _loaded.get(voice)
    if cached is not None:
        return cached
    path = VOICES[voice]
    if not (path / "model.safetensors").is_file():
        raise FileNotFoundError(f"speech model missing: {path}")
    import torch
    from transformers import AutoTokenizer, VitsModel

    log.info("Loading voice %s from %s", voice, path)
    tokenizer = AutoTokenizer.from_pretrained(path, local_files_only=True)
    model = VitsModel.from_pretrained(path, local_files_only=True)
    model.to(torch.device("cpu"))
    model.eval()
    # Lower noise keeps the rhythm steady. The default wobble is what makes these voices sound broken.
    model.noise_scale = 0.35
    model.noise_scale_duration = 0.15
    model.speaking_rate = 0.95
    _loaded[voice] = (model, tokenizer)
    return model, tokenizer


def _trim(samples, rate: int):
    import numpy as np

    amplitude = np.abs(samples)
    peak = float(amplitude.max()) if len(samples) else 0.0
    if peak < 1e-4:
        return samples
    hot = np.flatnonzero(amplitude > max(0.012, peak * 0.03))
    if len(hot) == 0:
        return samples
    pad = int(rate * 0.05)
    start = max(0, int(hot[0]) - pad)
    end = min(len(samples), int(hot[-1]) + pad)
    return samples[start:end]


def synthesize(text: str, voice: str) -> bytes:
    voice_id = voice if voice in VOICES else ""
    if not voice_id:
        raise ValueError("unknown voice")
    spoken = latin_to_cyrillic(text)
    if not spoken:
        raise ValueError("empty text")
    with _lock:
        model, tokenizer = _load(voice_id)
        import torch

        torch.manual_seed(7)
        inputs = tokenizer(spoken, return_tensors="pt")
        with torch.no_grad():
            samples = model(**inputs).waveform.squeeze().detach().cpu().numpy()
        rate = int(model.config.sampling_rate)
    import numpy as np

    samples = _trim(np.asarray(samples, dtype=np.float32), rate)
    peak = float(np.max(np.abs(samples))) if len(samples) else 0.0
    if peak > 1e-4:
        samples = samples * (0.9 / peak)
    pcm = np.clip(samples, -1.0, 1.0)
    pcm = (pcm * 32767.0).astype(np.int16)
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(rate)
        handle.writeframes(pcm.tobytes())
    return buffer.getvalue()
