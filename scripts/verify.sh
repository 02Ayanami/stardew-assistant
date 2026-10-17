#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
docker compose -p stardew-assistant-simple up -d --wait mysql milvus
(cd backend && ./mvnw -B verify)
(cd frontend && npm ci && npm test -- --run && npm run build && npm run test:e2e)
