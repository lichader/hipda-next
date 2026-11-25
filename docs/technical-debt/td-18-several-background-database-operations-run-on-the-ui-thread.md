# TD-18: Several 'background' database operations run on the UI thread

Category: Persistence and input validation. Status: open.

**P2 · Confirmed.** `HistoryDao.saveHistoryInBackground()` posts to the caller's looper; its UI call sites therefore execute SQLite work on the main thread. Draft saving in `PostFragment` uses the same pattern, and restoring drafts queries synchronously from a click handler.

Source: [HistoryDao.java:23](../../hipda/src/main/java/com/lichader/hipdanext/db/HistoryDao.java#L23), [PostFragment.java:278](../../hipda/src/main/java/com/lichader/hipdanext/ui/PostFragment.java#L278), [PostFragment.java:557](../../hipda/src/main/java/com/lichader/hipdanext/ui/PostFragment.java#L557).

**Improve:** Use a bounded database executor or asynchronous DAO layer, taking immutable text snapshots before leaving the UI thread. **Verify:** Use StrictMode and a slow-storage test while typing, navigating, and restoring drafts.

[Review context, evidence definitions, and priorities](README.md).
