#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
JDK18_HOME="${JDK18_HOME:-${JDK21_HOME:-$HOME/.local/java/jdk-18}}"
JAVA="$JDK18_HOME/bin/java"
LOG_DIR="$PROJECT_ROOT/logs"

info()  { echo -e "\033[1;34m[INFO]\033[0m  $*"; }
ok()    { echo -e "\033[1;32m[OK]\033[0m    $*"; }
err()   { echo -e "\033[1;31m[ERROR]\033[0m $*" >&2; }

if [[ ! -x "$JAVA" ]]; then
    err "JDK 18 not found at $JDK18_HOME"
    err "Set JDK18_HOME env var or install JDK 18 to ~/.local/java/"
    exit 1
fi

mkdir -p "$LOG_DIR"

# ---------- 1. Docker middleware ----------
info "Starting Docker middleware..."
cd "$PROJECT_ROOT"
docker compose up -d

info "Waiting for MySQL..."
for i in $(seq 1 30); do
    docker exec shining-mysql mysqladmin ping -h localhost -uroot -ppassword &>/dev/null && break
    sleep 2
done
info "Waiting for Nacos..."
for i in $(seq 1 20); do
    curl -sf "http://localhost:8848/nacos/v1/cs/configs?dataId=common.yaml&group=Shining&tenant=Shining" &>/dev/null && break
    sleep 3
done
ok "Middleware ready"

# ---------- 2. Backend services ----------
SERVICES=(gateway-service user-service music-service community-service statistics-service recommend-service)
PORTS=(8080 8081 8082 8083 8084 8085)

for i in "${!SERVICES[@]}"; do
    svc="${SERVICES[$i]}"
    port="${PORTS[$i]}"
    jar="$PROJECT_ROOT/$svc/target/$svc-1.0-SNAPSHOT.jar"

    if [[ ! -f "$jar" ]]; then
        info "JAR not found for $svc, building..."
        cd "$PROJECT_ROOT"
        JAVA_HOME="$JDK18_HOME" PATH="$JDK18_HOME/bin:$PATH" \
            mvn package -DskipTests -pl common,"$svc" -am -q
    fi

    if ss -tlnp 2>/dev/null | grep -q ":$port "; then
        ok "$svc already running on :$port"
        continue
    fi

    info "Starting $svc on :$port ..."
    nohup "$JAVA" -jar "$jar" > "$LOG_DIR/$svc.log" 2>&1 &
    echo $! > "$LOG_DIR/$svc.pid"
done

info "Waiting for backend services..."
for i in "${!SERVICES[@]}"; do
    svc="${SERVICES[$i]}"
    port="${PORTS[$i]}"
    for j in $(seq 1 40); do
        ss -tlnp 2>/dev/null | grep -q ":$port " && break
        sleep 2
    done
    if ss -tlnp 2>/dev/null | grep -q ":$port "; then
        ok "$svc :$port ready"
    else
        err "$svc :$port failed to start — check $LOG_DIR/$svc.log"
    fi
done

# ---------- 3. Frontend ----------
if ss -tlnp 2>/dev/null | grep -q ":5173 "; then
    ok "Frontend already running on :5173"
else
    info "Starting frontend..."
    cd "$PROJECT_ROOT/shining-ui"
    nohup npm run dev > "$LOG_DIR/frontend.log" 2>&1 &
    echo $! > "$LOG_DIR/frontend.pid"
    sleep 5
    if ss -tlnp 2>/dev/null | grep -q ":5173 "; then
        ok "Frontend :5173 ready"
    else
        err "Frontend failed to start — check $LOG_DIR/frontend.log"
    fi
fi

echo ""
ok "=== All services started ==="
echo "   Frontend:   http://localhost:5173"
echo "   Gateway:    http://localhost:8080"
echo "   Nacos:      http://localhost:8848/nacos"
echo "   MinIO:      http://localhost:9090"
echo "   RabbitMQ:   http://localhost:15672"
echo "   Logs:       $LOG_DIR/"
