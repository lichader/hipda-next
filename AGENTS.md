# Repository Guidelines

## Project Structure & Module Organization

HiPDA·NG is an unofficial Android client for the HiPDA forum, built with Java and Gradle.

- `hipda/`: application module. Sources live in `src/main/java/net/jejer/hipda/`, grouped into `ui`, `bean`, `db`, `okhttp`, `glide`, `job`, `async`, `cache`, `service`, and `utils` packages.
- `hipda/src/main/res/`: layouts, strings, themes, menus, and images; `src/main/assets/` contains release notes, licenses, and donor information.
- `emoji/`: local Android library, with Java sources under `src/main/java/com/vanniktech/emoji/` and emoji resources under `src/main/res/`.
- Root Gradle files define shared SDK versions and repositories; `gradle/wrapper/` pins Gradle 7.3.3.

## Build, Test, and Development Commands

Use JDK 11 and Android SDK 30. The minimum supported Android API is 21. Configure `sdk.dir` in an untracked `local.properties` file. Run commands from the repository root:

- `./gradlew :hipda:assembleDebug`: build a debug APK under `hipda/build/outputs/apk/debug/`.
- `./gradlew :hipda:installDebug`: install on a connected device or emulator; launch the app there.
- `./gradlew :hipda:lintDebug :emoji:lintDebug`: run Android Lint for both modules. Application lint errors currently do not fail the build, so review reports.
- `./gradlew :hipda:testDebugUnitTest :emoji:testDebugUnitTest`: run local unit tests once test dependencies and tests are added.

## Coding Style & Naming Conventions

Use four-space indentation and same-line opening braces in Java; follow surrounding XML formatting. Use `PascalCase` class names, `camelCase` methods, and `UPPER_SNAKE_CASE` constants. Preserve existing `m`-prefixed fields where used. Name Android resources in `lower_snake_case`, such as `fragment_thread_detail.xml`; prefix new emoji library resources with `emoji`. Keep code compatible with Java 8. No standalone formatter is configured.

## Testing Guidelines

No automated tests, test framework dependencies, instrumentation runner, or coverage threshold are configured. Place new JVM tests in `<module>/src/test/java/` and device tests in `<module>/src/androidTest/java/`, using names such as `HiParserTest.java`; configure their dependencies and runner as needed. Manually verify affected forum, login, posting, image, emoji, and theme flows on a device or emulator.

## Commit & Pull Request Guidelines

History uses short, lowercase imperative subjects, such as `fix image upload` and `improve vote ui`. Keep commits focused. PRs should describe the behavior change, link related issues, state build and validation results, and include screenshots for UI changes.

## Security & Configuration

Keep credentials, signing keys, and machine-specific settings out of commits. `local.properties`, `gradle.properties`, and `fabric.properties` are ignored. The README's Fabric setup note references an absent example file; current Gradle files contain no Fabric plugin configuration.
