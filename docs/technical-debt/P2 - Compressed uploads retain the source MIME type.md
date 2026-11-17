# TD-16: Compressed uploads retain the source MIME type

Category: Correctness and user-visible behavior. Status: open.

**P2 · Confirmed.** Compression writes JPEG, but `postImage()` still derives the extension and multipart media type from the original `ImageFileInfo`. A compressed PNG can therefore be sent with PNG metadata and JPEG bytes.

Source: [UploadImgHelper.java:188](../../hipda/src/main/java/net/jejer/hipda/async/UploadImgHelper.java#L188), [UploadImgHelper.java:230](../../hipda/src/main/java/net/jejer/hipda/async/UploadImgHelper.java#L230).

**Improve:** Carry metadata for the actual upload artifact, including its format and size; reset per-image state between selections. **Verify:** Capture multipart requests for compressed PNG/JPEG and mixed multi-image uploads.

[Review context, evidence definitions, and priorities](README.md).
