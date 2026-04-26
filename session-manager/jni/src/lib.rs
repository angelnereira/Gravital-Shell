use gravitalshell_core::manager::SessionManager;
use gravitalshell_core::session::{Distro, SessionPolicy};
use jni::objects::{JClass, JString};
use jni::sys::{jint, jstring};
use jni::JNIEnv;
use once_cell::sync::OnceCell;
use std::path::PathBuf;
use std::sync::Mutex;
use uuid::Uuid;

static MANAGER: OnceCell<Mutex<SessionManager>> = OnceCell::new();
static FILES_DIR: OnceCell<String> = OnceCell::new();

fn get_manager() -> &'static Mutex<SessionManager> {
    MANAGER.get().expect("SessionManager not initialized")
}

fn files_dir() -> &'static str {
    FILES_DIR.get().map(|s| s.as_str()).unwrap_or("")
}

#[no_mangle]
pub extern "C" fn Java_sh_gravital_shell_bridge_GravitalShellBridge_initialize(
    mut env: JNIEnv,
    _class: JClass,
    files_dir: JString,
) {
    android_logger::init_once(
        android_logger::Config::default()
            .with_max_level(log::LevelFilter::Debug)
            .with_tag("GravitalShell"),
    );

    let dir: String = env.get_string(&files_dir).unwrap().into();

    let _ = FILES_DIR.set(dir.clone());

    let resolv = PathBuf::from(&dir).join("resolv.conf");
    if !resolv.exists() {
        let _ = std::fs::write(&resolv, "nameserver 8.8.8.8\nnameserver 1.1.1.1\n");
    }

    let mut mgr = SessionManager::new(PathBuf::from(&dir));
    let _ = mgr.load_all();

    let _ = MANAGER.set(Mutex::new(mgr));
    log::info!("SessionManager initialized at {}", dir);
}

