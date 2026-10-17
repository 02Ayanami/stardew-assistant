#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ ! -f .env ]; then printf '请先复制 .env.example 为 .env 并填写密钥。
' >&2; exit 1; fi
# Only the new project is stopped; no shared or legacy services are addressed.
docker compose -p stardew-assistant-simple stop backend frontend
docker compose -p stardew-assistant-simple up -d --wait mysql milvus
docker compose -p stardew-assistant-simple build backend
docker compose -p stardew-assistant-simple run --rm --no-deps backend --spring.main.web-application-type=none --assistant.import=true
docker compose -p stardew-assistant-simple up -d --build --wait backend frontend
