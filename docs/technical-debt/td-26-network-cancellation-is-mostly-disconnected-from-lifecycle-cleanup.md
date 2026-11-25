# TD-26: Network cancellation is mostly disconnected from lifecycle cleanup

Category: Lifecycle and background work. Status: open.

**P2 · Confirmed.** `ImageStreamFetcher.cancel()` and `AvatarStreamFetcher.cancel()` are empty and do not retain their OkHttp Call for cancellation. `BaseImageLayout` detachment clears its visible Glide target, but its separate job-queue download is not canceled. The OkHttp tag-cancellation helper has no source call sites.

Source: [ImageStreamFetcher.java](../../hipda/src/main/java/net/jejer/hipda/glide/ImageStreamFetcher.java), [AvatarStreamFetcher.java:128](../../hipda/src/main/java/net/jejer/hipda/glide/AvatarStreamFetcher.java#L128), [BaseImageLayout.java:183](../../hipda/src/main/java/net/jejer/hipda/ui/widget/BaseImageLayout.java#L183).

**Improve:** Define explicit request ownership/cancellation and deduplicate downloads; keep background work only where its lifecycle is intentional. **Verify:** Navigate away on a slow connection and verify obsolete requests stop without canceling shared work still in use.

[Review context, evidence definitions, and priorities](README.md).
