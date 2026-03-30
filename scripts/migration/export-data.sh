#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SQL_DIR="$PROJECT_ROOT/sql"

info()  { echo -e "\033[1;34m[INFO]\033[0m  $*"; }
ok()    { echo -e "\033[1;32m[OK]\033[0m    $*"; }
err()   { echo -e "\033[1;31m[ERROR]\033[0m $*" >&2; }

# ---------- MySQL ----------
info "Exporting MySQL databases..."
docker exec shining-mysql mysqldump -uroot -ppassword \
  --databases "shining-music" "shining-user" "shining-community" "shining-statistics" \
  --add-drop-database --add-drop-table \
  --set-gtid-purged=OFF \
  --routines --triggers \
  --complete-insert \
  --default-character-set=utf8mb4 \
  2>/dev/null > "$SQL_DIR/shining_full.sql"

MYSQL_SIZE=$(du -h "$SQL_DIR/shining_full.sql" | cut -f1)
ok "MySQL exported: $SQL_DIR/shining_full.sql ($MYSQL_SIZE)"

# ---------- Elasticsearch ----------
info "Exporting Elasticsearch index..."
python3 -c "
import requests, json
ES = 'http://localhost:9200'
INDEX = 'music_search'
mapping = requests.get(f'{ES}/{INDEX}/_mapping').json()
docs = []
r = requests.get(f'{ES}/{INDEX}/_search', json={'size': 10000, 'query': {'match_all': {}}})
for h in r.json().get('hits', {}).get('hits', []):
    docs.append({'_id': h['_id'], '_source': h['_source']})
export = {
    'index': INDEX,
    'mapping': mapping[INDEX]['mappings'],
    'settings': {'number_of_shards': 1, 'number_of_replicas': 1},
    'documents': docs
}
out = '$SQL_DIR/elasticsearch_music_search.json'
with open(out, 'w', encoding='utf-8') as f:
    json.dump(export, f, ensure_ascii=False, indent=2)
print(f'{len(docs)} documents exported')
"

ES_SIZE=$(du -h "$SQL_DIR/elasticsearch_music_search.json" | cut -f1)
ok "Elasticsearch exported: $SQL_DIR/elasticsearch_music_search.json ($ES_SIZE)"

# ---------- Summary ----------
echo ""
info "MinIO data lives in docker-data/minio/ (synced via Docker volume)"
echo ""

SONGS=$(docker exec shining-mysql mysql -uroot -ppassword -N -e "SELECT COUNT(*) FROM \`shining-music\`.songs;" 2>/dev/null)
SINGERS=$(docker exec shining-mysql mysql -uroot -ppassword -N -e "SELECT COUNT(*) FROM \`shining-music\`.singers;" 2>/dev/null)
LYRICS=$(docker exec shining-mysql mysql -uroot -ppassword -N -e "SELECT COUNT(*) FROM \`shining-music\`.lyrics;" 2>/dev/null)
ES_COUNT=$(curl -s http://localhost:9200/music_search/_count 2>/dev/null | python3 -c "import sys,json; print(json.load(sys.stdin).get('count',0))")

ok "=== Export Summary ==="
echo "   Songs:   $SONGS"
echo "   Singers: $SINGERS"
echo "   Lyrics:  $LYRICS"
echo "   ES docs: $ES_COUNT"
echo "   Files:"
echo "     - $SQL_DIR/shining_full.sql"
echo "     - $SQL_DIR/elasticsearch_music_search.json"
echo "     - docker-data/minio/ (volume-mapped)"
