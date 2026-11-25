# TD-12: Thread parsing mixes node counts with element indexing

Category: Correctness and user-visible behavior. Status: open.

**P2 · Probe-confirmed.** `HiParserThreadDetail` loops to `childNodeSize()` but reads `child(i)`, which indexes only element children. A Jsoup 1.11.3 fixture containing one post with surrounding newlines has three nodes and one element; the iteration throws `IndexOutOfBoundsException`. The job catches parsing exceptions, so the impact would be a failed thread load.

This is a latent fragility, not a current failure. Thread pages load in the released app, so the forum's current markup evidently has no text or comment nodes directly inside `div#postlist`. A server-side markup change that adds whitespace or comments there would break every thread load. The same assumption appears at line 145, which passes `postsEL.childNodeSize()` to `setMaxPostsInPage()` and would miscount posts per page after the same change.

Source: [HiParserThreadDetail.java:106](../../hipda/src/main/java/net/jejer/hipda/utils/HiParserThreadDetail.java#L106), [HiParserThreadDetail.java:145](../../hipda/src/main/java/net/jejer/hipda/utils/HiParserThreadDetail.java#L145).

**Improve:** Iterate element children directly, filter post elements, and keep pagination counts independent of whitespace/comment nodes. **Verify:** Parse minified, formatted, commented, missing-element, and error-page fixtures; check the recorded posts-per-page value for each.

[Review context, evidence definitions, and priorities](README.md).
