# Gravital Shell
# Contexto maestro para Claude Code (Opus 4+)
# Repo: https://github.com/angelnereira/Gravital-Shell.git

---

## IDENTIDAD DEL PROYECTO

**Nombre:** Gravital Shell
**Package:** sh.gravital.shell
**Organizacion:** Gravital Labs — Nereira Technology and Business Solutions
**Repo:** https://github.com/angelnereira/Gravital-Shell.git
**Plataforma:** Android 10+ (API 29+), sin root
**CPU targets:** arm64-v8a (fisico) + x86_64 (emulador)
**Vision:** Terminal Linux profesional en Android, capaz de ejecutar tareas complejas
igual que Ubuntu Desktop: compilar, administrar servidores, correr pipelines,
automatizar, conectar por SSH, ejecutar procesos en background con control total.

---

## ARQUITECTURA CENTRAL

```
+-----------------------------------------------------+
|  GRAVITAL SHELL -- Capas del sistema                |
+-----------------------------------------------------+
|  UI LAYER          Kotlin + Jetpack Compose         |
|  . SessionListScreen  . TerminalScreen              |
|  . SessionViewModel   . Theme (dark, monospace)     |
+-----------------------------------------------------+
|  JNI BRIDGE        Kotlin <-> Rust                  |
|  . GravitalShellBridge.kt  . libgravitalshell.so    |
+-----------------------------------------------------+
|  SESSION MANAGER   Rust (ARM64 binary via cargo-ndk)|
|  . session.rs    Struct + lifecycle                 |
|  . manager.rs    Create/start/stop/destroy/list     |
|  . pty.rs        Argumentos proot + resize PTY      |
|  . ipc.rs        Unix socket server stub            |
|  . snapshot.rs   tar + zstd export/import           |
+-----------------------------------------------------+
|  EXECUTION ENGINE  proot 5.4+ ARM64 (precompilado)  |
|  . NO modificar proot. Solo invocarlo.              |
|  . Alpine Linux 3.19 minirootfs ARM64 (5MB base)   |
|  . Cada sesion = rootfs copiado e independiente     |
+-----------------------------------------------------+
|  ANDROID LAYER     ForegroundService + WorkManager  |
|  . Mantiene sesiones vivas en background            |
|  . FileProvider para export .gshell bundles         |
+-----------------------------------------------------+
```

**Arquitectura PTY (hibrida MVP):**
- Rust gestiona: session metadata, rootfs provisioning, proot args construction
- startSession() devuelve JSON array con los argumentos de proot
- Kotlin crea TerminalSession(shellPath=proot, args=prootArgs)
- termux-terminal-emulator maneja fork/PTY internamente
- Separacion limpia: Rust = datos + logica, Kotlin = UI + proceso

---

## STACK DEFINITIVO

| Capa             | Tecnologia                              | Por que                                      |
|------------------|-----------------------------------------|----------------------------------------------|
| Android UI       | Kotlin 1.9+ + Compose BOM 2024.x       | Nativo, moderno, minima friccion             |
| Session Manager  | Rust 1.78+ (cargo workspace)            | Control total de sesiones y metadata         |
| PTY              | termux-terminal-emulator v0.118.0       | PTY POSIX real ya resuelto via JitPack       |
| Execution        | proot 5.4+ ARM64 precompilado           | chroot sin root, battle-tested               |
| Base distro      | Alpine Linux 3.19 ARM64 minirootfs      | 5MB, APK manager, ARM64 nativo               |
| Terminal view    | termux-terminal-view v0.118.0 (JitPack) | PTY <-> display ya resuelto                 |
| Persistencia     | tar + zstd (Rust zstd crate)            | Snapshots comprimidos, portables             |
| IPC              | Unix domain sockets (stub MVP)          | Rust server <-> Kotlin, sin overhead HTTP   |
| JNI              | cargo-ndk -> .so                        | Rust compilado a libgravitalshell.so         |
| Background       | Android ForegroundService               | Android mata bg processes agresivamente      |

---

## ESTRUCTURA DEL PROYECTO

