# TD-46: Crash handling, obsolete code, and setup documentation need cleanup

Category: Build, architecture, and product quality. Status: open.

**P3 · Maintenance.** The global exception handler starts a Looper thread without shutdown and blocks for two seconds before delegating. `Utils.restartActivity()` calls System.exit(0). There are unused source candidates such as ThreadImageDecoder, SerializableCookie, and SwipeBackActivity; the README references an absent Fabric example and removed camera permission.

The version comparison has a concrete trigger and should be fixed before the rest of this item. `UpdateHelper.newer()` removes the dots and compares the remaining integers, which works only while every version uses the `#.#.##` format. A release numbered `5.1.0` would compare as 510, lower than 5003 for 5.0.03 and 5005 for 5.0.05:

- Upgrading from 5.0.05 to 5.1.0 would not count as an update, so the release-notes dialog would not appear.
- On the next upgrade after 5.1.0, `updateApp()` would re-run the 5.0.03 migration and silently reset the forum server and image host settings.

Fix this before any release changes the version format; the [Android 17 migration plan](../android-17-migration.md) release gate tracks this.

Source: [SimpleExceptionHandler.java:20](../../hipda/src/main/java/com/lichader/hipdanext/utils/SimpleExceptionHandler.java#L20), [Utils.java:286](../../hipda/src/main/java/com/lichader/hipdanext/utils/Utils.java#L286), [README.md](../../README.md), [UpdateHelper.java:164](../../hipda/src/main/java/com/lichader/hipdanext/async/UpdateHelper.java#L164), [UpdateHelper.java:182](../../hipda/src/main/java/com/lichader/hipdanext/async/UpdateHelper.java#L182).

**Improve:** Compare versions by version code or by numeric segments. Use minimal crash logging with reliable delegation, recreate affected activities without process exit, verify unused code/resources before removal, and update build/release documentation. **Verify:** Test version comparisons including `5.0.05`, `5.1.0`, and `5.10.0`; force a controlled crash, test settings-triggered recreation, and follow setup instructions on a fresh machine.

[Review context, evidence definitions, and priorities](README.md).
