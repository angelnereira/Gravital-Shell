use crate::session::{Distro, Session, SessionPolicy, SessionState};
use anyhow::{Context, Result};
use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::time::{SystemTime, UNIX_EPOCH};
use uuid::Uuid;

pub struct SessionManager {
    base_dir: PathBuf,
    sessions: HashMap<Uuid, Session>,
}

impl SessionManager {
    pub fn new(base_dir: PathBuf) -> Self {
        Self {
            base_dir,
            sessions: HashMap::new(),
        }
    }

    pub fn load_all(&mut self) -> Result<()> {
        let sessions_dir = self.sessions_dir();
        if !sessions_dir.exists() {
            std::fs::create_dir_all(&sessions_dir)?;
            return Ok(());
        }
        for entry in std::fs::read_dir(&sessions_dir)? {
            let entry = entry?;
            let meta = entry.path().join("meta.json");
            if meta.exists() {
                match self.load_session_from_meta(&meta) {
                    Ok(session) => {
                        self.sessions.insert(session.id, session);
                    }
                    Err(e) => {
                        log::warn!("failed to load session from {:?}: {}", meta, e);
                    }
                }
            }
        }
        Ok(())
    }

    fn load_session_from_meta(&self, meta_path: &Path) -> Result<Session> {
        let data = std::fs::read_to_string(meta_path)?;
        let mut session: Session = serde_json::from_str(&data)?;
        session.state = SessionState::Stopped;
        session.pty_master = None;
        session.child_pid = None;
        Ok(session)
    }

    pub fn create(
        &mut self,
        name: String,
        policy: SessionPolicy,
        distro: Distro,
    ) -> Result<Session> {
        let id = Uuid::new_v4();
        let root = self.sessions_dir().join(id.to_string());
        std::fs::create_dir_all(&root)?;

        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs() as i64;

        let session = Session {
            id,
            name,
            root: root.clone(),
            policy,
            state: SessionState::Stopped,
            distro,
            created_at: now,
            tags: Vec::new(),
            pty_master: None,
            child_pid: None,
        };

        self.persist_session(&session)?;
        self.sessions.insert(id, session.clone());
        Ok(session)
    }

    pub fn get(&self, id: &Uuid) -> Option<&Session> {
        self.sessions.get(id)
    }

    pub fn get_mut(&mut self, id: &Uuid) -> Option<&mut Session> {
        self.sessions.get_mut(id)
    }

    pub fn list(&self) -> Vec<&Session> {
        self.sessions.values().collect()
    }

    pub fn set_running(&mut self, id: &Uuid, pty_master: i32, child_pid: u32) -> Result<()> {
        let session = self
            .sessions
            .get_mut(id)
            .context("session not found")?;
        session.state = SessionState::Running;
        session.pty_master = Some(pty_master);
        session.child_pid = Some(child_pid);
        let snapshot = session.clone();
        self.persist_session(&snapshot)?;
        Ok(())
    }

    pub fn set_stopped(&mut self, id: &Uuid) -> Result<()> {
        let session = self
            .sessions
            .get_mut(id)
            .context("session not found")?;
        session.state = SessionState::Stopped;
        session.pty_master = None;
        session.child_pid = None;
        let snapshot = session.clone();
        self.persist_session(&snapshot)?;
        Ok(())
    }

    pub fn destroy(&mut self, id: &Uuid) -> Result<()> {
        if let Some(session) = self.sessions.remove(id) {
            if session.root.exists() {
                std::fs::remove_dir_all(&session.root)
                    .with_context(|| format!("failed to remove {:?}", session.root))?;
            }
        }
        Ok(())
    }

    pub fn proot_args_for(&self, id: &Uuid, files_dir: &str) -> Result<Vec<String>> {
        let session = self.sessions.get(id).context("session not found")?;
        let proot_bin = format!("{}/proot", files_dir);
        let rootfs = session.rootfs_path();
        let rootfs_str = rootfs
            .to_str()
            .context("rootfs path is not valid UTF-8")?;
        Ok(crate::pty::build_proot_argv_strings(&proot_bin, rootfs_str, files_dir))
    }

    pub fn copy_rootfs_from_template(&self, id: &Uuid, template_dir: &Path) -> Result<()> {
        let session = self.sessions.get(id).context("session not found")?;
        let dest = session.rootfs_path();
        if dest.exists() {
            return Ok(());
        }
        std::fs::create_dir_all(&dest)?;
        let src = format!("{}/.", template_dir.to_str().unwrap());
        let status = std::process::Command::new("cp")
            .args(["-a", "--", src.as_str(), dest.to_str().unwrap()])
            .status()
            .context("cp command failed")?;
        if !status.success() {
            anyhow::bail!("cp -a returned non-zero exit code");
        }
        Ok(())
    }

    pub fn import_file(&self, id: &Uuid, src_path: &Path, dest_rel: &str) -> Result<()> {
        let session = self.sessions.get(id).context("session not found")?;
        let dest = session.rootfs_path().join(dest_rel.trim_start_matches('/'));
        if let Some(parent) = dest.parent() {
            std::fs::create_dir_all(parent)?;
        }
        std::fs::copy(src_path, &dest)
            .with_context(|| format!("copy {:?} -> {:?}", src_path, dest))?;
        Ok(())
    }

    pub fn export_file(&self, id: &Uuid, src_rel: &str, dest_path: &Path) -> Result<()> {
        let session = self.sessions.get(id).context("session not found")?;
        let src = session.rootfs_path().join(src_rel.trim_start_matches('/'));
        if let Some(parent) = dest_path.parent() {
            std::fs::create_dir_all(parent)?;
        }
        std::fs::copy(&src, dest_path)
            .with_context(|| format!("copy {:?} -> {:?}", src, dest_path))?;
        Ok(())
    }

    pub fn list_session_files(&self, id: &Uuid, rel_path: &str) -> Result<Vec<String>> {
        let session = self.sessions.get(id).context("session not found")?;
        let dir = session.rootfs_path().join(rel_path.trim_start_matches('/'));
        let mut entries = Vec::new();
        if dir.is_dir() {
            for e in std::fs::read_dir(&dir)? {
                if let Ok(e) = e {
                    let name = e.file_name().to_string_lossy().into_owned();
                    let suffix = if e.path().is_dir() { "/" } else { "" };
                    entries.push(format!("{}{}", name, suffix));
                }
            }
        }
        entries.sort();
        Ok(entries)
    }

    fn persist_session(&self, session: &Session) -> Result<()> {
        let json = serde_json::to_string_pretty(session)?;
        std::fs::write(session.meta_path(), json)?;
        Ok(())
    }

    fn sessions_dir(&self) -> PathBuf {
        self.base_dir.join("sessions")
    }
}
