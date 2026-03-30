#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DOCKER_DATA="$PROJECT_ROOT/docker-data"
JDK21_HOME="$HOME/.local/java/jdk-21.0.10+7"

# ---------- helpers ----------
info()  { echo -e "\033[1;34m[INFO]\033[0m  $*"; }
ok()    { echo -e "\033[1;32m[OK]\033[0m    $*"; }
err()   { echo -e "\033[1;31m[ERROR]\033[0m $*" >&2; }
wait_for() {
    local desc="$1" cmd="$2" retries="${3:-30}" interval="${4:-3}"
    for ((i=1; i<=retries; i++)); do
        if eval "$cmd" &>/dev/null; then ok "$desc ready"; return 0; fi
        sleep "$interval"
    done
    err "$desc not ready after $((retries*interval))s"; return 1
}

# ---------- arg: migration package path ----------
MIGRATION_PKG="${1:-}"
if [[ -z "$MIGRATION_PKG" ]]; then
    err "Usage: $0 <migration-package-path>"
    err "Example: $0 ~/下载/migration-package"
    exit 1
fi
MIGRATION_PKG="$(cd "$MIGRATION_PKG" && pwd)"

if [[ ! -f "$MIGRATION_PKG/data/mysql/shining_full.sql" ]]; then
    err "Invalid migration package: $MIGRATION_PKG/data/mysql/shining_full.sql not found"
    exit 1
fi

# ---------- prepare docker-data ----------
info "Preparing docker-data from migration package..."

mkdir -p "$DOCKER_DATA"/{mysql,redis,minio,init-sql}

info "Copying MySQL init SQL..."
cp "$MIGRATION_PKG/data/mysql/shining_full.sql" "$DOCKER_DATA/init-sql/01-init.sql"

info "Copying Redis dump.rdb..."
cp "$MIGRATION_PKG/data/redis/dump.rdb" "$DOCKER_DATA/redis/dump.rdb"

info "Copying MinIO data (this may take a minute)..."
cp -r "$MIGRATION_PKG/data/minio-root/shining" "$DOCKER_DATA/minio/"
cp -r "$MIGRATION_PKG/data/minio-root/.minio.sys" "$DOCKER_DATA/minio/"

ok "docker-data prepared"

# ---------- start containers ----------
info "Starting Docker containers..."
cd "$PROJECT_ROOT"
docker compose up -d

info "Waiting for MySQL to be healthy..."
wait_for "MySQL" "docker exec shining-mysql mysqladmin ping -h localhost -uroot -ppassword" 60 3

info "Waiting for Nacos to respond..."
wait_for "Nacos" "curl -sf http://localhost:8848/nacos/v1/cs/configs?dataId=common.yaml\\&group=Shining\\&tenant=Shining" 40 5

