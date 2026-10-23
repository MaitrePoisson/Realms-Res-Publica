#!/usr/bin/env bash
# Copie la page de villages générée par le selftest vers .athanor/visuels/ et, si Edge est présent, en tire un PNG.
# Usage : render_visuels.sh <selftest-visuels.html>
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$1"
OUT="$ROOT/.athanor/visuels"
[ -s "$SRC" ] || { echo "VISUELS: page absente ($SRC)"; exit 1; }
mkdir -p "$OUT"
cp "$SRC" "$OUT/index.html"
EDGE="/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
if [ -x "$EDGE" ]; then
  WIN_OUT=$(cd "$OUT" && pwd -W 2>/dev/null || pwd)
  timeout 60 "$EDGE" --headless --disable-gpu --hide-scrollbars --window-size=1000,2600 \
    --screenshot="$WIN_OUT/apercu.png" "file:///$WIN_OUT/index.html" >/dev/null 2>&1
fi
echo "VISUELS: $OUT/index.html$([ -s "$OUT/apercu.png" ] && echo ' + apercu.png')"
