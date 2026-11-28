# Repository Guidelines

## Project Structure & Module Organization

HiPDA Next is an unofficial Android client for the HiPDA forum, built with Java, Kotlin and Gradle.

- `hipda/`: application module. Java sources live in `src/main/java/com/lichader/hipdanext/` and Kotlin sources in `src/main/kotlin/com/lichader/hipdanext/` (tests likewise under `src/test/java/` and `src/test/kotlin/`), grouped into `ui`, `bean`, `db`, `okhttp`, `glide`, `job`, `async`, `cache`, `service`, and `utils` packages.
- `hipda/src/main/res/`: layouts, strings, themes, menus, and images; `src/main/assets/` contains release notes, licenses, and donor information.
- `emoji/`: local Android library (Java only), with Java sources under `src/main/java/com/vanniktech/emoji/` and emoji resources under `src/main/res/`.
- `forum/`: pure Kotlin/JVM library (no Android SDK) with the site adapter, the only code that builds forum URLs and reads its HTML. Sources are under `src/main/kotlin/com/lichader/hipdanext/forum/`; page fixtures for its tests are under `src/test/resources/fixtures/`.
- Root Gradle files define shared SDK versions and repositories; `gradle/wrapper/` pins Gradle 9.6.0 (AGP 9.5.3).

## Build, Test, and Development Commands

Use JDK 21 (pinned in `gradle/gradle-daemon-jvm.properties`; `.sdkmanrc` names the SDKMAN! build) and the Android SDK API 37 platform (compile and target SDK 37). The minimum supported Android API is 21. `scripts/bootstrap.sh` installs both on Linux or macOS and writes `sdk.dir` to the untracked `local.properties`; see `docs/local-development-setup.md` for manual steps. Run commands from the repository root:

- `./gradlew :hipda:assembleDebug`: build a debug APK under `hipda/build/outputs/apk/debug/`.
- `./gradlew :hipda:installDebug`: install on a connected device or emulator; launch the app there.
- `./gradlew :hipda:lintDebug :emoji:lintDebug`: run Android Lint for both modules. Application lint errors currently do not fail the build, so review reports.
- `./gradlew :hipda:testDebugUnitTest`: run the Robolectric unit tests (JVM, no device needed).
- `./gradlew :forum:test`: run the site adapter's tests against HTML fixtures and OkHttp `MockWebServer`.

## Coding Style & Naming Conventions

Use four-space indentation and same-line opening braces in Java; follow surrounding XML formatting. Use `PascalCase` class names, `camelCase` methods, and `UPPER_SNAKE_CASE` constants. Preserve existing `m`-prefixed fields where used. Name Android resources in `lower_snake_case`, such as `fragment_thread_detail.xml`; prefix new emoji library resources with `emoji`. Java code may use Java 21 language features; D8 desugars them for API 21 devices. Newer Java library APIs are not desugared, so check that any `java.*` API you call exists at the minimum API level (lint flags those that do not). No standalone formatter is configured.

## Kotlin

Kotlin is compiled by AGP's built-in Kotlin support, so no `kotlin-android` plugin is applied; the root `build.gradle` pins the Kotlin version (`kotlin_version`). Kotlin targets the same JVM 21 bytecode as Java. New code may be written in Kotlin, and Java files may be converted one at a time; keep the package name and, for code Java still calls, keep the Java-facing API (for example `@JvmStatic` on `object` members). Follow the official Kotlin coding conventions with four-space indentation. Annotation processing for Hilt runs through KSP (`ksp`, `kspTest`), which covers both languages; Glide's compiler still uses `annotationProcessor`, so keep the `AppGlideModule` in Java.

## Testing Guidelines

`forum/src/test/` holds plain JUnit tests of the parsers, transport and feature APIs, and needs neither Robolectric nor the forum. Their fixtures should be real pages: capture them with the debug build's response recorder (`scripts/forum-captures.sh`) and scrub them as `docs/capturing-forum-pages.md` describes. `hipda/src/test/java/` and `hipda/src/test/kotlin/` hold JUnit 4 + Robolectric tests, and plain ViewModel tests with fake repositories: `AppStartupTest` checks application start-up and the app-wide services, and `ActivityLaunchTest` opens every screen offline, then recreates it the way a configuration change does. Keep them passing and extend them when you add screens or services. No instrumentation runner or coverage threshold is configured; place device tests in `<module>/src/androidTest/java/`, using names such as `HiParserTest.java`. Manually verify affected forum, login, posting, image, emoji, and theme flows on a device or emulator, since the tests cannot reach the forum.

## Architecture

The app is moving one feature at a time to layers: Fragment → ViewModel → repository → `:forum` site adapter. The plan, its rules and the migration status are in `docs/architecture-layering-plan.md`. Its "Next work" section is the project's to-do list: take work from it, update the status there, and add new findings to it. That section also sets the rules for testing with the owner's real forum account: never post, vote or message anyone else. In new code, don't call `OkHttpHelper`, Jsoup or `HiUtils.*Url` from UI classes. Add the request to a `:forum` API, put the app's policy in a repository under `data/`, and drive the screen from a `@HiltViewModel` that exposes `StateFlow` state. `:forum` has no DI of its own and is wired in `di/ForumModule.kt`. Because lint does not check its API levels, call only `java.*` APIs that exist on API 21.

## Dependency Injection

The app uses Hilt (`@HiltAndroidApp` on `HiApplication`). The core services are being moved from `getInstance()` singletons to Hilt bindings step by step; prefer injecting a service over calling `getInstance()` in new code.

## Commit & Pull Request Guidelines

Never commit to `master` directly. Make every change on its own branch, created from an up-to-date `master`. Where possible, work in a separate git worktree (`git worktree add ../hipda-next-<topic> -b <branch> master`) so the main checkout stays on `master`. Merge into `master` only through a pull request.

History uses short, lowercase imperative subjects, such as `fix image upload` and `improve vote ui`. Keep commits focused. PRs should describe the behavior change, link related issues, state build and validation results, and include screenshots for UI changes.

## Security & Configuration

Keep credentials, signing keys, and machine-specific settings out of commits. `local.properties` and `fabric.properties` are ignored. The tracked `gradle.properties` holds only non-secret project settings required to build; put signing keys and passwords in `~/.gradle/gradle.properties`. CI builds debug and release APKs, runs the unit tests, and lints on every push using `.github/workflows/build.yml`. The README's Fabric setup note references an absent example file; current Gradle files contain no Fabric plugin configuration.
