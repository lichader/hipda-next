# TD-41: There is no automated regression suite or CI

Category: Build, architecture, and product quality. Status: open.

**P1 · Maintenance.** Neither module has test source sets, test dependencies, or an instrumentation runner, and the repository has no CI configuration. Parsing, account switching, byte copying, and posting state currently have no automated safety net.

Source: [hipda/build.gradle](../../hipda/build.gradle), [emoji/build.gradle](../../emoji/build.gradle).

**Improve:** Add focused tests for the confirmed defects first, then parser fixtures, fake-server networking tests, database migrations, and a small device smoke suite. Add CI that runs them. **Verify:** Demonstrate that regression tests fail on the original defects, then pass after fixes; avoid treating NO-SOURCE test tasks as coverage.

[Review context, evidence definitions, and priorities](README.md).
