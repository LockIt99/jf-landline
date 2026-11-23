# Jio Landline — VS Code DevContainer

This DevContainer gives you a complete, reproducible build environment for the
Jio Landline Android application. It installs everything you need in one click:

| Tool | Version | Purpose |
|------|---------|---------|
| **Base image** | Ubuntu 22.04 | Host OS |
| **JDK** | OpenJDK 17 | Gradle / Android Gradle Plugin 8.2.2 |
| **Gradle** | 8.11.1 | Via wrapper (`gradlew`) — downloaded on first build |
| **Android SDK** | `/opt/android-sdk` | `platforms;android-34`, `build-tools;33.0.0`, `build-tools;34.0.0`, `platform-tools`, `emulator`, NDK `26.3.11579264` |
| **ADB** | platform-tools | Install APK to a device/emulator |

---

## Prerequisites on your machine

- **Docker Desktop** (Windows / macOS / Linux)
- **Visual Studio Code**
- **Dev Containers extension** (`ms-vscode-remote.remote-containers`)

---

## Quick start

1. Clone the repository and open it in VS Code:
   ```bash
   git clone https://github.com/arnab8820/jf-landline.git
   cd jf-landline
   code .
   ```

2. Press `Ctrl+Shift+P` → **Dev Containers: Reopen in Container**.
   The first build will take a few minutes while the SDK and Gradle are installed.

3. When the container is ready, build the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
   (Note: Use `--no-daemon` if the build hangs or fails due to environmental issues.)
   Output: `app/build/outputs/apk/debug/app-debug.apk`

4. Build a signed release APK (uses `release.keystore` at the repo root):
   ```bash
   ./gradlew assembleRelease
   ```
   Output: `app/build/outputs/apk/release/app-release.apk`

---

## Installing the APK

The container ships with `adb`. Two common options:

**On a real device (over USB):**
```bash
adb devices          # confirm your device is listed
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**On an Android emulator:** start one from inside the container:
```bash
emulator -avd <avd-name>
```
(The container installs `system-images;android-34;google_apis;x86_64` but no AVD is
pre-created. Create one with `avdmanager` or pass an AVD name if you have one.)

---

## Making your own changes

- **Java source** lives under `app/src/main/java/com/jio/calling/`. The Gradle Java
  extension (Red Hat) is pre-installed for autocomplete, linting, and refactoring.
- **Android XML** (manifest, layouts, drawables) gets syntax highlighting via the
  `mauve.vscode-android-xml` extension.
- **Native PJSIP libraries** are prebuilt under `app/src/main/jniLibs/`
  (`arm64-v8a`, `armeabi-v7a`, `x86`). No NDK build is required to produce an APK.
  If you want to rebuild PJSIP itself, that is outside this container — see the
  `../ndk-build/` notes referenced in the main README.
- **SIP protocol / gateway logic** is in `app/src/main/java/com/jio/calling/`.
  The companion HTTP server listens on port **8088** — the DevContainer forwards
  that port automatically so you can hit it from your host at `http://localhost:8088`.

---

## Tips

- **Gradle caching:** the `.gradle/` directory is mounted from the host repo, so
  dependencies persist between rebuilds.
- **Release signing:** `release.keystore` is committed at the repo root and wired
  into `app/build.gradle` (`storePassword 'jiofiber'`, `keyAlias 'jio-calling'`).
- **Rebuild from scratch:** `./gradlew clean` or delete `app/build/`.
- **Port conflict:** the app's gateway server (port 8088) is exposed in the
  DevContainer; if you run another instance, stop it first.

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `SDK location not found` | Add a `local.properties` file: `sdk.dir=/opt/android-sdk` (the container sets `ANDROID_HOME` so this is usually auto-detected). |
| `Gradle daemon crashed` | Increase memory: `org.gradle.jvmargs=-Xmx2048m` in `gradle.properties`. |
| `License not accepted` | Run inside the container: `sdkmanager --licenses` and accept all. |
| Emulator won't start | Emulators need KVM; on most Docker Desktop setups it is not available. Use a real device or a cloud emulator instead. |