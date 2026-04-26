#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

log() { printf '[build-all] %s\n' "$*"; }

log "=== Gravital Shell — Full Build ==="

log "Step 1/4: Environment check..."
"$REPO_ROOT/scripts/setup-env.sh"

log "Step 2/4: Downloading assets..."
"$REPO_ROOT/scripts/download-assets.sh"

log "Step 3/4: Building Rust native libraries..."
"$REPO_ROOT/scripts/build-rust.sh"

log "Step 4/4: Building Android APK..."
"$REPO_ROOT/scripts/build-android.sh"

log "=== Build complete ==="
log "APK: $REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"
