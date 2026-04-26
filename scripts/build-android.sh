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
if [ -f "$APK" ]; then
    log "APK built: $APK ($(du -sh "$APK" | cut -f1))"
else
    printf '[android] ERROR: APK not found at %s\n' "$APK" >&2
    exit 1
fi
