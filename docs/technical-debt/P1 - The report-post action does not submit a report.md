# TD-08: The report-post action does not submit a report

Category: Correctness and user-visible behavior. Status: open.

**P1 · Confirmed.** `showReportDialog()` accepts a reason, then starts an executor that only sleeps for a random interval and displays a thank-you message. It never submits the reason or post identity to the server.

Source: [ThreadDetailFragment.java:999](../../hipda/src/main/java/net/jejer/hipda/ui/ThreadDetailFragment.java#L999).

**Improve:** Implement the forum report workflow with confirmed server results, or direct users to a working browser report flow and clearly represent availability. **Verify:** Verify a real submission or explicit fallback, plus server rejection, network failure, and cancellation.

[Review context, evidence definitions, and priorities](README.md).
