# Building OmniBeat

This guide covers local builds, installation, and release preparation for the GitHub, F-Droid, and Google Play channels. Commands use Windows PowerShell and run from the repository root.

## Set up the build environment

Set `JAVA_HOME` to your JDK installation and configure the Android SDK location through `ANDROID_HOME` or the project's `local.properties` file. Add the SDK's `platform-tools` directory to `PATH` to run `adb` from your terminal. Use the paths appropriate for your system, and run the commands below from the repository root.

The project currently compiles against Android SDK 36.1 and requires Android 14 (API 34) or later to run.

## Choose a distribution channel

| Channel | Debug variant | Release variant | In-app updates |
|---|---|---|---|
| GitHub | `githubDebug` | `githubRelease` | Checks GitHub Releases and downloads APKs |
| F-Droid | `fdroidDebug` | `fdroidRelease` | Handled by F-Droid |
| Google Play | `playDebug` | `playRelease` | Handled by Google Play |

All variants use the same application ID, `omnibeat.app`, so they cannot be installed side by side. Installing an update over an existing build requires a compatible signing certificate.

Shared code lives in `app/src/main`. Only the GitHub flavor includes the updater and APK installation permission from `app/src/github`. The other channels do not need a separate setting to disable updates.

## Build a debug APK

In Windows PowerShell, you can invoke the wrapper with either `./gradlew` or `.\gradlew.bat`. On macOS or Linux, use `./gradlew`. All Gradle commands below use `./gradlew`.

Run the command for the channel you want:

```powershell
./gradlew :app:assembleGithubDebug
```

```powershell
./gradlew :app:assembleFdroidDebug
```

```powershell
./gradlew :app:assemblePlayDebug
```

Debug APKs are signed automatically with a debug key. The standard output paths are:

```text
app/build/outputs/apk/github/debug/app-github-debug.apk
app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk
app/build/outputs/apk/play/debug/app-play-debug.apk
```

For a Kotlin compilation check without packaging an APK, use:

```powershell
./gradlew :app:compileGithubDebugKotlin
```

The corresponding tasks for the other channels are `compileFdroidDebugKotlin` and `compilePlayDebugKotlin`.

## Install and launch on a specific device

When more than one device is connected, use `adb -s` to select the target explicitly:

```powershell
adb devices
adb -s emulator-5556 install -r .\app\build\outputs\apk\github\debug\app-github-debug.apk
adb -s emulator-5556 shell am start -n omnibeat.app/.MainActivity
```

Replace `emulator-5556` with the target's serial number. The `-r` option replaces the installed app while preserving its data, provided the signing certificates are compatible.

Gradle also provides build-and-install tasks:

```text
:app:installGithubDebug
:app:installFdroidDebug
:app:installPlayDebug
```

For example, run `./gradlew :app:installGithubDebug`. These tasks install the app but do not launch it. With multiple connected devices, building the APK and installing it with `adb -s` gives you explicit control over the target.

### Uninstall before installing

Uninstalling removes the station library, settings, and cache. It is usually unnecessary during development.

To uninstall from one device:

```powershell
adb -s emulator-5556 uninstall omnibeat.app
```

If your Android Studio run configuration has an uninstall task under **Before launch**, use the full task name for that channel:

| Channel | Uninstall task |
|---|---|
| GitHub | `:app:uninstallGithubDebug` |
| F-Droid | `:app:uninstallFdroidDebug` |
| Google Play | `:app:uninstallPlayDebug` |

`:app:uninstallDebug` is ambiguous now that the project has multiple flavors. A Gradle uninstall task does not necessarily use the device selected in Android Studio's run toolbar.

## Run from Android Studio

1. After changing the Gradle configuration, select **Sync Project with Gradle Files**.
2. Open **View → Tool Windows → Build Variants**.
3. Set the `app` module's active variant to `githubDebug`, `fdroidDebug`, or `playDebug`.
4. In the **Android App** run configuration, keep **Deploy: Default APK**, **Launch: Default Activity**, and **Gradle-aware Make**.
5. If you need an uninstall step before every run, use the matching task from the table above.
6. Select a device and click Run or Debug.

**Build Variants** is a separate tool window, not a field in the Run/Debug Configurations dialog. Adding `assembleGithubDebug` to **Before launch** builds that APK but does not change the active variant Android Studio deploys. See [Configure build variants](https://developer.android.com/build/build-variants).

## Build release artifacts

### APKs

```powershell
./gradlew :app:assembleGithubRelease
```

```powershell
./gradlew :app:assembleFdroidRelease
```

```powershell
./gradlew :app:assemblePlayRelease
```

The output directories are:

```text
app/build/outputs/apk/github/release/
app/build/outputs/apk/fdroid/release/
app/build/outputs/apk/play/release/
```

**The current Gradle configuration does not define a release `signingConfig`.** These commands produce unsigned APKs, typically named `app-<flavor>-release-unsigned.apk`. Sign them before installation or distribution.

### Google Play app bundle

```powershell
./gradlew :app:bundlePlayRelease
```

The standard output path is:

```text
app/build/outputs/bundle/playRelease/app-play-release.aab
```

This bundle also needs a signature. An AAB cannot be installed directly with `adb install`.

### Generate a signed release in Android Studio

Open **Build → Generate Signed App Bundle / APK**, then:

1. Choose **APK** for GitHub or **Android App Bundle** for Google Play.
2. Select your keystore and key alias.
3. Choose `githubRelease` or `playRelease` as appropriate.
4. Set **Destination Folder** to `...\omnibeat\app\build\` within your local checkout, then complete the build. Make sure the directory already exists; if it does not, create it first.

Check the destination before generating a signed release. Choosing `app\build\` keeps the exported files inside the project's ignored build directory. Leaving it set to `app\` exports files to folders such as `app\github\release\`, outside that directory.

Keep the signing key you use for each channel so future releases can update existing installations. A debug key is not a substitute for a release key. See Android's [command-line build and signing guide](https://developer.android.com/build/building-cmdline).
