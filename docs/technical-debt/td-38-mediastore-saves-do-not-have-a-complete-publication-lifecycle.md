# TD-38: MediaStore saves do not have a complete publication lifecycle

Category: Storage, navigation, and platform compatibility. Status: open.

**P2 · Risk.** Beyond the copy defect, `saveImageQ()` assumes insert/openOutputStream succeed, does not use IS_PENDING, and does not remove failed insertions. Save/copy work may also run on the UI thread after the download task finishes.

Source: [UIUtils.java:274](../../hipda/src/main/java/com/lichader/hipdanext/utils/UIUtils.java#L274).

**Improve:** Write on an I/O executor, check resolver results, publish completed media, and delete incomplete rows on failure. **Verify:** Simulate full storage, failed/null resolver operations, cancellation, and large saves; ensure galleries never see partial media.

[Review context, evidence definitions, and priorities](README.md).
