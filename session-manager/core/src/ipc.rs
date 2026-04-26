use anyhow::Result;
use std::path::PathBuf;

pub struct IpcServer {
    socket_path: PathBuf,
}

impl IpcServer {
    pub fn new(files_dir: &str) -> Self {
        Self {
            socket_path: PathBuf::from(files_dir).join("gravital.sock"),
        }
    }

    pub fn start(&self) -> Result<()> {
        if self.socket_path.exists() {
            std::fs::remove_file(&self.socket_path).ok();
        }
        log::info!("IPC server socket: {:?}", self.socket_path);
        Ok(())
    }

    pub fn stop(&self) {
        if self.socket_path.exists() {
            std::fs::remove_file(&self.socket_path).ok();
        }
    }
}
