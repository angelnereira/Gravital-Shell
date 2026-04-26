#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ASSETS_DIR="$REPO_ROOT/assets"
APP_ASSETS_DIR="$REPO_ROOT/app/src/main/assets"
JNILIBS_ARM64="$REPO_ROOT/app/src/main/jniLibs/arm64-v8a"
JNILIBS_X86_64="$REPO_ROOT/app/src/main/jniLibs/x86_64"

log() { printf '[assets] %s\n' "$*"; }
fail() { printf '[assets] ERROR: %s\n' "$*" >&2; exit 1; }

mkdir -p "$ASSETS_DIR" "$APP_ASSETS_DIR" "$JNILIBS_ARM64" "$JNILIBS_X86_64"

download_deb_binary() {
    local url="$1" dest_dir="$2" bin_path_in_deb="$3" out_name="$4"
    local tmp_deb tmp_dir
    tmp_deb="$(mktemp /tmp/pkg_XXXXXX.deb)"
    tmp_dir="$(mktemp -d /tmp/pkg_extract_XXXXXX)"

    if ! wget -q --timeout=60 -O "$tmp_deb" "$url"; then
        rm -rf "$tmp_deb" "$tmp_dir"
        return 1
    fi
    (cd "$tmp_dir" && ar x "$tmp_deb" && tar -xJf data.tar.xz) 2>/dev/null

    local src="$tmp_dir/data/data/com.termux/files/$bin_path_in_deb"
    if [ ! -f "$src" ]; then
        rm -rf "$tmp_deb" "$tmp_dir"
        return 1
    fi

    cp "$src" "$dest_dir/$out_name"
    chmod +x "$dest_dir/$out_name"
    rm -rf "$tmp_deb" "$tmp_dir"
    return 0
}

download_proot() {
    local arm64_dest="$APP_ASSETS_DIR/proot-arm64"
    local x86_dest="$APP_ASSETS_DIR/proot-x86_64"

    if [ -f "$arm64_dest" ] && [ -f "$x86_dest" ]; then
        log "proot binaries already present, skipping."
        return 0
    fi

    if [ ! -f "$arm64_dest" ]; then
        log "Downloading proot ARM64..."
        local url="https://packages.termux.dev/apt/termux-main/pool/main/p/proot/proot_5.1.107-70_aarch64.deb"
        if download_deb_binary "$url" "$APP_ASSETS_DIR" "usr/bin/proot" "proot-arm64"; then
            cp "$arm64_dest" "$ASSETS_DIR/proot-arm64"
            chmod +x "$ASSETS_DIR/proot-arm64"
            log "proot-arm64 installed ($(du -sh "$arm64_dest" | cut -f1))"
        else
            fail "Failed to download proot ARM64 from $url"
        fi
    fi

    if [ ! -f "$x86_dest" ]; then
        log "Downloading proot x86_64..."
        local url="https://packages.termux.dev/apt/termux-main/pool/main/p/proot/proot_5.1.107-70_x86_64.deb"
        if download_deb_binary "$url" "$APP_ASSETS_DIR" "usr/bin/proot" "proot-x86_64"; then
            cp "$x86_dest" "$ASSETS_DIR/proot-x86_64"
            chmod +x "$ASSETS_DIR/proot-x86_64"
            log "proot-x86_64 installed ($(du -sh "$x86_dest" | cut -f1))"
        else
            fail "Failed to download proot x86_64 from $url"
        fi
    fi
}

download_libtalloc() {
    local arm64_dest="$JNILIBS_ARM64/libtalloc.so.2"
    local x86_dest="$JNILIBS_X86_64/libtalloc.so.2"

    if [ -f "$arm64_dest" ] && [ -f "$x86_dest" ]; then
        log "libtalloc libraries already present, skipping."
        return 0
    fi

    if [ ! -f "$arm64_dest" ]; then
        log "Downloading libtalloc ARM64..."
        local url="https://packages.termux.dev/apt/termux-main/pool/main/libt/libtalloc/libtalloc_2.4.3_aarch64.deb"
        if download_deb_binary "$url" "$JNILIBS_ARM64" "usr/lib/libtalloc.so.2" "libtalloc.so.2"; then
            log "libtalloc arm64 installed ($(du -sh "$arm64_dest" | cut -f1))"
        else
            fail "Failed to download libtalloc ARM64 from $url"
        fi
    fi

    if [ ! -f "$x86_dest" ]; then
        log "Downloading libtalloc x86_64..."
        local url="https://packages.termux.dev/apt/termux-main/pool/main/libt/libtalloc/libtalloc_2.4.3_x86_64.deb"
        if download_deb_binary "$url" "$JNILIBS_X86_64" "usr/lib/libtalloc.so.2" "libtalloc.so.2"; then
            log "libtalloc x86_64 installed ($(du -sh "$x86_dest" | cut -f1))"
        else
            fail "Failed to download libtalloc x86_64 from $url"
        fi
    fi
}

download_ubuntu() {
    local tarball="$ASSETS_DIR/ubuntu-base.tar.gz"
    local app_tarball="$APP_ASSETS_DIR/ubuntu-base.tar.gz"

    if [ -f "$app_tarball" ]; then
        log "Ubuntu base tarball already present, skipping."
        return 0
    fi
    if [ -f "$tarball" ]; then
        log "Ubuntu tarball already in assets/, copying to app assets..."
        cp "$tarball" "$app_tarball"
        return 0
    fi

    log "Downloading Ubuntu 22.04 LTS ARM64 base rootfs..."
    local url="https://cdimage.ubuntu.com/ubuntu-base/releases/22.04/release/ubuntu-base-22.04.5-base-arm64.tar.gz"
    if ! wget -q --timeout=120 --show-progress -O "$tarball" "$url"; then
        fail "Failed to download Ubuntu base from $url"
    fi

    log "Ubuntu base downloaded ($(du -sh "$tarball" | cut -f1))"
    cp "$tarball" "$app_tarball"
    log "Ubuntu tarball copied to app assets"
}

main() {
    log "Downloading required assets..."
    download_proot
    download_libtalloc
    download_ubuntu
    log "Assets ready."
}

main "$@"
