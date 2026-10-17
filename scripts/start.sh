#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ ! -f .env ]; then cp .env.example .env; fi
docker compose -p stardew-assistant-simple up -d --build --wait mysql milvus backend frontend
printf '
新项目页面：http://127.0.0.1:8081
首次使用请填写本项目 .env 的密钥，再运行 ./scripts/import.sh。
'
