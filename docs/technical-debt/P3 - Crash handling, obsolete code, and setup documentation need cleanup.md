# TD-46: Crash handling, obsolete code, and setup documentation need cleanup

Category: Build, architecture, and product quality. Status: open.

**P3 · Maintenance.** The global exception handler starts a Looper thread without shutdown and blocks for two seconds before delegating. `Utils.restartActivity()` calls System.exit(0). There are unused source candidates such as ThreadImageDecoder, SerializableCookie, and SwipeBackActivity; the README references an absent Fabric example and removed camera permission. The actual app version comparator also relies on stripping dots rather than version-code ordering.

Source: [SimpleExceptionHandler.java:20](../../hipda/src/main/java/net/jejer/hipda/utils/SimpleExceptionHandler.java#L20), [Utils.java:286](../../hipda/src/main/java/net/jejer/hipda/utils/Utils.java#L286), [README.md](../../README.md), [UpdateHelper.java:164](../../hipda/src/main/java/net/jejer/hipda/async/UpdateHelper.java#L164).

**Improve:** Use minimal crash logging with reliable delegation, recreate affected activities without process exit, verify unused code/resources before removal, and update build/release documentation. **Verify:** Force a controlled crash, test settings-triggered recreation and version comparisons, and follow setup instructions on a fresh machine.

[Review context, evidence definitions, and priorities](README.md).
