# Gravital Shell — Release Index

Each row represents one build. The **versioned** APK is overwritten when the
version number stays the same; `gravital-shell-latest-debug.apk` always
contains the most recent build.

| Build | Version | versionCode | Date (UTC) | Commit | Message | Size |
|------:|---------|-------------|------------|--------|---------|------|
| 1 | 0.1.0 | 1 | 2026-04-26 22:48 UTC | a859201 | fix: move session start to IO thread, fix stale rootfs template detection | 85M |
| 2 | 0.1.0 | 1 | 2026-04-26 23:07 UTC | 4b69aa5 | feat: add APK versioning system with build counter and release index | 85M |
| 3 | 0.1.0 | 1 | 2026-05-19 04:10 UTC | 3a97cdc | fix: construct TerminalSession on main thread to satisfy Android Looper requirement | 87M |
| 4 | 0.1.0 | 1 | 2026-05-19 05:51 UTC | 609ea67 | fix: install proot as libproot.so in jniLibs to bypass Android SELinux noexec | 87M |
