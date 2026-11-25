# TD-27: One-shot executors are created without shutdown

Category: Lifecycle and background work. Status: open.

**P3 · Confirmed.** Login, notification delivery, settings maintenance, server configuration refresh, and report handling create a new `newSingleThreadExecutor()` instance on each call, at six call sites. No matching shutdown calls exist in the Java sources.

The consequence is smaller than a permanent leak. In both the Android and OpenJDK implementations, the executor returned by `newSingleThreadExecutor()` shuts down its pool when it is garbage-collected, and a worker thread does not keep a reference to a completed task. Each call therefore leaves an idle thread alive until garbage collection; UI state captured by a task is held only while that task runs. Lowered from P2 to P3 for that reason.

Source: [MainFrameActivity.java:596](../../hipda/src/main/java/com/lichader/hipdanext/ui/MainFrameActivity.java#L596), [NotiHelper.java:160](../../hipda/src/main/java/com/lichader/hipdanext/service/NotiHelper.java#L160), [TaskHelper.java:83](../../hipda/src/main/java/com/lichader/hipdanext/async/TaskHelper.java#L83), [ThreadDetailFragment.java:1034](../../hipda/src/main/java/com/lichader/hipdanext/ui/ThreadDetailFragment.java#L1034), [SettingNestedFragment.java:339](../../hipda/src/main/java/com/lichader/hipdanext/ui/setting/SettingNestedFragment.java#L339), [SettingNestedFragment.java:380](../../hipda/src/main/java/com/lichader/hipdanext/ui/setting/SettingNestedFragment.java#L380).

**Improve:** Use application-owned bounded executors with explicit operation lifetimes, or close genuinely temporary executors reliably. **Verify:** Repeat these actions and compare live thread counts before and after a forced garbage collection.

[Review context, evidence definitions, and priorities](README.md).
