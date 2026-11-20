from __future__ import annotations

import os
from pathlib import Path

from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parents[1] / ".env")

OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://127.0.0.1:11434")
OLLAMA_VISION_MODEL = os.getenv("OLLAMA_MODEL", "qwen2.5vl:7b")
# Instruct (not coder) — SMM/insights Uzbek text on RTX 4060 8GB.
OLLAMA_TEXT_MODEL = os.getenv("OLLAMA_TEXT_MODEL", "qwen2.5:7b")

# Scenario text. Key stays in ai-service/.env (gitignored). Empty key keeps local Ollama.
GROQ_API_KEY = os.getenv("GROQ_API_KEY", "").strip()
GROQ_BASE_URL = os.getenv("GROQ_BASE_URL", "https://api.groq.com/openai/v1").rstrip("/")
GROQ_TEXT_MODEL = os.getenv("GROQ_TEXT_MODEL", "qwen/qwen3.8-27b")

ANALYZE_PROMPT_VERSION = "1.1"
INSIGHTS_PROMPT_VERSION = "1.2"
SCENARIO_PROMPT_VERSION = "1.5.1"

MAX_IMAGES = 4
MAX_IMAGE_BYTES = 4 * 1024 * 1024

# Local VITS weights. Ollama speech has no Uzbek voice.
SPEECH_VOICE_ID = "uz-mms"
