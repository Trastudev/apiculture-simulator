#!/bin/bash
# Reinicia la API en esta máquina. Lo llama el despliegue de GitHub Actions.
set -euo pipefail
cd /opt/apiculture/apiculture-simulator/server

if [[ ! -f .env ]]; then
  echo "Falta server/.env en el servidor." >&2
  exit 1
fi

stop_pid() {
  local pid="$1"
  if [[ -z "${pid}" ]] || ! kill -0 "${pid}" 2>/dev/null; then
    return 0
  fi
  kill "${pid}" 2>/dev/null || true
  local i
  for i in 1 2 3 4 5 6 7 8 9 10; do
    kill -0 "${pid}" 2>/dev/null || return 0
    sleep 0.3
  done
  kill -9 "${pid}" 2>/dev/null || true
}

if [[ -f /tmp/apiculture-api.pid ]]; then
  stop_pid "$(cat /tmp/apiculture-api.pid || true)"
fi

# El proceso actual se arrancó con nohup; el pidfile puede no coincidir.
mapfile -t leftovers < <(pgrep -f '/usr/bin/node src/index.js' || true)
for pid in "${leftovers[@]}"; do
  stop_pid "${pid}"
done

set -a
# shellcheck disable=SC1091
. ./.env
set +a

nohup /usr/bin/node src/index.js >> /tmp/apiculture-api.log 2>&1 &
echo $! > /tmp/apiculture-api.pid

for _ in 1 2 3 4 5 6 7 8 9 10 11 12; do
  if curl -sf http://127.0.0.1:8080/health; then
    echo
    exit 0
  fi
  sleep 0.5
done

echo "La API no respondió en /health" >&2
tail -n 40 /tmp/apiculture-api.log >&2
exit 1
