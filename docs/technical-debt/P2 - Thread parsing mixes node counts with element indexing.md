# TD-12: Thread parsing mixes node counts with element indexing

Category: Correctness and user-visible behavior. Status: open.

**P2 · Probe-confirmed.** `HiParserThreadDetail` loops to `childNodeSize()` but reads `child(i)`, which indexes only element children. A Jsoup 1.11.3 fixture containing one post with surrounding newlines has three nodes and one element; the iteration throws `IndexOutOfBoundsException`. The job catches parsing exceptions, so the expected impact is a failed thread load.

Source: [HiParserThreadDetail.java:101](../../hipda/src/main/java/net/jejer/hipda/utils/HiParserThreadDetail.java#L101).

**Improve:** Iterate element children directly, filter post elements, and keep pagination counts independent of whitespace/comment nodes. **Verify:** Parse minified, formatted, commented, missing-element, and error-page fixtures.

[Review context, evidence definitions, and priorities](README.md).
