# TD-22: Jobs and caches are not scoped to an account generation

Category: Lifecycle and background work. Status: open.

**P1 · Risk.** Account switching replaces global settings and clears the shared cookie store. Queued posting/upload/read jobs have screen session IDs but no immutable account identity, and some requests/preflight values are obtained later from global state. Outstanding work or cached responses can cross a switch boundary.

Source: [MainFrameActivity.java:548](../../hipda/src/main/java/com/lichader/hipdanext/ui/MainFrameActivity.java#L548), [LoginHelper.java:154](../../hipda/src/main/java/com/lichader/hipdanext/async/LoginHelper.java#L154), [PostJob.java:26](../../hipda/src/main/java/com/lichader/hipdanext/job/PostJob.java#L26).

**Improve:** Capture account identity and an authentication generation in each operation, cancel/discard stale work, and define account boundaries for caches and results. **Verify:** Switch accounts during queued requests, uploads, and submissions using test accounts; verify the submitting identity and displayed data.

[Review context, evidence definitions, and priorities](README.md).