```
Gravital-Shell/
+-- CLAUDE.md                          <- este archivo
+-- README.md
+-- .gitignore
+-- settings.gradle.kts
+-- build.gradle.kts                   <- root gradle
+-- gradle.properties
+-- gradle/libs.versions.toml          <- version catalog
+-- gradlew / gradlew.bat
|
+-- app/                               <- Android module
|   +-- build.gradle.kts
|   +-- proguard-rules.pro
|   +-- src/main/
|       +-- AndroidManifest.xml
|       +-- kotlin/sh/gravital/shell/
|       |   +-- MainActivity.kt
|       |   +-- ui/
|       |   |   +-- SessionListScreen.kt
|       |   |   +-- TerminalScreen.kt
|       |   |   +-- components/
|       |   |   |   +-- SessionCard.kt
|       |   |   |   +-- CreateSessionDialog.kt
|       |   |   +-- theme/
|       |   |       +-- Theme.kt
|       |   |       +-- Color.kt
|       |   |       +-- Type.kt
|       |   +-- session/
|       |   |   +-- SessionViewModel.kt
|       |   |   +-- SessionState.kt
|       |   |   +-- SessionModel.kt
|       |   +-- bridge/
|       |   |   +-- GravitalShellBridge.kt    <- JNI
|       |   +-- service/
|       |   |   +-- ShellService.kt           <- ForegroundService
|       |   +-- setup/
|       |       +-- FirstRunSetup.kt          <- proot + Alpine install
|       +-- assets/                    <- proot-arm64 + alpine-minirootfs.tar.gz
|       +-- jniLibs/                   <- libgravitalshell.so (generado)
|       |   +-- arm64-v8a/
|       |   +-- x86_64/
|       +-- res/
|           +-- values/strings.xml
|           +-- values/colors.xml
|           +-- xml/file_paths.xml
|           +-- drawable/ic_launcher.xml
|
+-- session-manager/                   <- Rust workspace
|   +-- Cargo.toml                     <- workspace root
|   +-- .cargo/config.toml             <- NDK linker paths
|   +-- core/
|   |   +-- Cargo.toml
|   |   +-- src/
|   |       +-- lib.rs
|   |       +-- session.rs             <- Session struct
|   |       +-- manager.rs             <- SessionManager
|   |       +-- pty.rs                 <- proot args + PTY resize
|   |       +-- snapshot.rs            <- tar.zst export/import
|   |       +-- ipc.rs                 <- Unix socket stub
|   +-- jni/
|       +-- Cargo.toml
|       +-- src/
|           +-- lib.rs                 <- extern C JNI bindings
|
+-- assets/                            <- descargados por download-assets.sh
|   +-- proot-arm64                    <- precompiled proot binary
|   +-- alpine-minirootfs/             <- Alpine 3.19 ARM64 (extraido)
|   +-- alpine-minirootfs.tar.gz       <- Alpine 3.19 ARM64 (tarball)
|
+-- scripts/
    +-- setup-env.sh                   <- verifica dependencias
    +-- download-assets.sh             <- baja proot + Alpine
    +-- build-rust.sh                  <- cargo-ndk -> .so
    +-- build-android.sh               <- gradle assembleDebug
    +-- build-all.sh                   <- todo en un comando
```

---

## MODELO DE DATOS CANONICO (Rust)

```rust
// session-manager/core/src/session.rs

pub enum SessionPolicy { Ephemeral, Persistent, Snapshot }
pub enum SessionState  { Stopped, Running, Suspended }
pub enum Distro        { Alpine, Debian }

pub struct Session {
    pub id:          Uuid,
    pub name:        String,
    pub root:        PathBuf,      // filesDir/sessions/{id}/
    pub policy:      SessionPolicy,
    pub state:       SessionState,
    pub distro:      Distro,
    pub created_at:  i64,          // unix timestamp
    pub tags:        Vec<String>,
    pub pty_master:  Option<i32>,  // futuro: fd PTY master en Rust
    pub child_pid:   Option<u32>,  // PID del proceso proot
}

impl Session {
    pub fn rootfs_path(&self) -> PathBuf { self.root.join("rootfs") }
    pub fn home_path(&self)   -> PathBuf { self.root.join("home")   }
    pub fn meta_path(&self)   -> PathBuf { self.root.join("meta.json") }
    pub fn tmp_path(&self)    -> PathBuf { self.root.join("tmp")    }
}
```

---

## JNI API (GravitalShellBridge.kt <-> jni/src/lib.rs)

