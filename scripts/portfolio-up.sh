#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

HEALTH_URL="${HEALTH_URL:-http://localhost:8080/actuator/health}"
MAX_ATTEMPTS="${MAX_ATTEMPTS:-60}"
SLEEP_SECONDS="${SLEEP_SECONDS:-2}"

if [[ -f .env.example && ! -f .env ]]; then
  cp .env.example .env
  echo "Created .env from .env.example"
fi

docker compose up -d --build

echo "Waiting for health at ${HEALTH_URL} ..."
attempt=1
while (( attempt <= MAX_ATTEMPTS )); do
  if curl -fsS "$HEALTH_URL" >/dev/null 2>&1; then
    echo "AssinaFlow is healthy (${HEALTH_URL})"
    curl -fsS "$HEALTH_URL"
    echo
    exit 0
  fi
  sleep "$SLEEP_SECONDS"
  ((attempt++)) || true
done

echo "Health check failed after $((MAX_ATTEMPTS * SLEEP_SECONDS))s: ${HEALTH_URL}" >&2
docker compose ps >&2 || true
exit 1
