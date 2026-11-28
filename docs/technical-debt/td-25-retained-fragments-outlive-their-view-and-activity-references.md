# TD-25: Retained fragments outlive their view and activity references

Category: Lifecycle and background work. Status: open, partly addressed.

**P2 · Risk.** `BaseFragment` uses `setRetainInstance(true)`, keeps UI references, and cleans the emoji popup in `onDestroy()` rather than `onDestroyView()`. Jobs and AsyncTasks retain supplied Activity contexts, while delayed callbacks capture fragments/views. New views can replace references before old listeners or dialogs are cleaned.

Source: [BaseFragment.java:101](../../hipda/src/main/java/com/lichader/hipdanext/ui/BaseFragment.java#L101), [BaseFragment.java:126](../../hipda/src/main/java/com/lichader/hipdanext/ui/BaseFragment.java#L126), [ThreadDetailJob.java:38](../../hipda/src/main/java/com/lichader/hipdanext/job/ThreadDetailJob.java#L38).

**Improve:** Move retained state into ViewModels/repositories, clear view resources at view destruction, and use application context for non-UI work. **Verify:** Rotate, fold/unfold, navigate away during requests, and inspect retained activities and stale callbacks.

**Update 2026-11-28:** `BaseFragment` no longer retains fragments. Retention had also crashed every Hilt fragment on configuration changes; see W1 in the [layering plan](../architecture-layering-plan.md#w1-fix-the-app-wide-crash-on-configuration-changes). A configuration change now creates new fragment instances, which keep their session ID through saved state, and `ActivityLaunchTest` recreates every screen. Still open: emoji popup cleanup in `onDestroy()`, jobs and AsyncTasks holding Activity contexts, and delayed callbacks that capture fragments or views.

[Review context, evidence definitions, and priorities](README.md).
