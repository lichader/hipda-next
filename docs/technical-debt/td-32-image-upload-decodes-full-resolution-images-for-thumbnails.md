# TD-32: Image upload decodes full-resolution images for thumbnails

Category: Images, memory, and caching. Status: open.

**P2 · Risk.** `UploadImgHelper` loads the complete file into a byte array, decodes a full bitmap, extracts a small thumbnail, and later copies upload bytes again. Small compressed files can still have huge pixel dimensions. Null decodes and unsupported formats also need deliberate handling.

Source: [UploadImgHelper.java:131](../../hipda/src/main/java/com/lichader/hipdanext/async/UploadImgHelper.java#L131).

**Improve:** Decode sampled thumbnails, cap pixel counts, stream file-backed multipart bodies, and bound concurrent image processing. **Verify:** Profile large-dimension images, GIFs, corrupt inputs, and multi-select uploads on a low-memory device.

[Review context, evidence definitions, and priorities](README.md).
