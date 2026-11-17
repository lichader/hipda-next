# TD-27: One-shot executors are created without shutdown

Category: Lifecycle and background work. Status: open.

**P2 · Confirmed.** Login, notification delivery, settings maintenance, server configuration refresh, and report handling repeatedly create `newSingleThreadExecutor()` instances. No matching shutdown calls exist in the Java sources. Threads and captured UI state can remain after tasks finish.

Source: [MainFrameActivity.java:595](../../hipda/src/main/java/net/jejer/hipda/ui/MainFrameActivity.java#L595), [NotiHelper.java:159](../../hipda/src/main/java/net/jejer/hipda/service/NotiHelper.java#L159), [TaskHelper.java:83](../../hipda/src/main/java/net/jejer/hipda/async/TaskHelper.java#L83).

**Improve:** Use application-owned bounded executors with explicit operation lifetimes, or close genuinely temporary executors reliably. **Verify:** Repeat these actions and compare live thread counts and retained references.

[Review context, evidence definitions, and priorities](README.md).
