# Android 17 Migration

Status: build migration done; device validation and some code follow-ups remain. The app compiles and targets API 37 (`compileSdk` and `targetSdk` 37, `minSdk` 21). Nothing has yet been checked on a device or emulator.

## Done

| Area | Change |
| --- | --- |
| Tooling | AGP 9 and Gradle 9 (versions in `AGENTS.md`), JDK 21 pinned through the Gradle daemon JVM criteria; CI builds debug and minified release APKs, runs unit tests and lint |
| AGP 8/9 DSL | Namespaces and version metadata in Gradle, `buildConfig` enabled, `proguard-android-optimize.txt`, menu `switch` on resource IDs replaced with `if` chains; release APK is `hipda-release-unsigned.apk` (the renaming hook used a removed variant API) |
| Release shrinking | `-dontwarn` for absent optional OkHttp TLS providers and Tink's errorprone annotations |
| Dependencies | AppCompat 1.7.0, Material 1.12.0, Preference 1.2.1, WorkManager 2.10.0 (`androidx.work`), OkHttp 4.12.0, ViewPump 2.1.0 |
| WorkManager | `WorkManager.getInstance(context)`; unique work name `noti_work` and the worker class unchanged |
| Manifest | Launcher activity and link alias exported; `IntentActivity` not exported; `POST_NOTIFICATIONS` declared; `WRITE_EXTERNAL_STORAGE` limited to API 28 |
| Notifications | `PendingIntent` uses `FLAG_IMMUTABLE` on API 23+; permission requested on Android 13+ when notifications are enabled |
| Storage | No storage permission check on API 29+ for downloads, save and share; `saveImageQ()` writes only the bytes read, closes streams, uses `IS_PENDING` and cleans up failed inserts |
| Back navigation | Activity Back handling uses `OnBackPressedDispatcher` callbacks in `BaseActivity`, `MainFrameActivity` and `ImageViewerActivity` |
| Edge-to-edge | On Android 15+, `BaseActivity` pads the content view with system bar, cutout and keyboard insets |
| Tests | Robolectric smoke tests in `hipda/src/test/` (see `AGENTS.md`) |

The application ID is `com.lichader.hipdanext`, so installs of the original `net.jejer.hipda.ng` build cannot upgrade in place. The upgrade checks below apply only to earlier `com.lichader.hipdanext` builds.

## Remaining Code Work

- [ ] Retire the hidden `convertFromTranslucent`/`convertToTranslucent` reflection in [SwipeUtils](../hipda/src/main/java/com/lichader/hipdanext/ui/widget/swipeback/SwipeUtils.java), reconcile swipe-back with predictive Back, and review the Back animation. See [swipe-back flash](swipe-back-flash.md).
- [ ] Replace deprecated `SYSTEM_UI_FLAG_*` handling in [UIUtils](../hipda/src/main/java/com/lichader/hipdanext/utils/UIUtils.java) with AndroidX window APIs; consider per-screen inset handling instead of the blanket `BaseActivity` padding.
- [ ] Replace the screen-height and status-bar heuristics in [EmojiPopup](../emoji/src/main/java/com/vanniktech/emoji/EmojiPopup.java) and [AndroidBug5497Workaround](../hipda/src/main/java/com/lichader/hipdanext/ui/widget/swipeback/AndroidBug5497Workaround.java) with inset-aware sizing.
- [ ] Narrow [FileProvider paths](../hipda/src/main/res/xml/provider_paths.xml), which still share the whole external storage root ([TD-04](technical-debt/td-04-fileprovider-permits-an-unnecessarily-broad-external-path.md)).
- [ ] Resolve the `tools:overrideLibrary="androidx.security"` minimum-SDK override in the [manifest](../hipda/src/main/AndroidManifest.xml) explicitly ([TD-02](technical-debt/td-02-credential-storage-silently-falls-back-to-plaintext.md)).
- [ ] Review the fixed orientations set in `BaseActivity`: API 37 ignores orientation, aspect-ratio and resizability restrictions on large screens.
- [ ] Restore keyboard visibility after rotation where the composer needs it; Android 17 no longer restores IME visibility after activity recreation.
- [ ] Downsample in [UploadImgHelper](../hipda/src/main/java/com/lichader/hipdanext/async/UploadImgHelper.java), which decodes full-resolution images for thumbnails ([TD-32](technical-debt/td-32-image-upload-decodes-full-resolution-images-for-thumbnails.md)), and profile memory on long image-heavy threads.
- [ ] Add parser fixtures and byte-for-byte file-copy regression tests (sizes that are not multiples of 1 KB). Keep account data and cookies out of fixtures.
- [ ] Decide whether to raise `minSdk`. WorkManager 2.11+ and hilt-work 1.3+ need API 23; raising it to 23 would also remove the API 21–22 plaintext-credential branch and the `FLAG_IMMUTABLE` API check.

## Release Gate

Device checks, none done yet:

- [ ] Test API 21 (or the agreed minimum), 31, 33, 34, 35, 36 and 37, including a tablet or foldable and both gesture and three-button navigation.
- [ ] Cover login/logout/account switching, thread lists and details, replies, new posts and polls, messages, images and GIFs, image upload/save/share, attachment downloads, notifications, themes, fonts and emoji input.
- [ ] Exercise denied permissions (including notifications), offline and reconnected networking, rotation, split-screen resizing, backgrounding, process death and notification launches.
- [ ] Check login, browsing, image hosts and uploads under Android 17's default certificate-transparency enforcement, without bypassing certificate checks.
- [ ] Check edge-to-edge layouts: toolbars, lists, floating buttons, dialogs, full-screen images and composer/emoji placement.
- [ ] Verify scheduled polling survives process death, reboot and an app upgrade without duplicate `noti_work` scheduling.

Before a release:

- [ ] Test an in-place upgrade from 5.0.05 with matching signing keys, keeping accounts, drafts, history, preferences and scheduled work.
- [ ] Keep the `#.#.##` version-name format, or fix `UpdateHelper.newer()` first: it compares versions with the dots removed, so `5.1.0` would suppress the release-notes dialog and, on the next upgrade, reset the forum server and image host settings ([TD-46](technical-debt/td-46-crash-handling-obsolete-code-and-setup-documentation-need-cleanup.md)).
- [ ] Review backup and restore rules for encrypted preferences and cookies ([TD-03](technical-debt/td-03-backup-rules-omit-encrypted-credentials-and-session-data.md)).
- [ ] Test the minified release APK separately, and update version metadata and release notes.

References: [Android 17 behavior changes for apps targeting it](https://developer.android.com/about/versions/17/behavior-changes-17), [changes affecting all apps](https://developer.android.com/about/versions/17/behavior-changes-all), [Android 16 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16).
