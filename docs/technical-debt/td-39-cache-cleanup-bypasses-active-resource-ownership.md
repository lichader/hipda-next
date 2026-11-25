# TD-39: Cache cleanup bypasses active resource ownership

Category: Storage, navigation, and platform compatibility. Status: open.

**P2 · Risk.** `clearOkhttpCache()` deletes the cache directory while the singleton client retains its Cache instance. Image cache deletion can leave in-memory metadata stale. `ImageViewerActivity.onDestroy()` schedules deletion of every shared-image temporary file, potentially before another app has finished reading a granted URI.

Source: [Utils.java:295](../../hipda/src/main/java/com/lichader/hipdanext/utils/Utils.java#L295), [Utils.java:378](../../hipda/src/main/java/com/lichader/hipdanext/utils/Utils.java#L378), [ImageViewerActivity.java:202](../../hipda/src/main/java/com/lichader/hipdanext/ui/ImageViewerActivity.java#L202).

**Improve:** Use cache APIs and coordinated invalidation; retain shared files long enough for recipients and clean them by age/ownership. **Verify:** Clear caches during requests and share to a delayed reader before closing the viewer.

[Review context, evidence definitions, and priorities](README.md).
