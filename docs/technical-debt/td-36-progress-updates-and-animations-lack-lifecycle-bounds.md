# TD-36: Progress updates and animations lack lifecycle bounds

Category: Images, memory, and caching. Status: open.

**P2 · Confirmed.** Every network read publishes an EventBus progress update; unknown content lengths are still divided into a percentage. `DownloadProgressBar` creates an infinite ValueAnimator, but finish/error/idle transitions and detachment do not consistently end it. Detached or completed views can retain animation work.

Source: [MyGlideModule.java:81](../../hipda/src/main/java/com/lichader/hipdanext/glide/MyGlideModule.java#L81), [DownloadProgressBar.java:386](../../hipda/src/main/java/com/lichader/hipdanext/ui/widget/DownloadProgressBar.java#L386), [DownloadProgressBar.java:760](../../hipda/src/main/java/com/lichader/hipdanext/ui/widget/DownloadProgressBar.java#L760).

**Improve:** Throttle progress, use indeterminate state for missing lengths, and stop/restart animators according to state and attachment. **Verify:** Test chunked responses and repeated attach/detach while measuring UI callbacks and active animators.

[Review context, evidence definitions, and priorities](README.md).
