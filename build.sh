#!/usr/bin/env bash
# PocketHub build script
#   ./build.sh          → build release APK, bump version, commit to repo
#   ./build.sh --no-bump → build without incrementing version
#
# Requirements: ANDROID_HOME set, gradlew in project root.
# The script reads/writes version.properties for auto-increment.

set -euo pipefail
cd "$(dirname "$0")"

VERSION_FILE="version.properties"
APK_DIR="app/build/outputs/apk/release"
APK_ARCHIVE="apk"
APK_NAME="pockethub-release.apk"

# ── Bump version ─────────────────────────────────────────
BUMP=true
if [ "${1:-}" = "--no-bump" ]; then BUMP=false; fi

if [ "$BUMP" = true ] && [ -f "$VERSION_FILE" ]; then
    CODE=$(grep '^versionCode=' "$VERSION_FILE" | cut -d= -f2)
    NAME=$(grep '^versionName=' "$VERSION_FILE" | cut -d= -f2)
    NEW_CODE=$((CODE + 1))
    # Bump patch version: 0.1.0 → 0.1.1 → 0.1.2 ...
    IFS='.' read -r MAJOR MINOR PATCH <<< "$NAME"
    NEW_NAME="$MAJOR.$MINOR.$((PATCH + 1))"
    cat > "$VERSION_FILE" << EOF
# Auto-managed by build.sh — do not edit manually
versionCode=$NEW_CODE
versionName=$NEW_NAME
EOF
    echo "✦ Version: $CODE → $NEW_CODE ($NAME → $NEW_NAME)"
fi

# ── Build ────────────────────────────────────────────────
echo "✦ Building release APK..."
./gradlew clean :app:assembleRelease --no-daemon

# ── Archive ──────────────────────────────────────────────
mkdir -p "$APK_ARCHIVE"
cp "$APK_DIR/app-release.apk" "$APK_ARCHIVE/$APK_NAME"
cp "$APK_DIR/app-release.apk" "$APK_ARCHIVE/pockethub-$(grep '^versionName=' "$VERSION_FILE" | cut -d= -f2).apk"
echo "✦ APK archived to $APK_ARCHIVE/$APK_NAME"

# ── Show version ──────────────────────────────────────────
CODE=$(grep '^versionCode=' "$VERSION_FILE" | cut -d= -f2)
NAME=$(grep '^versionName=' "$VERSION_FILE" | cut -d= -f2)
echo ""
echo "✓ Build complete: v$NAME (code $CODE)"
echo "  → $APK_ARCHIVE/$APK_NAME"
ls -lh "$APK_ARCHIVE/$APK_NAME"
