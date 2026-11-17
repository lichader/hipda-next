# TD-40: The target-30 build needs a staged platform upgrade

Category: Storage, navigation, and platform compatibility. Status: open.

**P1 · Migration.** AGP 7.2.2, Gradle 7.3.3, and compile/target 30 are behind the intended Android 17 target. Missing namespaces, non-final resource-ID switch cases, BuildConfig generation, old variant/ProGuard APIs, exported declarations, PendingIntent mutability, and notification permissions all need review when upgrading. These are not all current target-30 runtime failures.

Source: [build.gradle](../../build.gradle), [hipda/build.gradle](../../hipda/build.gradle), [AndroidManifest.xml](../../hipda/src/main/AndroidManifest.xml).

**Improve:** Follow the [Android 17 migration plan](../android-17-migration.md), choosing the minimum supported OS before updating dependencies. **Verify:** Build debug/release at each tooling/target checkpoint and run the documented device matrix.

[Review context, evidence definitions, and priorities](README.md).
