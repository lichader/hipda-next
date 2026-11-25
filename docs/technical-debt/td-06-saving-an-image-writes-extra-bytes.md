# TD-06: Saving an image writes extra bytes

Category: Correctness and user-visible behavior. Status: open.

**P1 · Probe-confirmed.** `UIUtils.saveImageQ()` writes the entire 1 KB buffer regardless of the number of bytes read, and writes again after EOF. The extracted production loop turned an empty input into 1,024 bytes, a 1-byte input into 2,048 bytes, and a 1,500-byte input into 3,072 bytes. The input stream is never closed.

User impact: the loop allocates a fresh zero-filled buffer on each pass, so the extra bytes are zero padding after the end of the image data. JPEG, PNG, and GIF decoders generally ignore trailing data, so saved images most likely still open. The saved file is still not a copy of the original: it is up to about 2 KB larger and fails byte comparison. It stays P1 because saving is expected to produce an exact copy, the fix is small, and Stage 4 of the [Android 17 migration plan](../android-17-migration.md) requires saved files to match their source bytes.

Source: [UIUtils.java:300](../../hipda/src/main/java/com/lichader/hipdanext/utils/UIUtils.java#L300).

**Improve:** Copy only the valid byte range, stop at EOF, and use structured resource cleanup. **Verify:** Compare saved output byte-for-byte for empty, short, exact-buffer, partial-buffer, and large inputs.

[Review context, evidence definitions, and priorities](README.md).
