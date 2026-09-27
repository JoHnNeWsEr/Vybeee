#!/data/data/com.termux/files/usr/bin/bash
set -e

PROJECT_DIR="/sdcard/Vybeee/project/Vybeee-1.0.0"
ZIP="${1:-/sdcard/Download/Vybeee-1.1.0.zip}"

if [ ! -f "$ZIP" ]; then
  echo "ZIP not found: $ZIP"
  exit 1
fi

cd "$PROJECT_DIR"

echo "==> Preserving Git repository..."
TMP="$(mktemp -d)"
cp -a .git "$TMP/.git"

find . -mindepth 1 -maxdepth 1 ! -name '.git' -exec rm -rf {} +
unzip -q "$ZIP" -d "$TMP/extracted"
INNER="$TMP/extracted/Vybeee-1.0.0"

if [ ! -d "$INNER" ]; then
  echo "Unexpected ZIP structure. Expected Vybeee-1.0.0/"
  rm -rf "$TMP"
  exit 1
fi

cp -a "$INNER"/. .
rm -rf "$TMP"

echo "==> Configuring Git credential storage (one-time authentication may be requested)..."
git config --global credential.helper store

git add .
if git diff --cached --quiet; then
  echo "No changes to commit."
  exit 0
fi

git commit -m "Prepare Vybeee 1.1.0 stable signing and update workflow"
git push

echo
echo "==> Vybeee update pushed successfully."
