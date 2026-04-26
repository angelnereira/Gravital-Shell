#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$REPO_ROOT/scripts/.env"

log() { printf '[rust] %s\n' "$*"; }

if [ -f "$ENV_FILE" ]; then
    source "$ENV_FILE"
fi

if [ -n "${ANDROID_NDK_HOME:-}" ]; then
    NDK_TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
    if [ -d "$NDK_TOOLCHAIN" ]; then
        export PATH="$NDK_TOOLCHAIN:$PATH"
        log "NDK toolchain: $NDK_TOOLCHAIN"
    fi
fi

log "Building Rust native libraries..."
cd "$REPO_ROOT/session-manager"

cargo ndk \
    -t aarch64-linux-android \
    -t x86_64-linux-android \
    -o "../app/src/main/jniLibs" \
    build --release -p gravitalshell-jni

log "Build complete."
for abi in arm64-v8a x86_64; do
    so="$REPO_ROOT/app/src/main/jniLibs/$abi/libgravitalshell.so"
    if [ -f "$so" ]; then
        log "  $abi: $(du -sh "$so" | cut -f1)"
    else
        printf '[rust] WARNING: %s not found\n' "$so" >&2
    fi
done
