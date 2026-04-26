#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ASSETS_DIR="$REPO_ROOT/assets"
APP_ASSETS_DIR="$REPO_ROOT/app/src/main/assets"
JNILIBS_ARM64="$REPO_ROOT/app/src/main/jniLibs/arm64-v8a"

log() { printf '[assets] %s\n' "$*"; }
fail() { printf '[assets] ERROR: %s\n' "$*" >&2; exit 1; }

mkdir -p "$ASSETS_DIR" "$APP_ASSETS_DIR" "$JNILIBS_ARM64"

download_proot() {
    local dest="$ASSETS_DIR/proot-arm64"
    local app_dest="$APP_ASSETS_DIR/proot-arm64"

    if [ -f "$app_dest" ]; then
        log "proot-arm64 already present, skipping."
        return 0
    fi

    log "Downloading proot ARM64 from termux packages..."
    local deb_url="https://packages.termux.dev/apt/termux-main/pool/main/p/proot/proot_5.1.107-70_aarch64.deb"
    local tmp_deb
    tmp_deb="$(mktemp /tmp/proot_XXXXXX.deb)"

    if ! wget -q --timeout=60 -O "$tmp_deb" "$deb_url"; then
        fail "Failed to download proot deb from $deb_url"
    fi

    local tmp_dir
    tmp_dir="$(mktemp -d /tmp/proot_extract_XXXXXX)"
    (cd "$tmp_dir" && ar x "$tmp_deb" && tar -xJf data.tar.xz)

    local bin="$tmp_dir/data/data/com.termux/files/usr/bin/proot"
    if [ ! -f "$bin" ]; then
        fail "proot binary not found inside deb package"
    fi

    cp "$bin" "$dest"
    chmod +x "$dest"
    cp "$dest" "$app_dest"
    chmod +x "$app_dest"

    rm -rf "$tmp_deb" "$tmp_dir"
    log "proot-arm64 installed ($(du -sh "$dest" | cut -f1))"
}

download_libtalloc() {
    local dest="$JNILIBS_ARM64/libtalloc.so.2"
    if [ -f "$dest" ]; then
        log "libtalloc.so.2 already present, skipping."
        return 0
    fi

    log "Downloading libtalloc ARM64 from termux packages..."
    local deb_url="https://packages.termux.dev/apt/termux-main/pool/main/libt/libtalloc/libtalloc_2.4.3_aarch64.deb"
    local tmp_deb
    tmp_deb="$(mktemp /tmp/libtalloc_XXXXXX.deb)"

    if ! wget -q --timeout=30 -O "$tmp_deb" "$deb_url"; then
        fail "Failed to download libtalloc deb from $deb_url"
    fi

    local tmp_dir
    tmp_dir="$(mktemp -d /tmp/libtalloc_extract_XXXXXX)"
    (cd "$tmp_dir" && ar x "$tmp_deb" && tar -xJf data.tar.xz)

    local lib="$tmp_dir/data/data/com.termux/files/usr/lib/libtalloc.so.2"
    if [ ! -f "$lib" ]; then
        fail "libtalloc.so.2 not found inside deb package"
    fi

    cp "$lib" "$dest"
    rm -rf "$tmp_deb" "$tmp_dir"
    log "libtalloc.so.2 installed ($(du -sh "$dest" | cut -f1))"
}

download_alpine() {
    local tarball="$ASSETS_DIR/alpine-minirootfs.tar.gz"
    local extract_dir="$ASSETS_DIR/alpine-minirootfs"
    local app_tarball="$APP_ASSETS_DIR/alpine-minirootfs.tar.gz"

    if [ -f "$app_tarball" ]; then
        log "Alpine minirootfs tarball already present, skipping."
        return 0
    fi
    if [ -f "$tarball" ]; then
        log "Alpine tarball already in assets/, copying to app assets..."
        cp "$tarball" "$app_tarball"
        return 0
    fi

    log "Downloading Alpine Linux 3.19.1 ARM64 minirootfs..."
    local url="https://dl-cdn.alpinelinux.org/alpine/v3.19/releases/aarch64/alpine-minirootfs-3.19.1-aarch64.tar.gz"
    if ! wget -q --timeout=60 --show-progress -O "$tarball" "$url"; then
        fail "Failed to download Alpine minirootfs from $url"
    fi

    log "Alpine minirootfs downloaded ($(du -sh "$tarball" | cut -f1))"

    mkdir -p "$extract_dir"
    tar -xzf "$tarball" -C "$extract_dir"
    log "Alpine minirootfs extracted to $extract_dir"

    cp "$tarball" "$app_tarball"
    log "Alpine tarball copied to app assets"
}

main() {
    log "Downloading required assets..."
    download_proot
    download_libtalloc
    download_alpine
    log "Assets ready."
}

main "$@"
