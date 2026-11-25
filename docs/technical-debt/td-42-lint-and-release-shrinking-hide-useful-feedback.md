# TD-42: Lint and release shrinking hide useful feedback

Category: Build, architecture, and product quality. Status: open.

**P2 · Maintenance.** The app disables lint failure and release checks. ProGuard keeps the entire app package, broad AndroidX types, and stale Matisse/Picasso rules, reducing shrinking/optimization and making dependency problems harder to assess. The default non-optimizing ProGuard file also needs migration.

Source: [hipda/build.gradle:21](../../hipda/build.gradle#L21), [proguard-rules.txt](../../hipda/proguard-rules.txt).

**Improve:** Baseline existing lint findings, fail new serious issues, and replace broad keep rules with tested requirements from active libraries. **Verify:** Compare APK size and verify a minified release, including Gson models, EventBus subscribers, Glide, and account restore.

[Review context, evidence definitions, and priorities](README.md).
