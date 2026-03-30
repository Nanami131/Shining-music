#!/usr/bin/env python3
"""
Restore Elasticsearch index from exported JSON file.

Usage:
    python3 restore-es.py <path-to-elasticsearch_music_search.json>

The JSON file contains:
  - index: index name
  - mapping: ES mapping definition
  - settings: ES index settings
  - documents: list of {_id, _source} dicts
"""
import sys
import json
import requests
import time

ES_URL = "http://localhost:9200"


def main():
    if len(sys.argv) < 2:
        print(f"Usage: {sys.argv[0]} <json-file>", file=sys.stderr)
        sys.exit(1)

    json_path = sys.argv[1]
    with open(json_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    index = data["index"]
    mapping = data["mapping"]
    settings = data.get("settings", {"number_of_shards": 1, "number_of_replicas": 1})
    documents = data["documents"]

    print(f"Index: {index}")
    print(f"Documents: {len(documents)}")

    # Wait for ES to be available
    for i in range(30):
        try:
            r = requests.get(f"{ES_URL}/_cluster/health", timeout=5)
            if r.status_code == 200:
                break
        except Exception:
            pass
        time.sleep(2)
    else:
        print("ERROR: Elasticsearch not reachable", file=sys.stderr)
        sys.exit(1)

    # Delete existing index if present
    requests.delete(f"{ES_URL}/{index}")

    # Create index with mapping
    body = {
        "settings": settings,
        "mappings": mapping
    }
    r = requests.put(f"{ES_URL}/{index}", json=body)
    if r.status_code not in (200, 201):
        print(f"ERROR creating index: {r.status_code} {r.text}", file=sys.stderr)
        sys.exit(1)
    print(f"Index '{index}' created")

    # Bulk insert documents
    if not documents:
        print("No documents to import")
        return

    bulk_lines = []
    for doc in documents:
        action = json.dumps({"index": {"_index": index, "_id": doc["_id"]}})
        source = json.dumps(doc["_source"], ensure_ascii=False)
        bulk_lines.append(action)
        bulk_lines.append(source)
    bulk_body = "\n".join(bulk_lines) + "\n"

    r = requests.post(
        f"{ES_URL}/_bulk",
        data=bulk_body.encode("utf-8"),
        headers={"Content-Type": "application/x-ndjson"}
    )

    result = r.json()
    errors = sum(1 for item in result.get("items", []) if item.get("index", {}).get("error"))
    imported = len(result.get("items", [])) - errors

    print(f"Imported: {imported}, Errors: {errors}")

    # Refresh to make docs searchable
    requests.post(f"{ES_URL}/{index}/_refresh")

    # Verify count
    r = requests.get(f"{ES_URL}/{index}/_count")
    count = r.json().get("count", 0)
    print(f"Verified: {count} documents in index")


if __name__ == "__main__":
    main()
