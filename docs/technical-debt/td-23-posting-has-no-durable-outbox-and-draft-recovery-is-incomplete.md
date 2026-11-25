# TD-23: Posting has no durable outbox and draft recovery is incomplete

Category: Lifecycle and background work. Status: open.

**P1 · Risk.** `PostFragment` enqueues a nonpersistent job and immediately closes the composer. Process death can discard submission work. Draft persistence stores body text and username, but not subject, target thread/forum, upload references, or pending-send state; fragment/session identity is primarily in memory.

Source: [BaseJob.java:17](../../hipda/src/main/java/com/lichader/hipdanext/job/BaseJob.java#L17), [PostFragment.java:505](../../hipda/src/main/java/com/lichader/hipdanext/ui/PostFragment.java#L505), [ContentDBHelper.java:18](../../hipda/src/main/java/com/lichader/hipdanext/db/ContentDBHelper.java#L18).

**Improve:** Persist complete drafts and explicit outbox states before closing the composer. Define reconciliation for ambiguous submission outcomes rather than blindly resending. **Verify:** Kill the process before, during, and after submission and recover drafts without losing images or duplicating posts.

[Review context, evidence definitions, and priorities](README.md).
