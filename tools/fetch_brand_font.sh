#!/data/data/com.termux/files/usr/bin/bash
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$ROOT/app/src/main/res/font"
curl -L --fail --retry 3 \
  https://raw.githubusercontent.com/google/fonts/main/ofl/fredoka/static/Fredoka-SemiBold.ttf \
  -o "$ROOT/app/src/main/res/font/fredoka_semibold.ttf"
test -s "$ROOT/app/src/main/res/font/fredoka_semibold.ttf"
echo "Fredoka SemiBold downloaded."
