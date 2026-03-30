#!/usr/bin/env bash
set -euo pipefail

B="$HOME/.config/Cursor-fix-backups/20260326T102230"
C="$HOME/.config/Cursor"
W="527b7dd64a62b71506a6d7272c4544ec"

pkill -f '/usr/share/cursor/cursor' || true
sleep 1

rm -rf "$C/Session Storage"
rm -rf "$C/Local Storage"
mkdir -p "$C/User/workspaceStorage"
rm -rf "$C/User/workspaceStorage/$W"

cp -a "$B/workspaceStorage/$W" "$C/User/workspaceStorage/"
cp -a "$B/Session Storage" "$C/"
cp -a "$B/Local Storage" "$C/"

DISPLAY=:1 \
XAUTHORITY=/run/user/1001/gdm/Xauthority \
DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/1001/bus \
/usr/share/cursor/cursor --disable-gpu --new-window /home/chenxinyao/code/simulation-platform-tea \
  >/tmp/cursor-tea.log 2>&1 &

echo "tea session restore launched"
echo "log: /tmp/cursor-tea.log"
