# TD-33: Image geometry is cached as portrait-sized global state

Category: Images, memory, and caching. Status: open.

**P2 · Confirmed.** `Utils.getScreenWidth()/getScreenHeight()` cache the first display dimensions and force width=min/height=max. `ImageInfo` then stores layout limits in static final fields. These values do not follow activity window resizing, landscape, split screen, or folded/unfolded changes.

Source: [Utils.java:262](../../hipda/src/main/java/net/jejer/hipda/utils/Utils.java#L262), [ImageInfo.java:13](../../hipda/src/main/java/net/jejer/hipda/cache/ImageInfo.java#L13).

**Improve:** Size images from the current container/window and recompute constraints on layout/configuration changes. **Verify:** Open an image-heavy thread before and after folding, rotation, and split-screen resizing without restarting the process.

[Review context, evidence definitions, and priorities](README.md).
