#!/usr/bin/env bash
# Load BE/.env into the process environment, then start Spring Boot.
set -euo pipefail
cd "$(dirname "$0")"
if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
  echo "Loaded .env (INSTAGRAM_ENABLED=${INSTAGRAM_ENABLED:-unset})"
else
  echo "WARNING: .env not found — Instagram will stay disabled"
fi
exec ./gradlew bootRun "$@"