```
initialize(filesDir: String)
    -> inicia SessionManager, crea resolv.conf

createSession(name: String, policy: String) -> String
    -> devuelve UUID de la sesion creada

startSession(sessionId: String) -> String
    -> copia rootfs desde alpine-template si no existe
    -> devuelve JSON array con argumentos proot
    -> Kotlin crea TerminalSession(shellPath=proot, args=prootArgs)

stopSession(sessionId: String)
    -> envia SIGTERM al proceso hijo si esta corriendo

destroySession(sessionId: String)
    -> mata proceso, elimina filesDir/sessions/{id}/

listSessions() -> String
    -> devuelve JSON array de todas las sesiones

resizePty(fd: Int, rows: Int, cols: Int)
    -> TIOCSWINSZ sobre el fd PTY master

exportSession(sessionId: String, destPath: String)
    -> tar.zst del rootfs completo a destPath (.gshell)

importSession(srcPath: String) -> String
    -> descomprime .gshell, devuelve nuevo UUID
```

---

## FLUJO PTY EN TERMINALSCREEN

```
1. onOpenSession(id) en SessionListScreen
2. viewModel.buildTerminalSession(id, client)
   a. GravitalShellBridge.startSession(id) -> JSON args
   b. args[0] = ruta a proot-arm64 en filesDir
   c. TerminalSession(shellPath=args[0], args=args.drop(1))
3. navController.navigate("terminal/$id")
4. TerminalScreen:
   a. AndroidView { TerminalView }
   b. terminalView.attachSession(terminalSession)
   c. terminalSession.initializeEmulator() -> JNI.createSubprocess(proot)
   d. proot forkea, monta rootfs Alpine, ejecuta /bin/ash
   e. El usuario ve el prompt de Alpine
```

---

## ARGUMENTOS PROOT (build_proot_argv_strings en pty.rs)

```
{filesDir}/proot-arm64
--rootfs {filesDir}/sessions/{id}/rootfs
--bind=/dev
--bind=/proc
--bind=/sys
--bind={filesDir}/resolv.conf:/etc/resolv.conf
--bind=/dev/urandom:/dev/random
--change-id=0:0
--kill-on-exit
--pwd=/root
/bin/sh
-c
export TERM=xterm-256color && source /etc/profile 2>/dev/null; exec /bin/ash
```

---

## REGLAS DE DESARROLLO

```
R1  NO reinventar proot -- ya existe, ya funciona, solo invocarlo
R2  NO reinventar el terminal emulator -- JitPack termux-terminal-view v0.118.0
R3  proot se descarga precompilado ARM64, NUNCA compilar en el device
R4  Alpine rootfs se bundlea en assets/ del APK, FirstRunSetup lo extrae
R5  Toda logica de sesiones y rootfs vive en Rust -- Kotlin solo llama JNI
R6  ForegroundService obligatorio -- Android mata bg processes
R7  Workspace = filesDir/sessions/{uuid}/ -- NUNCA escribir fuera de filesDir
R8  Export format = .gshell (tar.zst del directorio de sesion completo)
R9  startSession devuelve JSON args para que Kotlin cree TerminalSession
R10 Cada sesion tiene su propio rootfs (copia de alpine-template) -- aislamiento real
```

---

## COMANDOS DEL PROYECTO

```bash
# Setup inicial (solo una vez)
./scripts/setup-env.sh          # verifica Android SDK, Rust, cargo-ndk, NDK, JDK17
./scripts/download-assets.sh    # baja proot ARM64 + Alpine 3.19 minirootfs

# Build
./scripts/build-rust.sh         # Rust -> libgravitalshell.so (arm64 + x86_64)
./scripts/build-android.sh      # gradle assembleDebug
./scripts/build-all.sh          # todo en un comando

# Dev loop
./gradlew installDebug          # instala en device/emulador conectado
adb logcat -s GravitalShell:V  # logs de la app en tiempo real
cd session-manager && cargo check  # verificacion rapida del codigo Rust
cd session-manager && cargo test --workspace  # tests Rust

# Git
git remote: https://github.com/angelnereira/Gravital-Shell.git
branch de desarrollo: claude/init-gravital-shell-fhtxz
```

---

## DEPENDENCIAS RUST (session-manager/Cargo.toml workspace)

