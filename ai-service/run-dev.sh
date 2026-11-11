#!/usr/bin/env bash
# Single-worker AI service — models run strictly one-at-a-time (RTX 4060 8GB).
set -euo pipefail
cd "$(dirname "$0")"

export OLLAMA_NUM_PARALLEL="${OLLAMA_NUM_PARALLEL:-1}"
export OLLAMA_MAX_LOADED_MODELS="${OLLAMA_MAX_LOADED_MODELS:-1}"

exec .venv/Scripts/python.exe -m uvicorn app.main:app \
  --host 127.0.0.1 \
  --port 8000 \
  --workers 1
