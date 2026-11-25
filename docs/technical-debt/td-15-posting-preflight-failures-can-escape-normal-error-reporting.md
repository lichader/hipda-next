# TD-15: Posting preflight failures can escape normal error reporting

Category: Correctness and user-visible behavior. Status: open.

**P2 · Confirmed.** `PostHelper` creates one `mPrePostAsyncTask` but runs different instances when fetching missing preflight data. Its later error lookup therefore uses the wrong task. In quote/reply-post modes it also dereferences `mInfo.getQuoteText()` before the null check inside `doPost()`. A failed preflight can throw into a job whose cancellation callback is empty.

Source: [PostHelper.java:74](../../hipda/src/main/java/com/lichader/hipdanext/async/PostHelper.java#L74), [BaseJob.java:33](../../hipda/src/main/java/com/lichader/hipdanext/job/BaseJob.java#L33).

**Improve:** Return a typed preflight result, stop safely on failure, and publish a terminal failure for every posting job. **Verify:** Force all preflight attempts to fail in each posting mode; preserve the draft and show a useful error.

[Review context, evidence definitions, and priorities](README.md).
