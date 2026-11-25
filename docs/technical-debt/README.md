# Technical Debt Review

Reviewed on 2026-11-17 against commit `81b8010c`. Status: findings recorded; application fixes not implemented. Revised on 2026-11-25 after a source re-check: TD-07 raised to P1, TD-27 lowered to P3, and the impact of TD-06, TD-08, TD-12, and TD-46 clarified.

Each finding has its own `td-NN-summary.md` file. Priority is recorded inside each file and in the tables below, so re-prioritizing a finding does not rename its file. IDs remain stable for cross-references; all findings are open.

Source links use line numbers from commit `81b8010c`. After code changes, locate the named class or method rather than relying on the line number.

## Scope and Evidence

The source inventory contains **209 Java files and 33,387 Java lines** across the `hipda` and `emoji` modules. This was a risk-based static review of build/manifest configuration, account and network boundaries, posting/background work, persistence, rendering, and representative UI/resource paths. It is not a guarantee that every defect has been found.

There was no configured Android SDK or connected device during the review. The application was not built, linted, or exercised on Android. Seven focused Java probes were run instead; platform/transport stubs were used where necessary and are not evidence of full Android integration behavior. No live forum account, cookies, or submissions were used.

Evidence labels:

- **Probe-confirmed:** reproduced with a focused JVM probe of production classes or an extracted production code pattern.
- **Confirmed:** directly established by source control flow; the affected end-to-end Android flow still needs regression validation.
- **Risk:** the source establishes a risky design or missing boundary, but the proposed runtime consequence is not yet reproduced.
- **Migration:** applies to the planned tooling/target-SDK upgrade.
- **Maintenance:** testing, design, performance, accessibility, or delivery improvements.

Priority indicates suggested order, not a claim that an incident has occurred:

| Priority | Meaning | Findings |
| --- | --- | --- |
| P1 | Address before the next substantial release or as a prerequisite to safe migration | 10 |
| P2 | Important correctness, reliability, security hardening, or compatibility work | 34 |
| P3 | Incremental design, performance, and maintenance improvements | 4 |

Existing focused documents: [Android 17 migration](../android-17-migration.md) and [swipe-back flash investigation](../swipe-back-flash.md). The reported flash remains **unconfirmed as to root cause**; animation overrides and hidden translucency APIs are investigation targets, not a proven diagnosis. Its dedicated device-validation checklist remains open.

## First Work Packages

Every finding appears in exactly one package.

