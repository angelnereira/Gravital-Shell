use anyhow::Result;
use nix::libc::{self, winsize};
use std::ffi::CString;
use std::os::unix::io::RawFd;

pub fn build_proot_argv(
    proot_bin: &str,
    rootfs_dir: &str,
    files_dir: &str,
) -> Vec<CString> {
    let resolv_bind = format!("--bind={}/resolv.conf:/etc/resolv.conf", files_dir);
    vec![
        CString::new(proot_bin).unwrap(),
        CString::new("--rootfs").unwrap(),
        CString::new(rootfs_dir).unwrap(),
        CString::new("--bind=/dev").unwrap(),
        CString::new("--bind=/proc").unwrap(),
        CString::new("--bind=/sys").unwrap(),
        CString::new(resolv_bind).unwrap(),
        CString::new("--bind=/dev/urandom:/dev/random").unwrap(),
        CString::new("--change-id=0:0").unwrap(),
        CString::new("--kill-on-exit").unwrap(),
        CString::new("--pwd=/root").unwrap(),
        CString::new("/bin/sh").unwrap(),
        CString::new("-c").unwrap(),
        CString::new(
            "export TERM=xterm-256color && source /etc/profile 2>/dev/null; \
             [ -f /root/.init.sh ] && sh /root/.init.sh && rm -f /root/.init.sh; \
             exec /bin/ash",
        )
        .unwrap(),
    ]
}

pub fn build_proot_argv_strings(
    proot_bin: &str,
    rootfs_dir: &str,
    files_dir: &str,
) -> Vec<String> {
    build_proot_argv(proot_bin, rootfs_dir, files_dir)
        .into_iter()
        .map(|s| s.into_string().unwrap_or_default())
        .collect()
}

pub fn resize_pty(master_fd: RawFd, rows: u16, cols: u16) -> Result<()> {
    let ws = winsize {
        ws_row: rows,
        ws_col: cols,
        ws_xpixel: 0,
        ws_ypixel: 0,
    };
    let ret = unsafe { libc::ioctl(master_fd, libc::TIOCSWINSZ, &ws) };
    if ret != 0 {
        return Err(anyhow::anyhow!(
            "TIOCSWINSZ failed: {}",
            std::io::Error::last_os_error()
        ));
    }
    Ok(())
}
