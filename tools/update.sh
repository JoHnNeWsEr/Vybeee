#!/data/data/com.termux/files/usr/bin/bash
set -e

PROJECT_DIR="/sdcard/Vybeee/project/Vybeee-1.0.0"
ZIP="${1:-/sdcard/Download/Vybeee-1.23.0-FAVORITES-SEARCH.zip}"

if [ ! -f "$ZIP" ]; then
  echo "ZIP not found: $ZIP"
  exit 1
fi

if [ ! -d "$PROJECT_DIR/.git" ]; then
  echo "Git repository not found: $PROJECT_DIR"
  exit 1
fi

cd "$PROJECT_DIR"

BACKUP="/sdcard/Vybeee/project/Vybeee-1.0.0-backup-$(date +%Y%m%d-%H%M%S)"
echo "==> Creating backup..."
cp -a "$PROJECT_DIR" "$BACKUP"
rm -rf "$BACKUP/.git"

echo "==> Preserving Git repository..."
TMP="$(mktemp -d)"
cp -a .git "$TMP/.git"

trap 'rm -rf "$TMP"' EXIT

echo "==> Replacing project files..."
find . -mindepth 1 -maxdepth 1 ! -name '.git' -exec rm -rf {} +
unzip -q "$ZIP" -d "$TMP/extracted"
INNER="$TMP/extracted/Vybeee-1.0.0"

if [ ! -d "$INNER" ]; then
  echo "Unexpected ZIP structure. Expected Vybeee-1.0.0/"
  exit 1
fi

cp -a "$INNER"/. .

VERSION="$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' app/build.gradle.kts | head -n 1)"
echo "==> Vybeee version: ${VERSION:-unknown}"
echo "==> Checking Git status..."
git status --short

git add .
if git diff --cached --quiet; then
  echo "No changes to commit."
  exit 0
fi

git commit -m "Update Vybeee ${VERSION:-library features}"
echo "==> Pushing to GitHub..."
git push

echo
echo "======================================"
echo "Vybeee ${VERSION:-update} pushed successfully."
echo "======================================"
echo
echo "Backup:"
echo "$BACKUP"
echo "GitHub Actions should now build the APK."
