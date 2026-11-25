# TD-44: UI, persistence, parsing, and account state are tightly coupled

Category: Build, architecture, and product quality. Status: open.

**P3 · Maintenance.** ThreadDetailFragment has 1,601 lines, HiSettingsHelper 1,139, and MainFrameActivity 960. UI classes coordinate network jobs, static state, parsing outcomes, drafts, dialogs, and navigation. Multiple asynchronous mechanisms make result ownership difficult to track; several parsers mutate settings or launch notification work as side effects.

Source: [ThreadDetailFragment.java](../../hipda/src/main/java/com/lichader/hipdanext/ui/ThreadDetailFragment.java), [HiSettingsHelper.java](../../hipda/src/main/java/com/lichader/hipdanext/bean/HiSettingsHelper.java), [HiParserThreadList.java:21](../../hipda/src/main/java/com/lichader/hipdanext/utils/HiParserThreadList.java#L21).

**Improve:** Extract account/session, forum repository, parser, draft/outbox, and image responsibilities incrementally. Keep parsers pure and expose immutable state through lifecycle-aware models. **Verify:** Move one responsibility at a time behind behavior tests; preserve existing Java/XML flows.

[Review context, evidence definitions, and priorities](README.md).
