# TD-28: Notification work reports success regardless of failure

Category: Lifecycle and background work. Status: open.

**P2 · Confirmed.** `NotiWorker.doWork()` catches exceptions and returns success. `NotiHelper.fetchNotification()` also swallows errors, and notification delivery starts another executor rather than completing within the worker's lifetime. WorkManager cannot observe the full result or apply an appropriate retry policy.

Source: [NotiWorker.java:39](../../hipda/src/main/java/net/jejer/hipda/service/NotiWorker.java#L39), [NotiHelper.java:94](../../hipda/src/main/java/net/jejer/hipda/service/NotiHelper.java#L94).

**Improve:** Return explicit fetch/delivery outcomes, await owned work, distinguish temporary failure from login/permission problems, and configure backoff. **Verify:** Test offline/timeouts, revoked notification permission, expired login, process stop, and successful retry.

[Review context, evidence definitions, and priorities](README.md).
