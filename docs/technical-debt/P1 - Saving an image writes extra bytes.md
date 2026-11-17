# TD-06: Saving an image writes extra bytes

Category: Correctness and user-visible behavior. Status: open.

**P1 · Probe-confirmed.** `UIUtils.saveImageQ()` writes the entire 1 KB buffer regardless of the number of bytes read, and writes again after EOF. The extracted production loop turned a 1-byte input into 2,048 bytes and a 1,500-byte input into 3,072 bytes.

Source: [UIUtils.java:300](../../hipda/src/main/java/net/jejer/hipda/utils/UIUtils.java#L300).

**Improve:** Copy only the valid byte range, stop at EOF, and use structured resource cleanup. **Verify:** Compare saved output byte-for-byte for empty, short, exact-buffer, partial-buffer, and large inputs.

[Review context, evidence definitions, and priorities](README.md).