```toml
[workspace.dependencies]
uuid       = { version = "1.7",  features = ["v4", "serde"] }
serde      = { version = "1",    features = ["derive"] }
serde_json = "1"
tokio      = { version = "1",    features = ["full"] }
anyhow     = "1"
thiserror  = "1"
log        = "0.4"
android_logger = "0.14"
zstd       = "0.13"
tar        = "0.4"
nix        = { version = "0.28", features = ["process", "signal", "fs", "term"] }
jni        = { version = "0.21", features = ["invocation"] }
once_cell  = "1"
```

---

## ASSETS A DESCARGAR (download-assets.sh)

```bash
# proot ARM64 estatico precompilado
URL: https://github.com/proot-me/proot/releases/download/v5.4.0/proot-v5.4.0-aarch64-static
DEST: assets/proot-arm64 + app/src/main/assets/proot-arm64
CHMOD: +x

# Alpine Linux 3.19.1 minirootfs ARM64
URL: https://dl-cdn.alpinelinux.org/alpine/v3.19/releases/aarch64/alpine-minirootfs-3.19.1-aarch64.tar.gz
DEST assets/alpine-minirootfs/ (extraido) + app/src/main/assets/alpine-minirootfs.tar.gz
SIZE: ~5MB comprimido

# Alpine packages a instalar dentro de cada sesion (post-launch):
apk add bash git curl wget openssh python3 py3-pip nodejs npm
apk add gcc g++ make cmake musl-dev binutils
apk add sqlite neovim tmux htop
```

---

## PREREQUISITOS PARA BUILD

```
- JDK 17+
- Android SDK con API 29-34
- Android NDK 26.1.10909125 (via SDK Manager)
- Rust + rustup con targets:
    aarch64-linux-android
    x86_64-linux-android
- cargo-ndk (cargo install cargo-ndk)
```

---

## DEFINICION DE MVP COMPLETADO

El MVP esta completo cuando se cumplan TODOS estos puntos:

- [ ] ./scripts/build-all.sh termina sin errores
- [ ] APK instala en Android 10+ fisico o emulador
- [ ] Primer launch: extrae proot + Alpine de assets automaticamente
- [ ] Se puede crear una sesion nueva desde la UI
- [ ] Al abrir la sesion, se ve una terminal funcional con prompt Alpine
- [ ] ls, pwd, echo, cat funcionan
- [ ] apk add git funciona (internet dentro del env)
- [ ] git clone https://github.com/... funciona dentro de la sesion
- [ ] gcc hello.c -o hello && ./hello compila y ejecuta
- [ ] python3 -c "print('hello')" funciona
- [ ] Ctrl+C mata el proceso en foreground
- [ ] ForegroundService mantiene sesion viva en background
- [ ] Destroy sesion limpia el rootfs del filesystem

---

## NOTAS TECNICAS IMPORTANTES

**PTY hibrido (MVP):**
startSession() en Rust construye y devuelve los argumentos proot como JSON.
Kotlin parsea esos argumentos y crea un TerminalSession de termux con proot
como proceso shell. termux-terminal-emulator maneja el fork/PTY internamente.
Esta arquitectura es la mas robusta para MVP sin modificar las librerias de termux.

**ForegroundService y procesos:**
ShellService como ForegroundService mantiene el proceso vivo.
Los procesos proot mueren cuando el proceso Android muere.
--kill-on-exit en proot garantiza que los hijos de proot tambien mueran.

**Aislamiento real:**
Cada sesion tiene su propio rootfs copiado de alpine-template.
cp -a filesDir/alpine-template/ filesDir/sessions/{id}/rootfs/
Borrar la sesion = rm -rf filesDir/sessions/{id}/

**resolv.conf:**
Creado en filesDir/resolv.conf con nameserver 8.8.8.8 y 1.1.1.1.
Bind-montado en /etc/resolv.conf dentro de cada sesion via proot.
Sin esto, apk add falla silenciosamente.

**Export .gshell:**
Un bundle .gshell es: tar.zst del directorio filesDir/sessions/{id}/
Import: extraer en nuevo filesDir/sessions/{new_id}/

---
# Gravital Shell -- Gravital Labs -- sh.gravital.shell
# github.com/angelnereira/Gravital-Shell
