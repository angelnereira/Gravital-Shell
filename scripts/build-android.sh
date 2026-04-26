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

# --- Version info -----------------------------------------------------------
VERSION="$(grep 'versionName' "$REPO_ROOT/app/build.gradle.kts" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+')"
VERSION_CODE="$(grep 'versionCode' "$REPO_ROOT/app/build.gradle.kts" | grep -oE '[0-9]+')"
OUTPUTS_DIR="$REPO_ROOT/outputs"
mkdir -p "$OUTPUTS_DIR"

# Auto-increment build number (independent of versionCode, tracks every build)
BUILD_FILE="$OUTPUTS_DIR/BUILD_NUMBER"
if [ -f "$BUILD_FILE" ]; then
    BUILD_NUM=$(( $(cat "$BUILD_FILE") + 1 ))
else
    BUILD_NUM=1
fi
printf '%d' "$BUILD_NUM" > "$BUILD_FILE"

# --- Copy APKs --------------------------------------------------------------
# Versioned: one file per version, overwritten on each build of same version
VERSIONED="$OUTPUTS_DIR/gravital-shell-${VERSION}-debug.apk"
# Latest: always points to the most recent build, overwritten unconditionally
LATEST="$OUTPUTS_DIR/gravital-shell-latest-debug.apk"

cp "$APK" "$VERSIONED"
cp "$APK" "$LATEST"
SIZE="$(du -sh "$VERSIONED" | cut -f1)"

log "APK built:"
log "  versioned : $VERSIONED ($SIZE)"
log "  latest    : $LATEST"

# --- Release index ----------------------------------------------------------
RELEASES="$OUTPUTS_DIR/RELEASES.md"
DATE="$(date -u '+%Y-%m-%d %H:%M UTC')"
GIT_HASH="$(git -C "$REPO_ROOT" rev-parse --short HEAD 2>/dev/null || printf 'n/a')"
GIT_MSG="$(git -C "$REPO_ROOT" log -1 --pretty=format:'%s' 2>/dev/null || printf '')"

if [ ! -f "$RELEASES" ]; then
    cat > "$RELEASES" <<'HDR'
# Gravital Shell — Release Index

Each row represents one build. The **versioned** APK is overwritten when the
version number stays the same; `gravital-shell-latest-debug.apk` always
contains the most recent build.

| Build | Version | versionCode | Date (UTC) | Commit | Message | Size |
|------:|---------|-------------|------------|--------|---------|------|
HDR
fi

printf '| %d | %s | %s | %s | %s | %s | %s |\n' \
    "$BUILD_NUM" "$VERSION" "$VERSION_CODE" "$DATE" "$GIT_HASH" "$GIT_MSG" "$SIZE" \
    >> "$RELEASES"

log "Release index updated: build #${BUILD_NUM} — v${VERSION} (versionCode ${VERSION_CODE})"