1. **Establish a test and CI baseline:** [TD-41](td-41-there-is-no-automated-regression-suite-or-ci.md). Configure unit-test source sets, test dependencies, and CI first, so each fix in the following packages can land with a regression test. This matches Stage 0 of the migration plan.
2. **Protect credentials and make reporting truthful:** [TD-01](td-01-auth-cookies-are-attached-using-url-substring-matching.md), [TD-02](td-02-credential-storage-silently-falls-back-to-plaintext.md), [TD-03](td-03-backup-rules-omit-encrypted-credentials-and-session-data.md), [TD-04](td-04-fileprovider-permits-an-unnecessarily-broad-external-path.md), [TD-05](td-05-logging-and-cleartext-policies-need-explicit-boundaries.md), and [TD-08](td-08-the-report-post-action-does-not-submit-a-report.md). Fix origin validation and encryption fallback, narrow FileProvider paths, and define backup/logging policies. TD-08 needs a product decision before implementation.
3. **Fix independently reproducible defects:** [TD-06](td-06-saving-an-image-writes-extra-bytes.md), [TD-07](td-07-exif-normalization-falls-through-switch-cases.md) with [TD-16](td-16-compressed-uploads-retain-the-source-mime-type.md), [TD-09](td-09-every-intercepted-request-pays-a-500-ms-delay.md), [TD-11](td-11-profilecomparator-violates-the-comparator-contract.md), [TD-12](td-12-thread-parsing-mixes-node-counts-with-element-indexing.md), [TD-31](td-31-image-metadata-cannot-recover-from-a-stale-successful-state.md), and [TD-47](td-47-password-representation-is-guessed-from-its-length.md). Fix TD-07 and TD-16 together because both determine what the upload path sends. Add a regression test alongside each small fix.
4. **Fix list and input-handling correctness:** [TD-13](td-13-replacing-a-thread-page-can-desynchronize-recyclerview-counts.md), [TD-14](td-14-click-handling-uses-positions-cached-at-bind-time.md), [TD-20](td-20-settings-mutate-collections-returned-by-sharedpreferences.md), and [TD-21](td-21-numeric-and-imported-input-validation-is-incomplete.md).
5. **Stabilize posting and account transitions:** [TD-15](td-15-posting-preflight-failures-can-escape-normal-error-reporting.md), [TD-22](td-22-jobs-and-caches-are-not-scoped-to-an-account-generation.md), [TD-23](td-23-posting-has-no-durable-outbox-and-draft-recovery-is-incomplete.md), [TD-24](td-24-upload-completion-events-can-disappear-while-another-screen-is-visible.md), and [TD-28](td-28-notification-work-reports-success-regardless-of-failure.md). Establish account-scoped operations and recoverable drafts/submission state.
6. **Bound resources and lifecycle work:** [TD-10](td-10-http-error-and-early-return-paths-leave-responses-open.md), [TD-17](td-17-failed-uploads-bypass-temporary-file-cleanup.md) through [TD-19](td-19-database-lifetime-and-migrations-are-not-consistently-owned.md), [TD-25](td-25-retained-fragments-outlive-their-view-and-activity-references.md) through [TD-27](td-27-one-shot-executors-are-created-without-shutdown.md), [TD-29](td-29-avatar-fetcher-does-not-close-the-file-streams-it-returns.md), [TD-30](td-30-dns-cache-is-shared-unsafely-and-has-no-expiry.md), [TD-32](td-32-image-upload-decodes-full-resolution-images-for-thumbnails.md) through [TD-39](td-39-cache-cleanup-bypasses-active-resource-ownership.md), and [TD-48](td-48-navigation-and-keyboard-layout-depend-on-legacy-window-behavior.md).
7. **Upgrade the platform with build gates:** [TD-40](td-40-the-target-30-build-needs-a-staged-platform-upgrade.md), [TD-42](td-42-lint-and-release-shrinking-hide-useful-feedback.md), [TD-43](td-43-build-reproducibility-and-dependency-review-need-explicit-support.md), and the existing migration plan. Fix the version comparison in [TD-46](td-46-crash-handling-obsolete-code-and-setup-documentation-need-cleanup.md) before any release changes the `#.#.##` version format. Then address [TD-44](td-44-ui-persistence-parsing-and-account-state-are-tightly-coupled.md), [TD-45](td-45-accessibility-and-ui-text-need-a-dedicated-pass.md), and the rest of TD-46.

## Focused Probe Results

The probes used JDK 17, Java 8 compilation compatibility, and the project's declared Jsoup 1.11.3. ProfileComparator was compiled with its real Profile class. CursorUtils, RetryIntercepter, and ImageInfo used small platform/transport stubs. The image-copy and node-iteration probes exercised extracted production patterns.

| Probe | Observed result |
| --- | --- |
| Image-save loop | Inputs of 0, 1, 1,024, and 1,500 bytes produced 1,024, 2,048, 2,048, and 3,072 bytes respectively; none matched the input |
| EXIF conversion | EXIF 6 (90°) and 3 (180°) both returned 270; normal EXIF 1 remained 1 |
| ProfileComparator | `compare(A, A) == -1`; inserting A twice into TreeMap produced size 2 |
| Retry interceptor | One successful chain invocation took 501 ms |
| Download origin predicate | A URL hosted at `example.invalid` was selected for the auth-cookie header |
| Thread element iteration | One post surrounded by newlines gave three nodes/one element and IndexOutOfBoundsException |
| Image state recovery | SUCCESS followed by IDLE or FAIL remained SUCCESS (3) |

The request probe used a fake transport, and the URL probe evaluated only the predicate. The report action, account races, leaks under load, backup restore, and swipe flash have not been validated on a device.

## Findings

### Security and account data

| ID | Severity | Finding | Evidence |
| --- | --- | --- | --- |
| TD-01 | P1 | [Auth cookies are attached using URL substring matching](td-01-auth-cookies-are-attached-using-url-substring-matching.md) | Probe-confirmed |
| TD-02 | P1 | [Credential storage silently falls back to plaintext](td-02-credential-storage-silently-falls-back-to-plaintext.md) | Confirmed |
| TD-03 | P2 | [Backup rules omit encrypted credentials and session data](td-03-backup-rules-omit-encrypted-credentials-and-session-data.md) | Risk |
| TD-04 | P2 | [FileProvider permits an unnecessarily broad external path](td-04-fileprovider-permits-an-unnecessarily-broad-external-path.md) | Risk |
| TD-05 | P2 | [Logging and cleartext policies need explicit boundaries](td-05-logging-and-cleartext-policies-need-explicit-boundaries.md) | Risk |

### Correctness and user-visible behavior

