#!/usr/bin/env python3
import json
import shutil
import sqlite3
import sys
from datetime import datetime
from pathlib import Path


CURRENT_GLOBAL = Path("/home/chenxinyao/.config/Cursor/User/globalStorage/state.vscdb")
CURRENT_GLOBAL_SHM = Path("/home/chenxinyao/.config/Cursor/User/globalStorage/state.vscdb-shm")
CURRENT_GLOBAL_WAL = Path("/home/chenxinyao/.config/Cursor/User/globalStorage/state.vscdb-wal")
SOURCE_GLOBAL = Path(
    "/home/chenxinyao/.config/Cursor/User/globalStorage/state.vscdb.backup.fix-attempt-20260325T092342"
)
SOURCE_WORKSPACE = Path(
    "/home/chenxinyao/.config/Cursor-fix-backups/20260326T102230/workspaceStorage/527b7dd64a62b71506a6d7272c4544ec/state.vscdb"
)
TARGET_PANEL_ID = "d2a03f9b-1ef8-41b1-a504-756635a08ff9"

PINNED_KEY = "workbench.auxiliarybar.pinnedPanels"
PLACEHOLDER_KEY = "workbench.auxiliarybar.placeholderPanels"
PANE_KEY = f"workbench.panel.composerChatViewPane.{TARGET_PANEL_ID}"
PANE_HIDDEN_KEY = f"{PANE_KEY}.hidden"
PANEL_FULL_ID = f"workbench.panel.aichat.{TARGET_PANEL_ID}"


def load_row(db_path: Path, key: str) -> str:
    conn = sqlite3.connect(str(db_path))
    try:
        row = conn.execute("select value from ItemTable where key=?", (key,)).fetchone()
        if not row:
            raise RuntimeError(f"missing key in source db: {key}")
        return row[0]
    finally:
        conn.close()


def backup_file(src: Path, backup_dir: Path) -> None:
    if src.exists():
        shutil.copy2(src, backup_dir / src.name)


def merge_json_array(existing_raw: str, source_raw: str, item_id: str) -> str:
    existing = json.loads(existing_raw) if existing_raw else []
    source = json.loads(source_raw) if source_raw else []
    if any(item.get("id") == item_id for item in existing):
        return json.dumps(existing, ensure_ascii=False, separators=(",", ":"))
    source_item = next((item for item in source if item.get("id") == item_id), None)
    if not source_item:
        raise RuntimeError(f"missing item {item_id} in source array")
    existing.insert(0, source_item)
    return json.dumps(existing, ensure_ascii=False, separators=(",", ":"))


def main() -> int:
    if not CURRENT_GLOBAL.exists():
        print(f"missing current db: {CURRENT_GLOBAL}", file=sys.stderr)
        return 1
    if not SOURCE_GLOBAL.exists():
        print(f"missing source db: {SOURCE_GLOBAL}", file=sys.stderr)
        return 1
    if not SOURCE_WORKSPACE.exists():
        print(f"missing source db: {SOURCE_WORKSPACE}", file=sys.stderr)
        return 1

    ts = datetime.now().strftime("%Y%m%dT%H%M%S")
    backup_dir = Path("/tmp") / f"cursor-lastnight-chat-backup-{ts}"
    backup_dir.mkdir(parents=True, exist_ok=True)
    backup_file(CURRENT_GLOBAL, backup_dir)
    backup_file(CURRENT_GLOBAL_SHM, backup_dir)
    backup_file(CURRENT_GLOBAL_WAL, backup_dir)

    source_rows = {
        PINNED_KEY: load_row(SOURCE_GLOBAL, PINNED_KEY),
        PLACEHOLDER_KEY: load_row(SOURCE_GLOBAL, PLACEHOLDER_KEY),
        PANE_KEY: load_row(SOURCE_WORKSPACE, PANE_KEY),
        PANE_HIDDEN_KEY: load_row(SOURCE_GLOBAL, PANE_HIDDEN_KEY),
    }

    conn = sqlite3.connect(str(CURRENT_GLOBAL))
    try:
        cur = conn.cursor()
        existing_pinned = cur.execute("select value from ItemTable where key=?", (PINNED_KEY,)).fetchone()
        existing_placeholder = cur.execute("select value from ItemTable where key=?", (PLACEHOLDER_KEY,)).fetchone()

        if not existing_pinned or not existing_placeholder:
            raise RuntimeError("missing pinned/placeholder keys in current db")

        merged_pinned = merge_json_array(existing_pinned[0], source_rows[PINNED_KEY], PANEL_FULL_ID)
        merged_placeholder = merge_json_array(
            existing_placeholder[0], source_rows[PLACEHOLDER_KEY], PANEL_FULL_ID
        )

        cur.execute("begin")
        cur.execute("update ItemTable set value=? where key=?", (merged_pinned, PINNED_KEY))
        cur.execute("update ItemTable set value=? where key=?", (merged_placeholder, PLACEHOLDER_KEY))
        cur.execute(
            "insert into ItemTable(key, value) values(?, ?) on conflict(key) do update set value=excluded.value",
            (PANE_KEY, source_rows[PANE_KEY]),
        )
        cur.execute(
            "insert into ItemTable(key, value) values(?, ?) on conflict(key) do update set value=excluded.value",
            (PANE_HIDDEN_KEY, source_rows[PANE_HIDDEN_KEY]),
        )
        conn.commit()
    finally:
        conn.close()

    print(str(backup_dir))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
