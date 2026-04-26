use serde::{Deserialize, Serialize};
use std::path::PathBuf;
use uuid::Uuid;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum SessionPolicy {
    Ephemeral,
    Persistent,
    Snapshot,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum SessionState {
    Stopped,
    Running,
    Suspended,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum Distro {
    Ubuntu,
    Debian,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Session {
    pub id: Uuid,
    pub name: String,
    pub root: PathBuf,
    pub policy: SessionPolicy,
    pub state: SessionState,
    pub distro: Distro,
    pub created_at: i64,
    pub tags: Vec<String>,
    pub pty_master: Option<i32>,
    pub child_pid: Option<u32>,
}

impl Session {
    pub fn rootfs_path(&self) -> PathBuf {
        self.root.join("rootfs")
    }

    pub fn home_path(&self) -> PathBuf {
        self.root.join("home")
    }

    pub fn meta_path(&self) -> PathBuf {
        self.root.join("meta.json")
    }

    pub fn tmp_path(&self) -> PathBuf {
        self.root.join("tmp")
    }
}
