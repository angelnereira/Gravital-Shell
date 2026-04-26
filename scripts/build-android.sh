#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$REPO_ROOT/scripts/.env"

log() { printf '[android] %s\n' "$*"; }

if [ -f "$ENV_FILE" ]; then
    source "$ENV_FILE"
fi

if [ -n "${ANDROID_HOME:-}" ] && [ ! -f "$REPO_ROOT/local.properties" ]; then
    echo "sdk.dir=$ANDROID_HOME" > "$REPO_ROOT/local.properties"
    log "Created local.properties with sdk.dir=$ANDROID_HOME"
fi

log "Building Android APK (debug)..."
cd "$REPO_ROOT"
./gradlew assembleDebug

APK="$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK" ]; then
    printf '[android] ERROR: APK not found at %s\n' "$APK" >&2
    exit 1
fi

VERSION="$(grep 'versionName' "$REPO_ROOT/app/build.gradle.kts" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+')"
OUTPUTS_DIR="$REPO_ROOT/outputs"
mkdir -p "$OUTPUTS_DIR"
DEST="$OUTPUTS_DIR/gravital-shell-${VERSION}-debug.apk"

cp "$APK" "$DEST"
log "APK built: $DEST ($(du -sh "$DEST" | cut -f1))"
