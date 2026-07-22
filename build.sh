#!/usr/bin/env bash
# PocketHub build script
#   ./build.sh          → build release APK, bump version, commit to repo
#   ./build.sh --no-bump → build without incrementing version
#
# Requirements: ANDROID_HOME set, gradlew in project root.
# The script reads/writes version.properties for auto-increment.
#
# Archived APK path: apk/pockethub-<versionName>.apk
# Local archive keeps only the newest 2 APKs (pruned below).
#
# The CI workflow (build.yml) also creates a matching GitHub Release (tag vX.Y.Z)
# with that APK as an asset — the app's in-app updater polls GitHub Releases
# to detect and prompt new versions. The CI keeps only the newest 2 Releases/tags
# on GitHub as well (old ones are pruned in build.yml).

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
# Keep only the two most recent versioned APKs; no "release" named APK.
mkdir -p "$APK_ARCHIVE"
NEW_APK="$APK_ARCHIVE/pockethub-$(grep '^versionName=' "$VERSION_FILE" | cut -d= -f2).apk"
cp "$APK_DIR/app-release.apk" "$NEW_APK"
echo "✦ APK archived to $NEW_APK"

# Remove any legacy "release"-named APK
rm -f "$APK_ARCHIVE/$APK_NAME"

# Prune old versioned APKs, keeping only the newest two
# (files matching pockethub-<version>.apk, sorted by version descending)
mapfile -t OLD_APKS < <(ls -1 "$APK_ARCHIVE"/pockethub-*.apk 2>/dev/null | sort -rV)
if [ "${#OLD_APKS[@]}" -gt 2 ]; then
    for apk in "${OLD_APKS[@]:2}"; do
        rm -f "$apk"
        echo "✦ Pruned old APK: $apk"
    done
fi

# ── Show version ──────────────────────────────────────────
CODE=$(grep '^versionCode=' "$VERSION_FILE" | cut -d= -f2)
NAME=$(grep '^versionName=' "$VERSION_FILE" | cut -d= -f2)
echo ""
echo "✓ Build complete: v$NAME (code $CODE)"
echo "  Kept APKs in $APK_ARCHIVE/:"
ls -lh "$APK_ARCHIVE"/pockethub-*.apk 2>/dev/null | awk '{print "  → "$NF}'