| ID | Severity | Finding | Evidence |
| --- | --- | --- | --- |
| TD-06 | P1 | [Saving an image writes extra bytes](td-06-saving-an-image-writes-extra-bytes.md) | Probe-confirmed |
| TD-07 | P1 | [EXIF normalization falls through switch cases](td-07-exif-normalization-falls-through-switch-cases.md) | Probe-confirmed |
| TD-08 | P1 | [The report-post action does not submit a report](td-08-the-report-post-action-does-not-submit-a-report.md) | Confirmed |
| TD-09 | P1 | [Every intercepted request pays a 500 ms delay](td-09-every-intercepted-request-pays-a-500-ms-delay.md) | Probe-confirmed |
| TD-10 | P2 | [HTTP error and early-return paths leave responses open](td-10-http-error-and-early-return-paths-leave-responses-open.md) | Confirmed |
| TD-11 | P2 | [ProfileComparator violates the comparator contract](td-11-profilecomparator-violates-the-comparator-contract.md) | Probe-confirmed |
| TD-12 | P2 | [Thread parsing mixes node counts with element indexing](td-12-thread-parsing-mixes-node-counts-with-element-indexing.md) | Probe-confirmed |
| TD-13 | P2 | [Replacing a thread page can desynchronize RecyclerView counts](td-13-replacing-a-thread-page-can-desynchronize-recyclerview-counts.md) | Confirmed |
| TD-14 | P2 | [Click handling uses positions cached at bind time](td-14-click-handling-uses-positions-cached-at-bind-time.md) | Risk |
| TD-15 | P2 | [Posting preflight failures can escape normal error reporting](td-15-posting-preflight-failures-can-escape-normal-error-reporting.md) | Confirmed |
| TD-16 | P2 | [Compressed uploads retain the source MIME type](td-16-compressed-uploads-retain-the-source-mime-type.md) | Confirmed |
| TD-17 | P2 | [Failed uploads bypass temporary-file cleanup](td-17-failed-uploads-bypass-temporary-file-cleanup.md) | Confirmed |

### Persistence and input validation

| ID | Severity | Finding | Evidence |
| --- | --- | --- | --- |
| TD-18 | P2 | [Several 'background' database operations run on the UI thread](td-18-several-background-database-operations-run-on-the-ui-thread.md) | Confirmed |
| TD-19 | P2 | [Database lifetime and migrations are not consistently owned](td-19-database-lifetime-and-migrations-are-not-consistently-owned.md) | Risk |
| TD-20 | P2 | [Settings mutate collections returned by SharedPreferences](td-20-settings-mutate-collections-returned-by-sharedpreferences.md) | Confirmed |
| TD-21 | P2 | [Numeric and imported input validation is incomplete](td-21-numeric-and-imported-input-validation-is-incomplete.md) | Confirmed |

### Lifecycle and background work

| ID | Severity | Finding | Evidence |
| --- | --- | --- | --- |
| TD-22 | P1 | [Jobs and caches are not scoped to an account generation](td-22-jobs-and-caches-are-not-scoped-to-an-account-generation.md) | Risk |
| TD-23 | P1 | [Posting has no durable outbox and draft recovery is incomplete](td-23-posting-has-no-durable-outbox-and-draft-recovery-is-incomplete.md) | Risk |
| TD-24 | P2 | [Upload completion events can disappear while another screen is visible](td-24-upload-completion-events-can-disappear-while-another-screen-is-visible.md) | Confirmed |
| TD-25 | P2 | [Retained fragments outlive their view and activity references](td-25-retained-fragments-outlive-their-view-and-activity-references.md) | Risk |
| TD-26 | P2 | [Network cancellation is mostly disconnected from lifecycle cleanup](td-26-network-cancellation-is-mostly-disconnected-from-lifecycle-cleanup.md) | Confirmed |
| TD-27 | P3 | [One-shot executors are created without shutdown](td-27-one-shot-executors-are-created-without-shutdown.md) | Confirmed |
| TD-28 | P2 | [Notification work reports success regardless of failure](td-28-notification-work-reports-success-regardless-of-failure.md) | Confirmed |

### Images, memory, and caching

