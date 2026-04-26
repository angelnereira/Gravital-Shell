# Gravital Shell

Terminal Linux profesional para Android. Ejecuta entornos Alpine Linux aislados sin root, con soporte completo de PTY, job control y paquetes APK.

## Caracteristicas

- Multiples sesiones aisladas en paralelo, cada una con su propio rootfs Alpine
- PTY real: Ctrl+C, Ctrl+Z, bg, fg, pipes, procesos interactivos
- Sin root: usa proot para chroot en espacio de usuario
- APK manager: instala gcc, git, python3, nodejs, ssh y cualquier paquete Alpine
- Export/import de sesiones como bundles `.gshell` (tar.zst)
- ForegroundService: sesiones viven en background sin que Android las mate

## Requisitos

| Herramienta | Version minima |
|-------------|----------------|
| Android SDK | API 29 (Android 10) |
| Android NDK | 26.1.10909125 |
| JDK | 17+ |
| Rust | 1.78+ con rustup |
| cargo-ndk | 3.x (`cargo install cargo-ndk`) |

Targets Rust necesarios:
```
rustup target add aarch64-linux-android x86_64-linux-android
```

## Setup inicial

```bash
# 1. Verificar entorno
./scripts/setup-env.sh

# 2. Descargar proot ARM64 y Alpine Linux 3.19
./scripts/download-assets.sh

# 3. Compilar la libreria Rust
./scripts/build-rust.sh

# 4. Compilar el APK
./scripts/build-android.sh

# O todo en un comando:
./scripts/build-all.sh
```

## Instalar en dispositivo

```bash
./gradlew installDebug

# Ver logs en tiempo real
adb logcat -s GravitalShell:V
```

## Primer uso

1. Abrir la app -- extrae Alpine Linux de los assets automaticamente (~5MB)
2. Pulsar "+" para crear una sesion nueva
3. Pulsar "Open" para acceder a la terminal
4. Dentro de la terminal:

```sh
# Verificar que Alpine funciona
cat /etc/alpine-release

# Instalar herramientas
apk add git gcc python3 nodejs

# Compilar codigo C
echo '#include <stdio.h>
int main(){ printf("hello from Alpine\n"); }' > hello.c
gcc hello.c -o hello && ./hello

# Conectar a servidor remoto
ssh usuario@servidor.ejemplo.com
```

## Estructura del proyecto

```
app/                        Android module (Kotlin + Compose)
session-manager/            Rust workspace
  core/                     Logica de sesiones, proot args, snapshots
  jni/                      Bindings JNI para Android
scripts/                    Scripts de build y setup
assets/                     proot + Alpine (descargados, no en git)
```

## Arquitectura

```
UI (Kotlin/Compose)
  |
  | JNI
  v
libgravitalshell.so (Rust)
  |
  | startSession() -> args JSON
  v
TerminalSession (termux-terminal-emulator)
  |
  | fork + execvp
  v
proot --rootfs {sesion}/rootfs [Alpine Linux]
  |
  v
/bin/ash  <-- el usuario interactua aqui
```

## Formato de sesiones

Cada sesion vive en `filesDir/sessions/{uuid}/`:

```
{uuid}/
  rootfs/     copia independiente del rootfs Alpine
  meta.json   metadata de la sesion (nombre, politica, estado)
  home/       directorio home adicional
  tmp/        temp de la sesion
```

Un export `.gshell` es un `tar.zst` del directorio completo de sesion.

## Licencia

Gravital Labs -- Nereira Technology and Business Solutions
