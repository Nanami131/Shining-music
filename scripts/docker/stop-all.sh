#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
LOG_DIR="$PROJECT_ROOT/logs"

info()  { echo -e "\033[1;34m[INFO]\033[0m  $*"; }
ok()    { echo -e "\033[1;32m[OK]\033[0m    $*"; }

# ---------- 1. Stop frontend ----------
info "Stopping frontend..."
if [[ -f "$LOG_DIR/frontend.pid" ]]; then
    pid=$(cat "$LOG_DIR/frontend.pid")
    kill "$pid" 2>/dev/null && ok "Frontend (PID $pid) stopped" || ok "Frontend already stopped"
    rm -f "$LOG_DIR/frontend.pid"
else
    pkill -f "vite.*shining-ui" 2>/dev/null && ok "Frontend stopped" || ok "Frontend not running"
fi

# ---------- 2. Stop backend services ----------
SERVICES=(statistics-service community-service music-service user-service gateway-service)

for svc in "${SERVICES[@]}"; do
    if [[ -f "$LOG_DIR/$svc.pid" ]]; then
        pid=$(cat "$LOG_DIR/$svc.pid")
        kill "$pid" 2>/dev/null && ok "$svc (PID $pid) stopped" || ok "$svc already stopped"
        rm -f "$LOG_DIR/$svc.pid"
    else
        pkill -f "$svc-1.0-SNAPSHOT.jar" 2>/dev/null && ok "$svc stopped" || ok "$svc not running"
    fi
done

# ---------- 3. Stop Docker middleware ----------
STOP_DOCKER="${1:-}"
if [[ "$STOP_DOCKER" == "--with-docker" ]]; then
    info "Stopping Docker containers..."
    cd "$PROJECT_ROOT"
    docker compose stop
    ok "Docker containers stopped (data preserved in docker-data/)"
    echo "   To remove containers: docker compose down"
    echo "   To remove containers + data: docker compose down -v && rm -rf docker-data/"
else
    info "Docker containers left running (pass --with-docker to stop them too)"
fi

echo ""
ok "=== All application services stopped ==="
