# Running the app in an emulator on a laptop

Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.

No phone is needed to try the app, to develop it or to run the end-to-end tests. An
Android emulator on a laptop runs the real app with the real MediaPipe models. The
emulator is **not** evidence for phone performance or real-meal accuracy. Those need
the [hardware validation](hardware-validation.md) protocol.

## 1. What you need

| Item | Notes |
| --- | --- |
| Java 21 | `java -version` must report 21. |
| Android SDK command-line tools | Android Studio installs them. Without Studio, unpack the [command-line tools](https://developer.android.com/studio#command-line-tools-only) to `~/Android/Sdk/cmdline-tools/latest`. |
| Hardware virtualization | Linux: KVM (`/dev/kvm` must exist and be writable). macOS: built in. Windows: enable *Windows Hypervisor Platform*. |
| About 10 GB disk and 8 GB RAM | The system image is about 1.5 GB; the emulator uses 2 GB RAM. |

Set the SDK location once, for example in `~/.zshrc` or `~/.bashrc`:

```sh
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
```

On Linux, check KVM access:

```sh
ls -l /dev/kvm                   # must exist
sudo usermod -aG kvm "$USER"     # then log out and in again, if access is denied
```

## 2. Install the SDK packages and create the emulator

The project's scripts and CI use an Android 14 (API 34) *Google APIs* image named
`ForkApi34`. Pick the image that matches the laptop's processor:

| Laptop | System image |
| --- | --- |
| Intel or AMD (Linux, Windows, Intel Mac) | `system-images;android-34;google_apis;x86_64` |
| Apple Silicon Mac | `system-images;android-34;google_apis;arm64-v8a` |

```sh
IMAGE="system-images;android-34;google_apis;x86_64"     # or the arm64-v8a image
sdkmanager --install "platform-tools" "emulator" "platforms;android-37.0" "build-tools;36.0.0" "$IMAGE"
sdkmanager --licenses            # Gradle fetches any other missing SDK package itself
echo no | avdmanager create avd --name ForkApi34 --package "$IMAGE" --device pixel_6
```

Give the emulator a rear camera so the setup screens have a picture. Edit
`~/.android/avd/ForkApi34.avd/config.ini` and set:

```ini
hw.camera.back=emulated
hw.camera.front=none
hw.ramSize=2G
```

`emulated` shows the emulator's virtual 3D room. Use `webcam0` instead to point the
laptop's webcam at a real table and people. Nothing leaves the laptop either way; the
app has no internet permission.

## 3. Start the emulator

With a window, to click through the app yourself:

```sh
emulator -avd ForkApi34 -gpu auto &
```

Headless, the way the pipeline does it. The script waits until Android has booted and
does nothing when a device is already attached:

```sh
scripts/emulator.sh              # or: scripts/emulator.sh MyOtherAvd, or FORK_AVD=MyOtherAvd
adb devices                      # shows emulator-5554  device
```

## 4. Build, install and open the app

```sh
./localPipeline.sh --noOpen      # full pipeline; its last stage installs and starts the debug APK
# or only build and install:
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n it.marcelpetrick.fork/.MainActivity
```

The debug APK contains `arm64-v8a` and `x86_64` code and runs on both emulator types.
The release APK from GitHub is `arm64-v8a` only. It installs on an Apple Silicon
emulator and on phones, but not on an Intel/AMD emulator.

## 5. What to try

1. **Try demo (synthetic)** plays generated stick figures and shows a real reminder. It
   needs no camera.
2. **Set up camera** asks for the camera permission; allow it. With the virtual room
   nobody is in the picture, so use **Mark table without the check**.
3. Tap four corners of any surface, then **Finish setup** and **Start dinner**. The
   monitor runs; **Pause**, **Stop** and the adult diagnostics work as on a phone.
4. With `webcam0`, sit in front of the laptop with your arms on a table to see real
   landmarks, the visibility check and reminders.

Useful emulator controls: the side toolbar rotates the device; *Extended controls →
Camera* moves through the virtual room (hold Alt/Option and use W A S D and the
mouse); `adb emu kill` shuts the emulator down.

## 6. Tests and screenshots on the emulator

```sh
./gradlew :app:connectedDebugAndroidTest   # end-to-end: real models offline, UI flows
./localPipeline.sh --e2e required          # the whole pipeline, failing without a device
scripts/screenshots.sh                     # genuine screenshots into docs/screenshots/
```

## 7. Troubleshooting

| Symptom | Fix |
| --- | --- |
| `/dev/kvm` missing or "KVM is required" | Enable virtualization (VT-x/AMD-V) in the BIOS/UEFI and join the `kvm` group. |
| Emulator window stays black | Start with `-gpu swiftshader_indirect` (software rendering, slower but reliable). |
| `INSTALL_FAILED_NO_MATCHING_ABIS` | The release APK is arm64-only. Install the debug APK on an x86_64 emulator. |
| "Waiting for the camera image" never ends | The AVD has no rear camera. Set `hw.camera.back` as in step 2 and cold-boot (`emulator -avd ForkApi34 -no-snapshot-load`). |
| "System UI isn't responding" dialogs | Common on slow software-rendered emulators; tap *Wait*. The e2e tests dismiss them automatically. |
| Very low FPS in diagnostics | Expected in an emulator without GPU; the app says so and warns less, never more. Phone numbers come from the hardware protocol. |
