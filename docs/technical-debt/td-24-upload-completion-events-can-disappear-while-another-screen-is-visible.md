# TD-24: Upload completion events can disappear while another screen is visible

Category: Lifecycle and background work. Status: open.

**P2 · Confirmed.** `ImageUploadJob.itemComplete()` sends a nonsticky event whenever any app activity is visible. `PostFragment` unregisters in `onPause()`. If that subscriber is absent while another screen is visible, the event is neither received nor added to `mHoldEvents`; the final event cannot replay it.

Source: [ImageUploadJob.java:61](../../hipda/src/main/java/com/lichader/hipdanext/job/ImageUploadJob.java#L61), [PostFragment.java:325](../../hipda/src/main/java/com/lichader/hipdanext/ui/PostFragment.java#L325), [PostFragment.java:851](../../hipda/src/main/java/com/lichader/hipdanext/ui/PostFragment.java#L851).

**Improve:** Store upload results by operation/session independently of visibility, then let lifecycle-aware observers consume state. **Verify:** Pause the composer while upload completes and resume it with every uploaded attachment available.

[Review context, evidence definitions, and priorities](README.md).
