from __future__ import annotations

import logging
from typing import Any

import httpx
from fastapi import HTTPException

from app import config
from app.services.ollama import parse_json

log = logging.getLogger("ai-service.groq")


def groq_configured() -> bool:
    return bool(config.GROQ_API_KEY)


def groq_json_chat(*, system: str, user_text: str, temperature: float) -> dict[str, Any]:
    """Instruct-mode JSON chat. Reasoning stays off so the free token cap is spent on the scenario."""
    if not config.GROQ_API_KEY:
        raise HTTPException(status_code=503, detail="Groq API key is not configured")

    payload: dict[str, Any] = {
        "model": config.GROQ_TEXT_MODEL,
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": user_text},
        ],
        "temperature": temperature,
        "top_p": 0.8,
        "presence_penalty": 1.2,
        "max_completion_tokens": 4096,
        "stream": False,
        "response_format": {"type": "json_object"},
        "reasoning_effort": "none",
        "reasoning_format": "hidden",
    }
    headers = {
        "Authorization": f"Bearer {config.GROQ_API_KEY}",
        "Content-Type": "application/json",
    }
    log.info("Groq scenario start model=%s", config.GROQ_TEXT_MODEL)
    try:
        with httpx.Client(timeout=httpx.Timeout(180.0, connect=10.0)) as client:
            response = client.post(
                f"{config.GROQ_BASE_URL}/chat/completions",
                json=payload,
                headers=headers,
            )
    except httpx.HTTPError as exc:
        log.exception("Groq chat failed")
        raise HTTPException(status_code=502, detail=f"Groq error: {exc}") from exc
    finally:
        log.info("Groq scenario end model=%s", config.GROQ_TEXT_MODEL)

    if response.status_code == 429:
        detail = "Groq rate limit reached"
        try:
            body = response.json()
            message = body.get("error", body).get("message") if isinstance(body, dict) else None
            if isinstance(message, str) and message.strip():
                detail = f"Groq rate limit reached: {message.strip()[:180]}"
        except Exception:
            pass
        log.warning("%s", detail)
        raise HTTPException(status_code=429, detail=detail)
    if response.status_code in (401, 403):
        raise HTTPException(status_code=502, detail="Groq rejected the API key")
    try:
        response.raise_for_status()
        data = response.json()
    except httpx.HTTPError as exc:
        detail = response.text[:300] if response.text else str(exc)
        raise HTTPException(status_code=502, detail=f"Groq error: {detail}") from exc

    content = ""
    choices = data.get("choices") or []
    if choices and isinstance(choices[0], dict):
        message = choices[0].get("message") or {}
        if isinstance(message, dict):
            content = str(message.get("content") or "")
    if not content:
        raise HTTPException(status_code=502, detail="Empty Groq response")
    return parse_json(content)
