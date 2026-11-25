# TD-17: Failed uploads bypass temporary-file cleanup

Category: Correctness and user-visible behavior. Status: open.

**P2 · Confirmed.** Temporary files are created before validation, but cleanup surrounds only the later network-post block. Parse failures, oversized GIFs, and compression failures return earlier. The compressed-file field also survives between items. Daily cleanup deletes the entire upload directory without checking active ownership.

Source: [UploadImgHelper.java:104](../../hipda/src/main/java/com/lichader/hipdanext/async/UploadImgHelper.java#L104), [Utils.java:312](../../hipda/src/main/java/com/lichader/hipdanext/utils/Utils.java#L312), [TaskHelper.java:43](../../hipda/src/main/java/com/lichader/hipdanext/async/TaskHelper.java#L43).

**Improve:** Give each upload scoped temporary artifacts and cleanup covering its entire lifetime; make periodic cleanup age/ownership-aware. **Verify:** Exercise every early-return path and run cleanup while an upload is active.

[Review context, evidence definitions, and priorities](README.md).
