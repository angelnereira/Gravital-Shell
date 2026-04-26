#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$REPO_ROOT/scripts/.env"

log() { printf '[setup] %s\n' "$*"; }
fail() { printf '[setup] ERROR: %s\n' "$*" >&2; exit 1; }

check_java() {
    if ! command -v java &>/dev/null; then
        fail "Java not found. Install JDK 17+ and ensure java is in PATH."
    fi
    local ver
    ver=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)
    if [ "${ver:-0}" -lt 17 ] 2>/dev/null; then
        fail "Java 17+ required. Found version $ver."
    fi
    log "Java OK (version $ver)"
}

check_rust() {
    if ! command -v cargo &>/dev/null; then
        fail "Rust not found. Install via rustup: https://rustup.rs"
    fi
    log "Rust OK ($(cargo --version))"
}

check_rust_targets() {
    local targets
    targets=$(rustup target list --installed 2>/dev/null)
    for target in aarch64-linux-android x86_64-linux-android; do
        if ! echo "$targets" | grep -q "$target"; then
            log "Adding Rust target $target..."
            rustup target add "$target"
        else
            log "Rust target $target OK"
        fi
    done
}

check_cargo_ndk() {
    if ! command -v cargo-ndk &>/dev/null; then
        log "Installing cargo-ndk..."
        cargo install cargo-ndk
    else
        log "cargo-ndk OK ($(cargo ndk --version 2>/dev/null || echo unknown))"
    fi
}

check_android_sdk() {
    if [ -z "${ANDROID_HOME:-}" ]; then
        if [ -d "$HOME/android-sdk" ]; then
            export ANDROID_HOME="$HOME/android-sdk"
        elif [ -d "$HOME/Android/Sdk" ]; then
            export ANDROID_HOME="$HOME/Android/Sdk"
        else
            fail "ANDROID_HOME not set and no SDK found at ~/android-sdk or ~/Android/Sdk."
        fi
    fi
    log "ANDROID_HOME = $ANDROID_HOME"
}

check_ndk() {
    local ndk_version="26.1.10909125"
    local ndk_path="$ANDROID_HOME/ndk/$ndk_version"
    if [ ! -d "$ndk_path" ]; then
        log "NDK $ndk_version not found at $ndk_path"
        log "Install it via Android Studio SDK Manager or sdkmanager:"
        log "  sdkmanager 'ndk;$ndk_version'"
        fail "NDK $ndk_version required."
    fi
    export ANDROID_NDK_HOME="$ndk_path"
    log "NDK OK ($ndk_path)"
}

write_env() {
    cat > "$ENV_FILE" <<EOF
export ANDROID_HOME="$ANDROID_HOME"
export ANDROID_NDK_HOME="$ANDROID_NDK_HOME"
export PATH="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin:\$PATH"
EOF
    log "Environment written to $ENV_FILE"
}

main() {
    log "Checking build environment for Gravital Shell..."
    check_java
    check_rust
    check_rust_targets
    check_cargo_ndk
    check_android_sdk
    check_ndk
    write_env
    log "All checks passed."
}

main "$@"