| ID | Severity | Finding | Evidence |
| --- | --- | --- | --- |
| TD-29 | P2 | [Avatar fetcher does not close the file streams it returns](td-29-avatar-fetcher-does-not-close-the-file-streams-it-returns.md) | Confirmed |
| TD-30 | P2 | [DNS cache is shared unsafely and has no expiry](td-30-dns-cache-is-shared-unsafely-and-has-no-expiry.md) | Risk |
| TD-31 | P2 | [Image metadata cannot recover from a stale successful state](td-31-image-metadata-cannot-recover-from-a-stale-successful-state.md) | Probe-confirmed |
| TD-32 | P2 | [Image upload decodes full-resolution images for thumbnails](td-32-image-upload-decodes-full-resolution-images-for-thumbnails.md) | Risk |
| TD-33 | P2 | [Image geometry is cached as portrait-sized global state](td-33-image-geometry-is-cached-as-portrait-sized-global-state.md) | Confirmed |
| TD-34 | P2 | [Emoji thumbnail ownership is global across composers](td-34-emoji-thumbnail-ownership-is-global-across-composers.md) | Risk |
| TD-35 | P3 | [Emoji rendering rescans the full draft on each edit](td-35-emoji-rendering-rescans-the-full-draft-on-each-edit.md) | Maintenance |
| TD-36 | P2 | [Progress updates and animations lack lifecycle bounds](td-36-progress-updates-and-animations-lack-lifecycle-bounds.md) | Confirmed |

### Storage, navigation, and platform compatibility

| ID | Severity | Finding | Evidence |
| --- | --- | --- | --- |
| TD-37 | P2 | [Downloads depend on an obsolete storage permission](td-37-downloads-depend-on-an-obsolete-storage-permission.md) | Confirmed |
| TD-38 | P2 | [MediaStore saves do not have a complete publication lifecycle](td-38-mediastore-saves-do-not-have-a-complete-publication-lifecycle.md) | Risk |
| TD-39 | P2 | [Cache cleanup bypasses active resource ownership](td-39-cache-cleanup-bypasses-active-resource-ownership.md) | Risk |
| TD-40 | P1 | [The target-30 build needs a staged platform upgrade](td-40-the-target-30-build-needs-a-staged-platform-upgrade.md) | Migration |

### Build, architecture, and product quality

| ID | Severity | Finding | Evidence |
| --- | --- | --- | --- |
| TD-41 | P1 | [There is no automated regression suite or CI](td-41-there-is-no-automated-regression-suite-or-ci.md) | Maintenance |
| TD-42 | P2 | [Lint and release shrinking hide useful feedback](td-42-lint-and-release-shrinking-hide-useful-feedback.md) | Maintenance |
| TD-43 | P2 | [Build reproducibility and dependency review need explicit support](td-43-build-reproducibility-and-dependency-review-need-explicit-support.md) | Maintenance |
| TD-44 | P3 | [UI, persistence, parsing, and account state are tightly coupled](td-44-ui-persistence-parsing-and-account-state-are-tightly-coupled.md) | Maintenance |
| TD-45 | P2 | [Accessibility and UI text need a dedicated pass](td-45-accessibility-and-ui-text-need-a-dedicated-pass.md) | Maintenance |
| TD-46 | P3 | [Crash handling, obsolete code, and setup documentation need cleanup](td-46-crash-handling-obsolete-code-and-setup-documentation-need-cleanup.md) | Maintenance |
| TD-47 | P2 | [Password representation is guessed from its length](td-47-password-representation-is-guessed-from-its-length.md) | Confirmed |
| TD-48 | P2 | [Navigation and keyboard layout depend on legacy window behavior](td-48-navigation-and-keyboard-layout-depend-on-legacy-window-behavior.md) | Risk |

## Additional Follow-up Areas

- Profile notification state and mutable sticky events under concurrent fetches; replace class-global event snapshots with operation-scoped state where needed.
- Review notification-channel sound ownership: the UI selects a ringtone while delivery configures the builder and channel initialization runs separately.
- Bound thread-page/reverse-index caches and keep reverse mappings consistent when pages are replaced or removed; measure long-thread memory growth.
- Validate the emoji button's missing-avatar fallback: BaseFragment assigns the fallback face icon to the keyboard drawable field while displaying the face drawable.
- Prefer monotonic time for delays/timeouts, and atomic settings/account updates for related state.
- Review updater endpoints and server/CDN configuration validation; endpoint liveness and server-side behavior were not tested.
- Audit actual dependency advisories, licenses, and packaged native libraries after resolving the dependency graph.
- Preserve intentional cross-account draft/history behavior only after documenting the product/privacy decision.

## Completion and Re-review

Implement work in small commits with targeted regression checks. Use the findings' verification steps as acceptance criteria, then run debug and minified release builds, lint, the automated suite, and the migration device matrix. Update each item's status as evidence changes. Revisit the previously reported swipe flash with frame capture and the toolbar-versus-custom-swipe comparison before selecting its fix.