#[no_mangle]
pub extern "C" fn Java_sh_gravital_shell_bridge_GravitalShellBridge_createSession(
    mut env: JNIEnv,
    _class: JClass,
    name: JString,
    policy: JString,
) -> jstring {
    let name: String = env.get_string(&name).unwrap().into();
    let policy_str: String = env.get_string(&policy).unwrap().into();

    let policy = match policy_str.as_str() {
        "Persistent" => SessionPolicy::Persistent,
        "Snapshot" => SessionPolicy::Snapshot,
        _ => SessionPolicy::Ephemeral,
    };

    let mut mgr = get_manager().lock().unwrap();
    match mgr.create(name, policy, Distro::Ubuntu) {
        Ok(session) => {
            let id = session.id.to_string();
            env.new_string(id).unwrap().into_raw()
        }
        Err(e) => {
            log::error!("createSession failed: {}", e);
            env.new_string("").unwrap().into_raw()
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_sh_gravital_shell_bridge_GravitalShellBridge_startSession(
    mut env: JNIEnv,
    _class: JClass,
    session_id: JString,
) -> jstring {
    let id_str: String = env.get_string(&session_id).unwrap().into();
    let id = match Uuid::parse_str(&id_str) {
        Ok(id) => id,
        Err(e) => {
            log::error!("invalid session id {}: {}", id_str, e);
            return env.new_string("[]").unwrap().into_raw();
        }
    };

    let dir = files_dir().to_string();
    let template = PathBuf::from(&dir).join("ubuntu-template");

    let mgr = get_manager().lock().unwrap();

    if let Err(e) = mgr.copy_rootfs_from_template(&id, &template) {
        log::error!("failed to copy rootfs for {}: {}", id_str, e);
        return env.new_string("[]").unwrap().into_raw();
    }

    match mgr.proot_args_for(&id, &dir) {
        Ok(args) => {
            let json = serde_json::to_string(&args).unwrap_or_else(|_| "[]".to_string());
            env.new_string(json).unwrap().into_raw()
        }
        Err(e) => {
            log::error!("proot_args_for {} failed: {}", id_str, e);
            env.new_string("[]").unwrap().into_raw()
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_sh_gravital_shell_bridge_GravitalShellBridge_stopSession(
    mut env: JNIEnv,
    _class: JClass,
    session_id: JString,
) {
    let id_str: String = env.get_string(&session_id).unwrap().into();
    if let Ok(id) = Uuid::parse_str(&id_str) {
        let mut mgr = get_manager().lock().unwrap();
        if let Some(session) = mgr.get(&id) {
            if let Some(pid) = session.child_pid {
                unsafe {
                    libc::kill(pid as libc::c_int, libc::SIGTERM);
                }
            }
        }
        let _ = mgr.set_stopped(&id);
    }
}

#[no_mangle]
pub extern "C" fn Java_sh_gravital_shell_bridge_GravitalShellBridge_destroySession(
    mut env: JNIEnv,
    _class: JClass,
    session_id: JString,
) {
    let id_str: String = env.get_string(&session_id).unwrap().into();
    if let Ok(id) = Uuid::parse_str(&id_str) {
        let mut mgr = get_manager().lock().unwrap();
        if let Err(e) = mgr.destroy(&id) {
            log::error!("destroySession {} failed: {}", id_str, e);
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_sh_gravital_shell_bridge_GravitalShellBridge_listSessions(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let mgr = get_manager().lock().unwrap();
    let sessions: Vec<_> = mgr.list().into_iter().collect();
    let json = serde_json::to_string(&sessions).unwrap_or_else(|_| "[]".to_string());
    env.new_string(json).unwrap().into_raw()
}

#[no_mangle]
pub extern "C" fn Java_sh_gravital_shell_bridge_GravitalShellBridge_resizePty(
    _env: JNIEnv,
    _class: JClass,
    fd: jint,
    rows: jint,
    cols: jint,
) {
    if let Err(e) = gravitalshell_core::pty::resize_pty(fd, rows as u16, cols as u16) {
        log::warn!("resizePty failed: {}", e);
    }
}

#[no_mangle]
pub extern "C" fn Java_sh_gravital_shell_bridge_GravitalShellBridge_exportSession(
    mut env: JNIEnv,
    _class: JClass,
    session_id: JString,
    dest_path: JString,
) {
    let id_str: String = env.get_string(&session_id).unwrap().into();
    let dest: String = env.get_string(&dest_path).unwrap().into();

    if let Ok(id) = Uuid::parse_str(&id_str) {
        let mgr = get_manager().lock().unwrap();
        if let Some(session) = mgr.get(&id) {
            let root = session.root.clone();
            drop(mgr);
            if let Err(e) = gravitalshell_core::snapshot::export(&root, std::path::Path::new(&dest))
            {
                log::error!("exportSession failed: {}", e);
            }
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_sh_gravital_shell_bridge_GravitalShellBridge_importSession(
    mut env: JNIEnv,
    _class: JClass,
    src_path: JString,
) -> jstring {
    let src: String = env.get_string(&src_path).unwrap().into();
    let dir = files_dir().to_string();

    let new_id = uuid::Uuid::new_v4();
    let dest = PathBuf::from(&dir)
        .join("sessions")
        .join(new_id.to_string());

    match gravitalshell_core::snapshot::import(std::path::Path::new(&src), &dest) {
        Ok(_) => {
            let mut mgr = get_manager().lock().unwrap();
            let meta = dest.join("meta.json");
            if meta.exists() {
                let _ = mgr.load_all();
            }
            env.new_string(new_id.to_string()).unwrap().into_raw()
        }
        Err(e) => {
            log::error!("importSession failed: {}", e);
            env.new_string("").unwrap().into_raw()
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_sh_gravital_shell_bridge_GravitalShellBridge_importFileToSession(
    mut env: JNIEnv,
    _class: JClass,
    session_id: JString,
    src_path: JString,
    dest_rel: JString,
) -> jint {
    let id_str: String = env.get_string(&session_id).unwrap().into();
    let src: String = env.get_string(&src_path).unwrap().into();
    let rel: String = env.get_string(&dest_rel).unwrap().into();

    if let Ok(id) = Uuid::parse_str(&id_str) {
        let mgr = get_manager().lock().unwrap();
        match mgr.import_file(&id, std::path::Path::new(&src), &rel) {
            Ok(_) => 0,
            Err(e) => {
                log::error!("importFileToSession failed: {}", e);
                -1
            }
        }
    } else {
        -1
    }
}

#[no_mangle]
pub extern "C" fn Java_sh_gravital_shell_bridge_GravitalShellBridge_exportFileFromSession(
    mut env: JNIEnv,
    _class: JClass,
    session_id: JString,
    src_rel: JString,
    dest_path: JString,
) -> jint {
    let id_str: String = env.get_string(&session_id).unwrap().into();
    let rel: String = env.get_string(&src_rel).unwrap().into();
    let dest: String = env.get_string(&dest_path).unwrap().into();

    if let Ok(id) = Uuid::parse_str(&id_str) {
        let mgr = get_manager().lock().unwrap();
        match mgr.export_file(&id, &rel, std::path::Path::new(&dest)) {
            Ok(_) => 0,
            Err(e) => {
                log::error!("exportFileFromSession failed: {}", e);
                -1
            }
        }
    } else {
        -1
    }
}

#[no_mangle]
pub extern "C" fn Java_sh_gravital_shell_bridge_GravitalShellBridge_listSessionFiles(
    mut env: JNIEnv,
    _class: JClass,
    session_id: JString,
    rel_path: JString,
) -> jstring {
    let id_str: String = env.get_string(&session_id).unwrap().into();
    let rel: String = env.get_string(&rel_path).unwrap().into();

    if let Ok(id) = Uuid::parse_str(&id_str) {
        let mgr = get_manager().lock().unwrap();
        match mgr.list_session_files(&id, &rel) {
            Ok(entries) => {
                let json = serde_json::to_string(&entries).unwrap_or_else(|_| "[]".into());
                env.new_string(json).unwrap().into_raw()
            }
            Err(e) => {
                log::error!("listSessionFiles failed: {}", e);
                env.new_string("[]").unwrap().into_raw()
            }
        }
    } else {
        env.new_string("[]").unwrap().into_raw()
    }
}

mod libc {
    pub use std::os::raw::c_int;
    pub const SIGTERM: c_int = 15;

    extern "C" {
        pub fn kill(pid: c_int, sig: c_int) -> c_int;
    }
}
