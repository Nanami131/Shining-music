#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
LOG_DIR="$PROJECT_ROOT/logs"

info()  { echo -e "\033[1;34m[INFO]\033[0m  $*"; }
ok()    { echo -e "\033[1;32m[OK]\033[0m    $*"; }
err()   { echo -e "\033[1;31m[ERROR]\033[0m $*" >&2; }

mkdir -p "$LOG_DIR"

info "Stopping legacy host processes..."
LEGACY_PROCESSES=(frontend gateway-service user-service music-service community-service statistics-service recommend-service)
for process_name in "${LEGACY_PROCESSES[@]}"; do
    pid_file="$LOG_DIR/$process_name.pid"
    [[ -f "$pid_file" ]] || continue
    pid="$(cat "$pid_file")"
    if [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null; then
        kill "$pid"
    fi
    rm -f "$pid_file"
done

cd "$PROJECT_ROOT"
info "Building and starting the complete Docker stack..."
docker compose build gateway-service frontend elasticsearch
docker compose up -d --no-build --remove-orphans

info "Waiting for the frontend..."
for _ in $(seq 1 60); do
    if curl -fsS http://localhost:5173/healthz >/dev/null; then
        ok "Frontend ready on :5173"
        break
    fi
    sleep 2
done

if ! curl -fsS http://localhost:5173/healthz >/dev/null; then
    err "Frontend failed to start"
    docker compose ps
    exit 1
fi

TUNNEL_URL=""
for _ in $(seq 1 45); do
    TUNNEL_LOGS="$(docker compose logs cloudflared 2>&1)"
    if grep -q "Registered tunnel connection" <<< "$TUNNEL_LOGS"; then
        TUNNEL_URL="$(sed -n 's#.*\(https://[-a-z0-9]*\.trycloudflare\.com\).*#\1#p' <<< "$TUNNEL_LOGS" | tail -n 1)"
        [[ -n "$TUNNEL_URL" ]] && break
    fi
    sleep 2
done

if [[ -z "$TUNNEL_URL" ]]; then
    err "Cloudflare Tunnel failed to connect"
    docker compose logs --tail=80 cloudflared
    exit 1
fi

echo ""
ok "=== All services started ==="
echo "   Frontend:   http://localhost:5173"
echo "   Public:     $TUNNEL_URL"
echo "   Logs:       docker compose logs -f"
