# TD-07: EXIF normalization falls through switch cases

Category: Correctness and user-visible behavior. Status: open.

**P1 · Probe-confirmed.** `CursorUtils.getOrientationFromExif()` has no breaks between the 90°, 180°, and 270° cases. Probes returned 270 for both EXIF 6 and EXIF 3. Actual compressor rotation has not been tested.

The larger effect is on direct upload. The probe covered the switch; the consequence below follows from source and still needs device confirmation. When an image has no orientation tag, the method returns `ORIENTATION_NORMAL` (1). That covers normally oriented photos, most PNGs, and GIFs, which carry no EXIF; AndroidX `ExifInterface` logs unsupported formats rather than throwing, so GIFs reach that default. Since commit `827b6e3c` (2022-07-10), `isDirectUploadable()` passes an image through unchanged only when its orientation is 0 or less. Almost no image is therefore uploaded unchanged:

- The original-image (原图) option has no effect; every image is recompressed.
- A GIF under the size limit is recompressed by `Compressor` into a still JPEG, so animation is lost.
- Combined with [TD-16](td-16-compressed-uploads-retain-the-source-mime-type.md), that JPEG is sent with the GIF file extension and MIME type.

Raised from P2 to P1 because GIF and original-image uploads are user-visible features.

Source: [CursorUtils.java:38](../../hipda/src/main/java/com/lichader/hipdanext/utils/CursorUtils.java#L38), [CursorUtils.java:45](../../hipda/src/main/java/com/lichader/hipdanext/utils/CursorUtils.java#L45), [UploadImgHelper.java:256](../../hipda/src/main/java/com/lichader/hipdanext/async/UploadImgHelper.java#L256).

**Improve:** Define one orientation representation, return explicitly for each case, and handle mirrored EXIF orientations deliberately. Base pass-through on an explicit "no rotation needed" result, so normally oriented images, untagged images, and GIFs qualify. Fix together with TD-16. **Verify:** Use real normal/rotated/mirrored fixtures and verify uploaded pixels. Upload a GIF under the size limit and confirm it stays animated; upload with the original-image option and confirm the uploaded bytes match the source.

[Review context, evidence definitions, and priorities](README.md).
