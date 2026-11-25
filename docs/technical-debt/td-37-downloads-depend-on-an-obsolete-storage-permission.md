# TD-37: Downloads depend on an obsolete storage permission

Category: Storage, navigation, and platform compatibility. Status: open.

**P2 · Confirmed.** `Utils.download()` always requests WRITE_EXTERNAL_STORAGE before using DownloadManager. That permission provides no additional write access for apps targeting API 30 on modern Android, so the gate can block a download it should allow. Image selection already uses content URIs and should retain that permission-free flow.

Source: [Utils.java:529](../../hipda/src/main/java/net/jejer/hipda/utils/Utils.java#L529), [UIUtils.java:157](../../hipda/src/main/java/net/jejer/hipda/utils/UIUtils.java#L157).

**Improve:** Restrict legacy permissions to older OS operations and use DownloadManager, MediaStore, and system pickers on current Android. **Verify:** Download authenticated attachments and select images with storage permissions denied on current Android and on the oldest supported version.

[Review context, evidence definitions, and priorities](README.md).
