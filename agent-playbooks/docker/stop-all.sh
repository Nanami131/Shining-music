#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

info()  { echo -e "\033[1;34m[INFO]\033[0m  $*"; }
ok()    { echo -e "\033[1;32m[OK]\033[0m    $*"; }

info "Stopping the complete Docker stack..."
cd "$PROJECT_ROOT"
docker compose stop
ok "Docker containers stopped (data preserved in docker-data/)"

echo ""
ok "=== All services stopped ==="
