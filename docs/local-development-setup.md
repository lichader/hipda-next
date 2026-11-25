# Local Development Setup

Status: draft. Written on 2026-11-25. The command-line steps match the CI workflow ([.github/workflows/build.yml](../.github/workflows/build.yml)), which builds the debug APK on every push. Android Studio steps have not been tested end to end.

This guide covers building, installing, and checking HiPDA·NG on your own machine. For project layout and coding conventions, see [AGENTS.md](../AGENTS.md).

## Toolchain Summary

| Component | Version | Where it is set |
| --- | --- | --- |
| JDK | 11 | Required by AGP 7.2.2; Gradle 7.3.3 cannot run on JDK 18 or newer |
| Gradle | 7.3.3 (via wrapper) | [gradle/wrapper/gradle-wrapper.properties](../gradle/wrapper/gradle-wrapper.properties) |
| Android Gradle plugin (AGP) | 7.2.2 | [build.gradle](../build.gradle) |
| Android SDK platform | `android-30` | `compileSdkVersion` in [build.gradle](../build.gradle) |
| Android SDK build tools | 30.0.3 | Default for AGP 7.2.2 |
| Minimum device API | 21 (Android 5.0) | `minSdkVersion` in [build.gradle](../build.gradle) |
| Java language level | 8 | `compileOptions` in both module `build.gradle` files |

Always use the wrapper (`./gradlew`, or `gradlew.bat` on Windows) rather than a system-installed Gradle.

## 1. Install JDK 11

Install any JDK 11 distribution, such as Eclipse Temurin 11 (the one CI uses).

- macOS (Homebrew): `brew install --cask temurin@11`
- Ubuntu/Debian: `sudo apt install openjdk-11-jdk`
- Windows: download the Temurin 11 installer from adoptium.net.

Point Gradle at it. Either set `JAVA_HOME` in your shell:

```sh
export JAVA_HOME=/path/to/jdk-11
java -version   # should report 11.x
```

or, if your default JDK must stay newer, pin it for this project only by adding `org.gradle.java.home=/path/to/jdk-11` to `~/.gradle/gradle.properties`. Do not add it to the tracked `gradle.properties`, because the path is machine-specific.

## 2. Install the Android SDK

You need the SDK platform, build tools, and platform tools (for `adb`).

**With Android Studio:** open *Settings > Languages & Frameworks > Android SDK* and install *Android 11.0 (R) / API 30* under *SDK Platforms*, and *Android SDK Build-Tools 30.0.3* and *Android SDK Platform-Tools* under *SDK Tools* (tick *Show Package Details* to choose the exact build-tools version).

**Without Android Studio:** download the *Command line tools only* package from developer.android.com/studio, unpack it to `<sdk>/cmdline-tools/latest/`, then run:

```sh
export ANDROID_HOME=/path/to/android-sdk
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --install \
  "platforms;android-30" \
  "build-tools;30.0.3" \
  "platform-tools"
```

Recent versions of the command-line tools need JDK 17 to run `sdkmanager`. If it fails under JDK 11, run `sdkmanager` with a newer JDK and switch back to JDK 11 for Gradle; CI does the same.

## 3. Clone and Configure the Project

```sh
git clone https://github.com/lichader/hipda-next.git
cd hipda-next
```

Create `local.properties` in the repository root so Gradle can find the SDK. The file is ignored by Git.

```properties
# macOS example; on Windows, escape backslashes: C\:\\Users\\you\\AppData\\Local\\Android\\Sdk
sdk.dir=/Users/you/Library/Android/sdk
```

Android Studio creates this file automatically when it opens the project. Setting `ANDROID_HOME` also works in place of `sdk.dir`.

No other local configuration is needed for a debug build. In particular, the README's instruction to copy `fabric.properties.example` is outdated: that file does not exist and the build has no Fabric configuration.

## 4. Build

From the repository root:

```sh
./gradlew :hipda:assembleDebug
```

The first run downloads Gradle 7.3.3 and all dependencies from Maven Central, Google Maven, and JitPack, so it needs network access to those hosts. The APK is written to `hipda/build/outputs/apk/debug/`.

## 5. Run on a Device or Emulator

Use a physical device with USB debugging enabled, or an emulator created in Android Studio's Device Manager running API 21 or newer. With the SDK's `platform-tools` on your `PATH`, check that the device is visible and install:

```sh
adb devices
./gradlew :hipda:installDebug
```

Then launch *HiPDA·NG* on the device. The debug build uses the same application ID as the Play Store build (`net.jejer.hipda.ng`), so uninstall the store version first if it is installed; Android will refuse to replace an app signed with a different key.

Logging in, posting, and most forum flows need a HiPDA forum account.

## 6. Lint and Tests

```sh
./gradlew :hipda:lintDebug :emoji:lintDebug
```

Application lint errors do not fail the build (`abortOnError false`), so read the HTML reports under `hipda/build/reports/` and `emoji/build/reports/`.

There are no automated tests yet. `./gradlew :hipda:testDebugUnitTest :emoji:testDebugUnitTest` will run JVM tests once they are added. Until then, verify changes manually on a device, covering whichever of the forum, login, posting, image, emoji, and theme flows the change touches.

## Using Android Studio

Open the repository root with *File > Open*. Android Studio Chipmunk (2021.2.1) is the release paired with AGP 7.2; newer releases generally still open the project, but if one asks to upgrade AGP, decline: the upgrade is planned separately in [android-17-migration.md](android-17-migration.md).

Set the Gradle JDK to 11 under *Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK*. Android Studio's bundled JDK is usually newer than 17 and will not run Gradle 7.3.3.

The repository tracks a few `.idea/` files (copyright, encodings, scopes, VCS); leave other generated IDE files untracked.

## Release Builds

`./gradlew :hipda:assembleRelease` produces `hipda-ng-release.apk` with R8 minification. The build has no signing configuration, so the APK is unsigned and must be signed with `apksigner` before it can be installed. Keep keystores and passwords out of the repository; put any signing properties in `~/.gradle/gradle.properties`.

## Troubleshooting

| Symptom | Likely cause and fix |
| --- | --- |
| `Unsupported class file major version` or `Could not initialize class org.codehaus.groovy...` | Gradle is running on a JDK newer than 17. Switch `JAVA_HOME` or `org.gradle.java.home` to JDK 11. |
| `Android Gradle plugin requires Java 11 to run` | Gradle is running on JDK 8. Switch to JDK 11. |
| `SDK location not found` | Create `local.properties` with `sdk.dir`, or set `ANDROID_HOME`. |
| `Failed to find target with hash string 'android-30'` or missing build tools | Install `platforms;android-30` and `build-tools;30.0.3` with `sdkmanager`. |
| Dependency resolution fails for `com.github.*` artifacts | JitPack (`jitpack.io`) is unreachable; check network or proxy settings. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | A differently signed copy of the app is installed. Uninstall it, then reinstall. |
