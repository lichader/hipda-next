# TD-08: The report-post action does not submit a report

Category: Correctness and user-visible behavior. Status: open.

**P1 · Confirmed.** `showReportDialog()` accepts a reason, then starts an executor that only sleeps for a random interval of about 1.2–3.3 seconds and displays a thank-you message (感谢您的反馈). It never submits the reason or post identity to the server. Users are told a report was sent when it was not.

This is the original design, not a regression. The commit that introduced the feature (`d72875ad`, "report post", 2021-10-20) already contained the random sleep and had no submission path; it does not record why. Resolving this is therefore a product decision before it is an implementation task: implement the forum's report workflow, send users to the web report flow, or remove the action.

Source: [ThreadDetailFragment.java:999](../../hipda/src/main/java/com/lichader/hipdanext/ui/ThreadDetailFragment.java#L999).

**Improve:** Record the product decision, then implement the forum report workflow with confirmed server results, direct users to a working browser report flow, or remove the action; in every case, represent availability accurately. **Verify:** Verify a real submission or explicit fallback, plus server rejection, network failure, and cancellation.

[Review context, evidence definitions, and priorities](README.md).
