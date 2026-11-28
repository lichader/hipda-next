# TD-08: The report-post action does not submit a report

Category: Correctness and user-visible behavior. Status: closed (2026-11-28, action removed).

**P1 · Confirmed.** `showReportDialog()` accepts a reason, then starts an executor that only sleeps for a random interval of about 1.2–3.3 seconds and displays a thank-you message (感谢您的反馈). It never submits the reason or post identity to the server. Users are told a report was sent when it was not.

This is the original design, not a regression. The commit that introduced the feature (`d72875ad`, "report post", 2021-10-20) already contained the random sleep and had no submission path; it does not record why. Resolving this is therefore a product decision before it is an implementation task: implement the forum's report workflow, send users to the web report flow, or remove the action.

Source: [ThreadDetailFragment.java:999](../../hipda/src/main/java/com/lichader/hipdanext/ui/ThreadDetailFragment.java#L999).

**Improve:** Record the product decision, then implement the forum report workflow with confirmed server results, direct users to a working browser report flow, or remove the action; in every case, represent availability accurately. **Verify:** Verify a real submission or explicit fallback, plus server rejection, network failure, and cancellation.

**Resolution (2026-11-28):** the report action was removed (decision D3 in the [layering plan](../architecture-layering-plan.md#next-work)): its dialog, its menu button and their resources. The button had been hidden since 2021-10-28 (`19705139`), eight days after the feature was added, so users could not reach it; removing it changes nothing on screen. Adding a working report feature is tracked as a task in the owner's "Hi-pda next v2" GitHub project. A working version must submit to the forum and show the server's result. Testing it sends real reports to moderators, so it needs a deliberate test report by the owner.

[Review context, evidence definitions, and priorities](README.md).
