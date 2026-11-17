# TD-07: EXIF normalization falls through switch cases

Category: Correctness and user-visible behavior. Status: open.

**P2 · Probe-confirmed.** `CursorUtils.getOrientationFromExif()` has no breaks between the 90°, 180°, and 270° cases. Probes returned 270 for both EXIF 6 and EXIF 3. Normal orientation remains EXIF value 1, while `isDirectUploadable()` expects a nonpositive rotation for pass-through, causing normal originals to fail that check. Actual compressor rotation has not been tested.

Source: [CursorUtils.java:44](../../hipda/src/main/java/net/jejer/hipda/utils/CursorUtils.java#L44), [UploadImgHelper.java:250](../../hipda/src/main/java/net/jejer/hipda/async/UploadImgHelper.java#L250).

**Improve:** Define one orientation representation, return explicitly for each case, and handle mirrored EXIF orientations deliberately. **Verify:** Use real normal/rotated/mirrored fixtures and verify both uploaded pixels and original-file pass-through.

[Review context, evidence definitions, and priorities](README.md).
