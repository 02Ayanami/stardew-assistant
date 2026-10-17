#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
docker compose -p stardew-assistant-simple down
