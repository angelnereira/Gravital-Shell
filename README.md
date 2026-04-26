# Gravital Shell

Terminal Linux profesional para Android. Ejecuta entornos Ubuntu 22.04 LTS aislados sin root, con soporte completo de PTY, job control, apt y acceso web remoto.

## APKs listos para instalar

Los builds compilados se publican en la carpeta [`outputs/`](outputs/) de esta rama. Descarga el `.apk` mas reciente e instalalo directamente en tu dispositivo Android (API 29+).

```bash
# Instalar via ADB
adb install outputs/gravital-shell-0.1.0-debug.apk
```

## Caracteristicas

- Entorno Ubuntu 22.04 LTS ARM64 completo, sin root, via proot
- PTY real: Ctrl+C, Ctrl+Z, bg, fg, pipes, procesos interactivos
- Multiples sesiones aisladas en paralelo, cada una con rootfs propio
- Sesiones efimeras (auto-destruidas al salir) para tareas aisladas
- Gestor de archivos bidireccional: importa/exporta entre Android y el entorno
- Templates preconfigurados: Dev Toolkit, Claude Code CLI, Gemini CLI
- ForegroundService: sesiones viven en background sin que Android las mate
- Export/import de sesiones como bundles `.gshell` (tar.zst)
- Plataforma web: acceso remoto via xterm.js + WebSocket (Next.js + Neon)

## Capturas de pantalla

| Lista de sesiones | Terminal Ubuntu | Gestor de archivos |
|---|---|---|
| _(proxima version)_ | _(proxima version)_ | _(proxima version)_ |

## Requisitos de build

| Herramienta | Version minima |
|-------------|----------------|
| Android SDK | API 29 (Android 10) |
| Android NDK | 26.1.10909125 |
| JDK | 17+ |
| Rust | 1.78+ con rustup |
| cargo-ndk | 4.x (`cargo install cargo-ndk`) |

Targets Rust necesarios:

```bash
rustup target add aarch64-linux-android x86_64-linux-android
```

## Build completo

```bash
# 1. Verificar entorno de desarrollo
./scripts/setup-env.sh

# 2. Descargar proot ARM64 + Ubuntu 22.04 LTS rootfs
./scripts/download-assets.sh

# 3. Compilar la libreria Rust (libgravitalshell.so)
./scripts/build-rust.sh

# 4. Compilar el APK (copia resultado a outputs/)
./scripts/build-android.sh

# O todo en un comando:
./scripts/build-all.sh
```

El APK compilado queda en `outputs/gravital-shell-{version}-debug.apk`.

## Instalar en dispositivo

```bash
# Via ADB
adb install outputs/gravital-shell-0.1.0-debug.apk

# O instalar directamente desde Gradle
./gradlew installDebug

# Ver logs en tiempo real
adb logcat -s GravitalShell:V
```

## Primer uso

1. Abrir la app -- extrae Ubuntu 22.04 LTS de los assets automaticamente (~27 MB, solo la primera vez)
2. Pulsar "+" para crear una sesion nueva
3. Elegir template: Base Ubuntu, Dev Toolkit, Claude Code o Gemini CLI
4. Pulsar "Open" para abrir la terminal
5. Dentro de la terminal:

```bash
# Verificar Ubuntu
cat /etc/os-release

# Instalar herramientas
apt-get update && apt-get install -y git gcc python3 nodejs npm

# Compilar codigo C
echo '#include <stdio.h>
int main(){ printf("hello from Ubuntu\n"); }' > hello.c
gcc hello.c -o hello && ./hello

# Conectar a servidor remoto
ssh usuario@servidor.ejemplo.com

# Instalar Claude Code
npm install -g @anthropic-ai/claude-code
claude
```

## Gestor de archivos

Desde la pantalla de terminal, toca el icono de carpeta para acceder al gestor de archivos:

- Navega el rootfs del entorno Ubuntu
- Importa archivos desde el almacenamiento Android al entorno
- Exporta archivos del entorno para compartirlos con otras apps Android

## Sesiones efimeras

Al crear una sesion con politica **Ephemeral**, el entorno completo se destruye automaticamente al cerrar la terminal. Util para tareas aisladas, pruebas o entornos desechables.

## Arquitectura

```
UI (Kotlin + Jetpack Compose)
  |
  | JNI
  v
libgravitalshell.so (Rust)
  |
  | startSession() -> JSON con args de proot
  v
TerminalSession (termux-terminal-emulator)
  |
  | fork + execvp
  v
proot --rootfs {sesion}/rootfs [Ubuntu 22.04 LTS]
  |
  v
/bin/bash  <-- el usuario interactua aqui
```

### Capas del sistema

| Capa | Tecnologia |
|------|------------|
| UI Android | Kotlin 1.9 + Jetpack Compose + Material3 |
| Emulador PTY | termux-terminal-emulator v0.118.0 (JitPack) |
| Session Manager | Rust (cargo-ndk -> libgravitalshell.so) |
| Motor de ejecucion | proot 5.1+ ARM64 (sin root) |
| Distro base | Ubuntu 22.04 LTS ARM64 minirootfs |
| Background | Android ForegroundService |
| Snapshots | tar + zstd |
| Web | Next.js 14 + Neon Postgres + NextAuth + xterm.js |

## Estructura del proyecto

```
app/                        Modulo Android (Kotlin + Compose)
  src/main/
    kotlin/sh/gravital/shell/
      bridge/               JNI bridge (GravitalShellBridge.kt)
      files/                Gestor de archivos (FileBridgeScreen.kt)
      service/              ForegroundService
      session/              ViewModel, modelos, templates
      setup/                Extraccion de assets en primer arranque
      ui/                   Pantallas Compose
    assets/                 proot + ubuntu-base.tar.gz (generados por download-assets.sh)
    jniLibs/                libgravitalshell.so (generado por build-rust.sh)
session-manager/            Workspace Rust
  core/                     Logica: sesiones, proot args, file bridge, snapshots
  jni/                      Bindings JNI -> libgravitalshell.so
scripts/                    Scripts de build y setup
web/                        Plataforma web (Next.js + Neon + WebSocket)
outputs/                    APKs compilados listos para instalar
assets/                     proot + Ubuntu rootfs (no en git, descargados)
```

## Formato de sesiones

Cada sesion vive en `filesDir/sessions/{uuid}/`:

```
{uuid}/
  rootfs/     copia independiente del rootfs Ubuntu 22.04
  meta.json   metadata (nombre, politica, estado, timestamps)
  home/       directorio home adicional
  tmp/        temporales de la sesion
```

Un export `.gshell` es un `tar.zst` del directorio completo de sesion, portable entre dispositivos.

## Plataforma web

```bash
cd web
cp .env.example .env   # configurar DATABASE_URL, GITHUB_CLIENT_ID, etc.
npm install
npm run db:migrate     # crear tablas en Neon Postgres
npm run dev            # http://localhost:3000
```

Requiere: cuenta Neon (Postgres serverless), app OAuth en GitHub.

## Licencia

Gravital Labs -- Nereira Technology and Business Solutions
