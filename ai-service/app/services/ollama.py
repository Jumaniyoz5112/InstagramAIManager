from __future__ import annotations

import base64
import json
import logging
import re
import threading
from typing import Any

import httpx
from fastapi import HTTPException

from app.config import MAX_IMAGE_BYTES, OLLAMA_BASE_URL

log = logging.getLogger("ai-service.ollama")

# RTX 4060 8GB: vision + text never concurrent — one Ollama call at a time.
_OLLAMA_LOCK = threading.Lock()
_busy_model: str | None = None


def ollama_busy() -> dict[str, Any]:
    return {"busy": _OLLAMA_LOCK.locked(), "model": _busy_model}


def ollama_reachable(timeout: float = 3.0) -> bool:
    try:
        with httpx.Client(timeout=timeout) as client:
            r = client.get(f"{OLLAMA_BASE_URL}/api/tags")
            return r.status_code == 200
    except Exception:
        return False


def ollama_chat(
    *,
    model: str,
    system: str,
    user_text: str,
    images: list[str] | None,
    temperature: float,
    repeat_penalty: float | None = None,
) -> dict[str, Any]:
    global _busy_model
    user_msg: dict[str, Any] = {"role": "user", "content": user_text}
    if images:
        user_msg["images"] = images
    options: dict[str, Any] = {"temperature": temperature}
    if repeat_penalty is not None:
        options["repeat_penalty"] = repeat_penalty
    payload: dict[str, Any] = {
        "model": model,
        "stream": False,
        "format": "json",
        "messages": [
            {"role": "system", "content": system},
            user_msg,
        ],
        "options": options,
    }
    with _OLLAMA_LOCK:
        _busy_model = model
        log.info("Ollama sequential start model=%s", model)
        try:
            with httpx.Client(timeout=httpx.Timeout(300.0, connect=10.0)) as client:
                r = client.post(f"{OLLAMA_BASE_URL}/api/chat", json=payload)
                r.raise_for_status()
                data = r.json()
        except httpx.HTTPError as e:
            log.exception("Ollama chat failed model=%s", model)
            raise HTTPException(status_code=502, detail=f"Ollama error: {e}") from e
        finally:
            _busy_model = None
            log.info("Ollama sequential end model=%s", model)

    content = ""
    message = data.get("message") or {}
    if isinstance(message, dict):
        content = str(message.get("content") or "")
    if not content:
        raise HTTPException(status_code=502, detail="Empty model response")
    return parse_json(content)


def download_as_b64(url: str) -> str:
    headers = {
        "User-Agent": "InstagramAIManager-AI/0.3 (+local analysis)",
        "Accept": "image/*,*/*",
    }
    with httpx.Client(timeout=30.0, follow_redirects=True, headers=headers) as client:
        r = client.get(url)
        r.raise_for_status()
        raw = r.content
    if not raw:
        raise ValueError("empty image body")
    if len(raw) > MAX_IMAGE_BYTES:
        raw = raw[:MAX_IMAGE_BYTES]
    return base64.b64encode(raw).decode("ascii")


def parse_json(text: str) -> dict[str, Any]:
    text = text.strip()
    try:
        obj = json.loads(text)
        if isinstance(obj, dict):
            return obj
    except json.JSONDecodeError:
        pass
    match = re.search(r"\{.*\}", text, re.DOTALL)
    if match:
        obj = json.loads(match.group(0))
        if isinstance(obj, dict):
            return obj
    raise HTTPException(status_code=502, detail="Model did not return valid JSON")