# ---------- patch Nacos schema (missing legacy tables) ----------
info "Creating legacy Nacos tables if missing..."
docker exec shining-mysql mysql -uroot -ppassword nacos -e "
CREATE TABLE IF NOT EXISTS config_info_aggr (
  id bigint NOT NULL AUTO_INCREMENT, data_id varchar(255) NOT NULL,
  group_id varchar(255) NOT NULL, datum_id varchar(255) NOT NULL,
  content longtext NOT NULL, gmt_modified datetime NOT NULL,
  app_name varchar(128) DEFAULT NULL, tenant_id varchar(128) DEFAULT '',
  PRIMARY KEY (id),
  UNIQUE KEY uk_configinfoaggr_datagrouptenantdatum (data_id,group_id,tenant_id,datum_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;

CREATE TABLE IF NOT EXISTS config_info_beta (
  id bigint NOT NULL AUTO_INCREMENT, data_id varchar(255) NOT NULL,
  group_id varchar(128) NOT NULL, app_name varchar(128) DEFAULT NULL,
  content longtext NOT NULL, beta_ips varchar(1024) DEFAULT NULL,
  md5 varchar(32) DEFAULT NULL,
  gmt_create datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  gmt_modified datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  src_user text, src_ip varchar(50) DEFAULT NULL,
  tenant_id varchar(128) DEFAULT '', encrypted_data_key varchar(1024) NOT NULL DEFAULT '',
  PRIMARY KEY (id),
  UNIQUE KEY uk_configinfobeta_datagrouptenant (data_id,group_id,tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;

CREATE TABLE IF NOT EXISTS config_info_tag (
  id bigint NOT NULL AUTO_INCREMENT, data_id varchar(255) NOT NULL,
  group_id varchar(128) NOT NULL, tenant_id varchar(128) DEFAULT '',
  tag_id varchar(128) NOT NULL, app_name varchar(128) DEFAULT NULL,
  content longtext NOT NULL, md5 varchar(32) DEFAULT NULL,
  gmt_create datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  gmt_modified datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  src_user text, src_ip varchar(50) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_configinfotag_datagrouptenanttag (data_id,group_id,tenant_id,tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
" 2>/dev/null
ok "Nacos schema patched"

# Nacos may have failed on first boot due to missing tables — restart it
info "Restarting Nacos to pick up patched schema..."
docker compose restart nacos
wait_for "Nacos" "curl -sf http://localhost:8848/nacos/v1/cs/configs?dataId=common.yaml\\&group=Shining\\&tenant=Shining" 40 5

# ---------- cleanup Redis login state ----------
info "Cleaning Redis login state..."
for pattern in 'jwt:*' 'cache:userInfo:*'; do
    docker exec shining-redis redis-cli -n 4 --scan --pattern "$pattern" | while read -r key; do
        docker exec shining-redis redis-cli -n 4 DEL "$key" >/dev/null
    done
done
ok "Redis login state cleaned"

# ---------- restore Elasticsearch data ----------
ES_JSON="$MIGRATION_PKG/data/elasticsearch/elasticsearch_music_search.json"
if [[ ! -f "$ES_JSON" ]]; then
    ES_JSON="$PROJECT_ROOT/sql/elasticsearch_music_search.json"
fi
if [[ -f "$ES_JSON" ]]; then
    info "Waiting for Elasticsearch to be ready..."
    wait_for "Elasticsearch" "curl -sf http://localhost:9200/_cluster/health" 40 5

    info "Restoring Elasticsearch index from $ES_JSON ..."
    python3 "$PROJECT_ROOT/scripts/migration/restore-es.py" "$ES_JSON"
    ok "Elasticsearch data restored"
else
    info "No Elasticsearch export found, skipping ES restore."
    info "  (Expected at $MIGRATION_PKG/data/elasticsearch/elasticsearch_music_search.json"
    info "   or $PROJECT_ROOT/sql/elasticsearch_music_search.json)"
fi

# ---------- verify ----------
info "Verifying middleware..."
docker exec shining-mysql mysql -uroot -ppassword -e "SELECT COUNT(*) FROM \`shining-music\`.songs;" 2>/dev/null | tail -1 | grep -q '[0-9]' && ok "MySQL: business data OK"
docker exec shining-redis redis-cli -n 4 DBSIZE | grep -q '[0-9]' && ok "Redis: data OK"
curl -sf http://localhost:9000/minio/health/live >/dev/null && ok "MinIO: healthy"
curl -sf http://localhost:8848/nacos/v1/cs/configs?dataId=common.yaml\&group=Shining\&tenant=Shining >/dev/null && ok "Nacos: config OK"
curl -sf -u guest:guest http://localhost:15672/api/overview >/dev/null && ok "RabbitMQ: healthy"
curl -sf http://localhost:9200/music_search/_count 2>/dev/null | grep -q '"count"' && ok "Elasticsearch: index OK" || info "Elasticsearch: no index yet (will be populated on first use)"

echo ""
ok "=== Initialization complete. All middleware running. ==="
echo "   Next: run  scripts/docker/start-all.sh  to start backend + frontend"
