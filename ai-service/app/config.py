from __future__ import annotations

import os

OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://127.0.0.1:11434")
OLLAMA_VISION_MODEL = os.getenv("OLLAMA_MODEL", "qwen2.5vl:7b")
# Instruct (not coder) — SMM/insights Uzbek text on RTX 4060 8GB.
OLLAMA_TEXT_MODEL = os.getenv("OLLAMA_TEXT_MODEL", "qwen2.5:7b")

ANALYZE_PROMPT_VERSION = "1.1"
INSIGHTS_PROMPT_VERSION = "1.2"
SCENARIO_PROMPT_VERSION = "1.5.1"

MAX_IMAGES = 4
MAX_IMAGE_BYTES = 4 * 1024 * 1024
