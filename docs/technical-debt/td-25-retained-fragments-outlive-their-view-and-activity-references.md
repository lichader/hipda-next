# TD-25: Retained fragments outlive their view and activity references

Category: Lifecycle and background work. Status: open.

**P2 · Risk.** `BaseFragment` uses `setRetainInstance(true)`, keeps UI references, and cleans the emoji popup in `onDestroy()` rather than `onDestroyView()`. Jobs and AsyncTasks retain supplied Activity contexts, while delayed callbacks capture fragments/views. New views can replace references before old listeners or dialogs are cleaned.

Source: [BaseFragment.java:101](../../hipda/src/main/java/com/lichader/hipdanext/ui/BaseFragment.java#L101), [BaseFragment.java:126](../../hipda/src/main/java/com/lichader/hipdanext/ui/BaseFragment.java#L126), [ThreadDetailJob.java:38](../../hipda/src/main/java/com/lichader/hipdanext/job/ThreadDetailJob.java#L38).

**Improve:** Move retained state into ViewModels/repositories, clear view resources at view destruction, and use application context for non-UI work. **Verify:** Rotate, fold/unfold, navigate away during requests, and inspect retained activities and stale callbacks.

[Review context, evidence definitions, and priorities](README.md).
