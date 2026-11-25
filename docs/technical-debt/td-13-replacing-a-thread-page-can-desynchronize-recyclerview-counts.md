# TD-13: Replacing a thread page can desynchronize RecyclerView counts

Category: Correctness and user-visible behavior. Status: open.

**P2 · Confirmed.** When an existing page is replaced, `ThreadDetailAdapter.addDatas()` updates the total count but only calls `notifyItemRangeChanged()` using the new page size. A different number of posts requires structural insert/remove notifications. Same-object mutations are skipped, even if the underlying contents changed.

Source: [ThreadDetailAdapter.java:149](../../hipda/src/main/java/com/lichader/hipdanext/ui/adapter/ThreadDetailAdapter.java#L149).

**Improve:** Use immutable page snapshots with diffing, or emit correct structural updates from old and new counts. **Verify:** Refresh a page after post deletion/insertion and while a neighboring page is visible.

[Review context, evidence definitions, and priorities](README.md).
