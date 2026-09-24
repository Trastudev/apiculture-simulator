#!/bin/sh
set -eu
cd "$(dirname "$0")"
if [ ! -f .env ]; then
  echo "Falta server/.env. Copia .env.example y pon PGPASSWORD y API_TOKEN." >&2
  exit 1
fi
if ! command -v docker >/dev/null 2>&1; then
  apt-get update
  apt-get install -y docker.io docker-compose-v2
fi
docker compose up -d --build
docker compose ps
