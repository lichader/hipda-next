# Android 17 Migration Plan

Status: planned. Repository and Android documentation reviewed on 2026-11-17.

## Objective

Update HiPDA·NG to compile and target Android 17 (API 37), preserving forum browsing, accounts, posting, images, downloads, notifications, and the local emoji library. Keep the existing Java/XML architecture throughout the compatibility migration.

Upgrade build tooling, dependencies, and target SDK behavior in separate, reviewable changes. Raise `compileSdk` before `targetSdk` so compilation problems can be resolved before enabling new platform behavior.

## Current Baseline

| Area | Current configuration |
| --- | --- |
| Modules | `hipda` application and `emoji` Android library |
| SDK levels | Minimum 21, compile 30, target 30 |
| Build tooling | Android Gradle plugin (AGP) 7.2.2; Gradle wrapper 7.3.3 |
| Java source compatibility | Java 8 in both modules |
| Application ID | `com.lichader.hipdanext` |
| Source packages | `com.lichader.hipdanext` and `com.vanniktech.emoji` |
| Background polling | WorkManager 1.0.1 via `android.arch.work:work-runtime` |
| Validation | No test source sets or configured test framework; application lint errors are nonfatal |

Configuration lives in [build.gradle](../build.gradle), [hipda/build.gradle](../hipda/build.gradle), [emoji/build.gradle](../emoji/build.gradle), and the [Gradle wrapper properties](../gradle/wrapper/gradle-wrapper.properties).

This plan is based on static inspection. No Android SDK was configured in the review environment, so the current application build and device behavior have not been verified.

## Decisions to Record

