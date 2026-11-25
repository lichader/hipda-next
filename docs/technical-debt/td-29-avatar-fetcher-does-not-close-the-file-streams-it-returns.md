# TD-29: Avatar fetcher does not close the file streams it returns

Category: Images, memory, and caching. Status: open.

**P2 · Confirmed.** `AvatarStreamFetcher` constructs `FileInputStream` objects for callbacks but stores only the HTTP response body. Its cleanup method therefore cannot close the returned file stream. Glide assigns resource cleanup to the fetcher. [DataFetcher contract](https://bumptech.github.io/glide/javadocs/490/com/bumptech/glide/load/data/DataFetcher.html). `GlideHelper.initDefaultFiles()` also creates output streams without explicitly closing them.

Source: [AvatarStreamFetcher.java:73](../../hipda/src/main/java/net/jejer/hipda/glide/AvatarStreamFetcher.java#L73), [GlideHelper.java:136](../../hipda/src/main/java/net/jejer/hipda/glide/GlideHelper.java#L136).

**Improve:** Retain and close the fetched stream; use try-with-resources for default-avatar files and atomic replacement for cached avatars. **Verify:** Scroll through many unique avatars and monitor descriptors; interrupt avatar writes without leaving partial cache entries.

[Review context, evidence definitions, and priorities](README.md).
