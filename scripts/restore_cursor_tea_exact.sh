#!/usr/bin/env bash
set -euo pipefail

BACKUP_ROOT="$HOME/.config/Cursor-fix-backups/20260326T102230"
CURSOR_ROOT="$HOME/.config/Cursor"
WORKSPACE_ID="527b7dd64a62b71506a6d7272c4544ec"
WORKSPACE_PATH="/home/chenxinyao/code/simulation-platform-tea"

pkill -f '/usr/share/cursor/cursor' || true
sleep 1

mkdir -p "$CURSOR_ROOT/User/workspaceStorage"
rm -rf "$CURSOR_ROOT/User/workspaceStorage/$WORKSPACE_ID"
cp -a "$BACKUP_ROOT/workspaceStorage/$WORKSPACE_ID" "$CURSOR_ROOT/User/workspaceStorage/"

rm -rf "$CURSOR_ROOT/Session Storage" "$CURSOR_ROOT/Local Storage"
cp -a "$BACKUP_ROOT/Session Storage" "$CURSOR_ROOT/"
cp -a "$BACKUP_ROOT/Local Storage" "$CURSOR_ROOT/"

DISPLAY=:1 \
XAUTHORITY=/run/user/1001/gdm/Xauthority \
DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/1001/bus \
/usr/share/cursor/cursor --disable-gpu --new-window "$WORKSPACE_PATH" \
  >/tmp/cursor-tea.log 2>&1 &

echo "restored tea session from backup"
echo "log: /tmp/cursor-tea.log"