- **Reason for targeting API 37:** record what drives the target, such as store target-API requirements or Android 17 runtime behavior. The reason determines whether intermediate targets can ship and how much of Stage 6 the first release needs.
- **Oldest supported Android version:** preserve API 21 until a different minimum is agreed. Select dependencies accordingly. WorkManager 2.11 raised its minimum to 23, and 2.12.0 raised it to 24; upgrading to the newest release therefore changes device support. See the [WorkManager release notes](https://developer.android.com/jetpack/androidx/releases/work). Other current AndroidX libraries may also require API 23 or higher, so check each library's release notes during Stage 2 rather than treating WorkManager as the only constraint. Raising the minimum to 23 would also remove the API 21–22 plaintext-credential branch described in [TD-02](technical-debt/td-02-credential-storage-silently-falls-back-to-plaintext.md) and the API-level handling for `FLAG_IMMUTABLE`, which was added in API 23.
- **Build environment:** use a compatible AGP/Gradle/JDK combination and pin it in local setup and CI. Java source compatibility can remain at 8 while Gradle runs on JDK 17.
- **Upgrade compatibility:** preserve the application ID, signing identity, stored accounts, database contents, and scheduled work across an in-place app upgrade.

## Stage 0: Establish a Reproducible Baseline

- [ ] Configure JDK 11 and Android SDK 30 for the existing build; record any existing build failures before changing versions.
- [ ] Build debug and minified release variants and capture lint reports.
- [ ] Record working device behavior for login, forum browsing, posting, image upload/save/share, downloads, notifications, themes, and emoji input.
- [ ] Add focused parser fixtures and file-copy regression tests, configuring test dependencies first. Keep account data and cookies out of fixtures.
- [ ] Add CI for builds, unit tests, and lint. Record existing lint findings and distinguish new failures from the baseline.

Exit criterion: the existing app has a reproducible build and a recorded validation baseline, or documented blockers with reproducible errors.

## Stage 1: Modernize Build Tooling

A documented API 37-compatible starting combination is **AGP 9.1.1, Gradle 9.3.1, and JDK 17**. These are compatible versions, not a requirement to use the newest release of every tool. See [API-level tool requirements](https://developer.android.com/build/releases/about-agp) and [AGP 9.1.1 compatibility](https://developer.android.com/build/releases/agp-9-1-0-release-notes).

- [ ] Upgrade AGP and the wrapper together, using intermediate upgrades and the AGP Upgrade Assistant as needed. Keep each checkpoint buildable.
- [ ] Add `namespace 'com.lichader.hipdanext'` to the app and `namespace 'com.vanniktech.emoji'` to the library. Move namespace ownership out of manifest `package` attributes and move app version metadata into Gradle.
- [ ] Enable app `buildFeatures { buildConfig true }`; existing code imports `BuildConfig`.
- [ ] Replace menu `case R.id...` statements with comparisons compatible with non-final resource IDs.
- [ ] Qualify or locally define dependency resources currently referenced through the app's `R`, such as `R.color.md_red_700`, when adopting non-transitive `R` classes.
- [ ] Replace the app's `applicationVariants` APK-renaming hook with supported `androidComponents` APIs or a packaging step.
- [ ] Replace `getDefaultProguardFile('proguard-android.txt')` with `proguard-android-optimize.txt` and review release keep rules.
- [ ] Make required AndroidX/Jetifier settings reproducible. Project `gradle.properties` is currently ignored; track project settings deliberately and keep secrets in user-level configuration.
- [ ] Set `compileSdk` to 37 while explicitly retaining target 30 until the relevant runtime fixes are ready. Configure target SDK in the application module and adapt library DSL settings to the selected AGP.

Namespace, resource-ID, and `BuildConfig` changes are documented in the [AGP 8 migration notes](https://developer.android.com/build/releases/agp-8-0-0-release-notes). Variant API and ProGuard changes are covered by the [AGP 9 migration notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes).

Exit criterion: debug and minified release builds succeed with the selected tooling and compile SDK, with target SDK still controlled separately.

## Stage 2: Update Dependencies

- [ ] Replace `android.arch.work:work-runtime:1.0.1` with an `androidx.work:work-runtime` version compatible with the agreed minimum SDK.
- [ ] Update [NotiWorker](../hipda/src/main/java/com/lichader/hipdanext/service/NotiWorker.java) to use `WorkManager.getInstance(context)`. Verify existing unique work named `noti_work` survives an app upgrade without duplicate scheduling.
- [ ] Update AppCompat, Activity/Fragment, Preference, and Material together where version compatibility requires it, including AppCompat in the emoji module.
- [ ] Update OkHttp and its cookie-store integration together; verify session persistence, authenticated requests, and downloads. Update Glide and its integration/compiler artifacts together.
- [ ] Update Jsoup, Gson, and remaining UI/image libraries in separate groups, testing affected behavior after each group.
- [ ] Assess the priority job queue, Calligraphy/ViewPump, MaterialDrawer/Iconics, and compressor for compatibility or replacement where needed.
- [ ] Review the existing `androidx.security` minimum-SDK override and encrypted preference migration. Resolve compatibility explicitly rather than adding blanket manifest overrides.

Exit criterion: dependencies resolve reproducibly and login, parsing, posting, background polling, and release shrinking pass validation.

## Stage 3: Fix Manifest and Notifications

The [application manifest](../hipda/src/main/AndroidManifest.xml) omits exported declarations for its launcher and public link alias. [NotiHelper](../hipda/src/main/java/com/lichader/hipdanext/service/NotiHelper.java) creates its notification `PendingIntent` with only `FLAG_UPDATE_CURRENT`.

- [ ] Explicitly export the launcher activity and public link alias. Keep internal components private and verify the merged manifest, including library components.
- [ ] Add `FLAG_IMMUTABLE` to the notification intent, with appropriate API handling if API 21–22 remain supported.
- [ ] Declare `POST_NOTIFICATIONS` and request it on Android 13+ when users enable notifications. Handle refusal and disabled channels without repeated prompts or crashes.
- [ ] Verify notification channels exist when background work runs after a cold process start.
- [ ] Test notification taps, unread counts, public links, and scheduled polling after process death and reboot.

These requirements start with [Android 12 component exporting and pending intents](https://developer.android.com/about/versions/12/behavior-changes-12#security-and-privacy) and [Android 13 notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission).

## Stage 4: Repair Storage and Image Handling

- [ ] Remove the unconditional storage-permission gate in [Utils.download()](../hipda/src/main/java/com/lichader/hipdanext/utils/Utils.java) on modern Android. Retain legacy permission checks only for operations and OS versions that require them.
- [ ] Limit `WRITE_EXTERNAL_STORAGE` to API 28 if older-device public-storage writes are retained. Verify authenticated attachment downloads to Downloads without requesting storage access on newer devices.
- [ ] Preserve content-URI handling in [PostFragment](../hipda/src/main/java/com/lichader/hipdanext/ui/PostFragment.java) and [UploadImgHelper](../hipda/src/main/java/com/lichader/hipdanext/async/UploadImgHelper.java); consider the system photo-picker contract when updating Activity libraries. Test cloud-backed selections and cancellation.
- [ ] Fix the existing byte-copy bug in `UIUtils.saveImageQ()`: write only the bytes actually read, stop before EOF, and close both streams. Add byte-for-byte regression cases with sizes that are not multiples of 1 KB.
- [ ] Handle null MediaStore results, publish completed writes using `IS_PENDING`, and clean up failed inserts.
- [ ] Narrow [FileProvider paths](../hipda/src/main/res/xml/provider_paths.xml) to the directories actually shared and verify URI grants in receiving apps.

The byte-copy bug is an existing defect, independent of target SDK. The modern save path already uses MediaStore in [UIUtils](../hipda/src/main/java/com/lichader/hipdanext/utils/UIUtils.java). Consult the [shared-media guidance](https://developer.android.com/training/data-storage/shared/media) for permission boundaries.

Exit criterion: selection, upload, save, share, and download work on the oldest retained OS and Android 17; saved files match their source bytes.

## Stage 5: Migrate Navigation and Layout Behavior

- [ ] Move activity Back handling to AndroidX `OnBackPressedDispatcher` callbacks. Preserve fragment behavior, drawer dismissal, draft confirmation, and image-viewer transitions in [BaseActivity](../hipda/src/main/java/com/lichader/hipdanext/ui/BaseActivity.java), [MainFrameActivity](../hipda/src/main/java/com/lichader/hipdanext/ui/MainFrameActivity.java), and [ImageViewerActivity](../hipda/src/main/java/com/lichader/hipdanext/ui/ImageViewerActivity.java).
- [ ] Retire hidden activity-method reflection in [SwipeUtils](../hipda/src/main/java/com/lichader/hipdanext/ui/widget/swipeback/SwipeUtils.java) and reconcile custom swipe gestures with system Back navigation.
- [ ] Apply system-bar, display-cutout, and keyboard insets through AndroidX window APIs. Replace deprecated system-UI flag handling in `UIUtils`.
- [ ] Replace screen-height and status-bar heuristics in [EmojiPopup](../emoji/src/main/java/com/vanniktech/emoji/EmojiPopup.java) and [AndroidBug5497Workaround](../hipda/src/main/java/com/lichader/hipdanext/ui/widget/swipeback/AndroidBug5497Workaround.java) with inset-aware sizing.
- [ ] Check toolbars, lists, floating buttons, dialogs, image fullscreen mode, and emoji/composer placement with gesture and three-button navigation.

Android 16 enables predictive Back by default, changes legacy Back dispatch, and removes the edge-to-edge opt-out for apps targeting API 36 on Android 16+. See [Android 16 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16).

Exit criterion: navigation and input behavior pass on API 35, 36, and 37 without layout overlap or lost draft-confirmation behavior.

## Stage 6: Target SDK Checkpoints and Android 17

Advance the target through these checkpoints after the corresponding fixes. These are internal validation steps; they do not each require a published release.

| Target API | Main checks |
| --- | --- |
| 31 | Exported components, pending intents, notification taps, public links |
| 33 | Notification grant/denial, picker and storage behavior |
| 34 | Dependency behavior, intent restrictions, receiver registration |
| 35 | Edge-to-edge layouts and system-bar appearance |
| 36 | Predictive Back, keyboard insets, resizing |
| 37 | Large-screen constraints, keyboard restoration, HTTPS, image memory use |

For receiver registration, audit the actual broadcast types. The current `CONNECTIVITY_ACTION` receiver receives a system broadcast; it should not be treated as a confirmed violation of the export-flag requirement for non-system broadcasts. See [Android 14 receiver requirements](https://developer.android.com/about/versions/14/behavior-changes-14#runtime-receivers-exported).

Android 17 validation:

- [ ] Test tablets/foldables and split-screen resizing. API 37 removes the large-screen opt-out from orientation, aspect-ratio, and resizability changes; review `BaseActivity` orientation settings.
- [ ] Test login, browsing, image hosts, and uploads with default certificate-transparency enforcement. Diagnose TLS failures without bypassing certificate checks.
- [ ] Audit dependencies for reflection on `MessageQueue` internals or modification of `static final` fields. Current source inspection did not establish such usage.

These checks follow the [Android 17 target-specific changes](https://developer.android.com/about/versions/17/behavior-changes-17).

- [ ] Restore keyboard visibility deliberately after rotation where the composer requires it; Android 17 no longer automatically restores prior IME visibility after activity recreation.
- [ ] Profile long image-heavy threads and large uploads. `UploadImgHelper` decodes full images before thumbnail extraction; measure peak memory and downsample where appropriate.

See [Android 17 changes affecting all apps](https://developer.android.com/about/versions/17/behavior-changes-all) for keyboard restoration and app memory limits.

## Validation and Release Gate

Run these from the repository root once tooling and test dependencies are configured:

```sh
./gradlew :hipda:assembleDebug :hipda:assembleRelease
./gradlew :hipda:lintDebug :emoji:lintDebug
./gradlew :hipda:testDebugUnitTest :emoji:testDebugUnitTest
```

Configure an instrumentation runner before adding connected-device test commands to CI. A test task completing with no tests is not regression coverage. Review lint reports while `abortOnError false` remains configured, then make new serious findings fail CI.

- [ ] Test the lowest retained API, 31, 33, 34, 35, 36, and 37; include a tablet or foldable and both navigation modes on modern Android.
- [ ] Cover login/logout/account switching, thread lists/details, replies/new posts/polls, forum messages, images/GIFs, attachment downloads, notifications, themes, fonts, and emoji input.
- [ ] Exercise denied permissions, offline/reconnected networking, rotation, backgrounding, process recreation, and notification launches.
- [ ] Test an in-place upgrade from version 5.0.05 using matching signing credentials, preserving accounts, drafts, history, preferences, and scheduled work.
- [ ] Keep the `#.#.##` version-name format, or fix `UpdateHelper.newer()` first. It compares versions with the dots removed, so a name such as `5.1.0` would suppress the release-notes dialog and, on the following upgrade, reset the forum server and image host settings. See [TD-46](technical-debt/td-46-crash-handling-obsolete-code-and-setup-documentation-need-cleanup.md).
- [ ] Review backup/restore rules for encrypted preferences and cookies; verify failures do not silently expose credentials or discard user data.
- [ ] Validate the minified release artifact separately, update version metadata and release notes, and update contributor setup instructions to match the final toolchain.

Complete the migration only when the app compiles and targets API 37 and these build, upgrade, and device checks have recorded results.
