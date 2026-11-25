# TD-14: Click handling uses positions cached at bind time

Category: Correctness and user-visible behavior. Status: open.

**P2 · Risk.** `BaseRvAdapter` stores the adapter position in a view tag; `RecyclerItemClickListener` reads it later for taps and long presses. Insertions and removals can move an existing holder without rebinding it. Font/log deletion and thread prepending expose relevant mutation paths.

Source: [BaseRvAdapter.java:138](../../hipda/src/main/java/com/lichader/hipdanext/ui/adapter/BaseRvAdapter.java#L138), [RecyclerItemClickListener.java:56](../../hipda/src/main/java/com/lichader/hipdanext/ui/adapter/RecyclerItemClickListener.java#L56).

**Improve:** Resolve the holder's current binding position at click time and handle `NO_POSITION`, or bind actions to stable item identity. **Verify:** Delete/prepend rows, then tap a surviving visible row without forcing a rebind.

[Review context, evidence definitions, and priorities](README.md).
