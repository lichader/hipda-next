# TD-31: Image metadata cannot recover from a stale successful state

Category: Images, memory, and caching. Status: open.

**P2 · Probe-confirmed.** `ImageInfo.setStatus()` ignores every update after SUCCESS. The actual class remained at state 3 after requests to reset to IDLE and FAIL. Image metadata also survives deletion/eviction of its backing Glide file, so cached paths can remain successful even when their files are gone. Compound get/create/reset operations in `ImageContainer` are not atomic.

Source: [ImageInfo.java:135](../../hipda/src/main/java/net/jejer/hipda/cache/ImageInfo.java#L135), [ImageContainer.java](../../hipda/src/main/java/net/jejer/hipda/cache/ImageContainer.java).

**Improve:** Define valid state transitions, validate backing files, invalidate metadata with disk caches, and make per-URL state updates atomic. **Verify:** Clear/evict disk images while viewing threads, then reopen and retry them; stress parallel requests for the same URL.

[Review context, evidence definitions, and priorities](README.md).
