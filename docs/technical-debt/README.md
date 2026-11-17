# Technical Debt Review

Reviewed on 2026-11-17 against commit `81b8010c`. Status: findings recorded; application fixes not implemented.

Each finding has its own `%severity% - %summary%.md` file. IDs remain stable for cross-references; all findings are open.

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
| P1 | Address before the next substantial release or as a prerequisite to safe migration | 9 |
| P2 | Important correctness, reliability, security hardening, or compatibility work | 36 |
| P3 | Incremental design, performance, and maintenance improvements | 3 |

Existing focused documents: [Android 17 migration](../android-17-migration.md) and [swipe-back flash investigation](../swipe-back-flash.md). The reported flash remains **unconfirmed as to root cause**; animation overrides and hidden translucency APIs are investigation targets, not a proven diagnosis. Its dedicated device-validation checklist remains open.

## First Work Packages

1. **Protect credentials and restore trust:** [TD-01](P1%20-%20Auth%20cookies%20are%20attached%20using%20URL%20substring%20matching.md), [TD-02](P1%20-%20Credential%20storage%20silently%20falls%20back%20to%20plaintext.md), [TD-03](P2%20-%20Backup%20rules%20omit%20encrypted%20credentials%20and%20session%20data.md), [TD-05](P2%20-%20Logging%20and%20cleartext%20policies%20need%20explicit%20boundaries.md), and [TD-08](P1%20-%20The%20report-post%20action%20does%20not%20submit%20a%20report.md). Fix origin validation and encryption fallback, define backup/logging policies, and make report submission truthful.
2. **Fix independently reproducible defects:** [TD-06](P1%20-%20Saving%20an%20image%20writes%20extra%20bytes.md), [TD-07](P2%20-%20EXIF%20normalization%20falls%20through%20switch%20cases.md), [TD-09](P1%20-%20Every%20intercepted%20request%20pays%20a%20500%20ms%20delay.md), [TD-11](P2%20-%20ProfileComparator%20violates%20the%20comparator%20contract.md), [TD-12](P2%20-%20Thread%20parsing%20mixes%20node%20counts%20with%20element%20indexing.md), [TD-31](P2%20-%20Image%20metadata%20cannot%20recover%20from%20a%20stale%20successful%20state.md), and [TD-47](P2%20-%20Password%20representation%20is%20guessed%20from%20its%20length.md). Add regression tests alongside each small fix.
3. **Stabilize posting and account transitions:** [TD-15](P2%20-%20Posting%20preflight%20failures%20can%20escape%20normal%20error%20reporting.md), [TD-22](P1%20-%20Jobs%20and%20caches%20are%20not%20scoped%20to%20an%20account%20generation.md), [TD-23](P1%20-%20Posting%20has%20no%20durable%20outbox%20and%20draft%20recovery%20is%20incomplete.md), [TD-24](P2%20-%20Upload%20completion%20events%20can%20disappear%20while%20another%20screen%20is%20visible.md), and [TD-28](P2%20-%20Notification%20work%20reports%20success%20regardless%20of%20failure.md). Establish account-scoped operations and recoverable drafts/submission state.
4. **Bound resources and lifecycle work:** [TD-10](P2%20-%20HTTP%20error%20and%20early-return%20paths%20leave%20responses%20open.md), [TD-17](P2%20-%20Failed%20uploads%20bypass%20temporary-file%20cleanup.md) through [TD-19](P2%20-%20Database%20lifetime%20and%20migrations%20are%20not%20consistently%20owned.md), [TD-25](P2%20-%20Retained%20fragments%20outlive%20their%20view%20and%20activity%20references.md) through [TD-30](P2%20-%20DNS%20cache%20is%20shared%20unsafely%20and%20has%20no%20expiry.md), [TD-32](P2%20-%20Image%20upload%20decodes%20full-resolution%20images%20for%20thumbnails.md) through [TD-39](P2%20-%20Cache%20cleanup%20bypasses%20active%20resource%20ownership.md), and [TD-48](P2%20-%20Navigation%20and%20keyboard%20layout%20depend%20on%20legacy%20window%20behavior.md).
5. **Upgrade the platform with build gates:** [TD-40](P1%20-%20The%20target-30%20build%20needs%20a%20staged%20platform%20upgrade.md) through [TD-43](P2%20-%20Build%20reproducibility%20and%20dependency%20review%20need%20explicit%20support.md) and the existing migration plan; then address the remaining UI/design backlog.

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
| TD-01 | P1 | [Auth cookies are attached using URL substring matching](P1%20-%20Auth%20cookies%20are%20attached%20using%20URL%20substring%20matching.md) | Probe-confirmed |
| TD-02 | P1 | [Credential storage silently falls back to plaintext](P1%20-%20Credential%20storage%20silently%20falls%20back%20to%20plaintext.md) | Confirmed |
| TD-03 | P2 | [Backup rules omit encrypted credentials and session data](P2%20-%20Backup%20rules%20omit%20encrypted%20credentials%20and%20session%20data.md) | Risk |
| TD-04 | P2 | [FileProvider permits an unnecessarily broad external path](P2%20-%20FileProvider%20permits%20an%20unnecessarily%20broad%20external%20path.md) | Risk |
| TD-05 | P2 | [Logging and cleartext policies need explicit boundaries](P2%20-%20Logging%20and%20cleartext%20policies%20need%20explicit%20boundaries.md) | Risk |

### Correctness and user-visible behavior

| ID | Severity | Finding | Evidence |
| --- | --- | --- | --- |
| TD-06 | P1 | [Saving an image writes extra bytes](P1%20-%20Saving%20an%20image%20writes%20extra%20bytes.md) | Probe-confirmed |
| TD-07 | P2 | [EXIF normalization falls through switch cases](P2%20-%20EXIF%20normalization%20falls%20through%20switch%20cases.md) | Probe-confirmed |
| TD-08 | P1 | [The report-post action does not submit a report](P1%20-%20The%20report-post%20action%20does%20not%20submit%20a%20report.md) | Confirmed |
| TD-09 | P1 | [Every intercepted request pays a 500 ms delay](P1%20-%20Every%20intercepted%20request%20pays%20a%20500%20ms%20delay.md) | Probe-confirmed |
| TD-10 | P2 | [HTTP error and early-return paths leave responses open](P2%20-%20HTTP%20error%20and%20early-return%20paths%20leave%20responses%20open.md) | Confirmed |
| TD-11 | P2 | [ProfileComparator violates the comparator contract](P2%20-%20ProfileComparator%20violates%20the%20comparator%20contract.md) | Probe-confirmed |
| TD-12 | P2 | [Thread parsing mixes node counts with element indexing](P2%20-%20Thread%20parsing%20mixes%20node%20counts%20with%20element%20indexing.md) | Probe-confirmed |
| TD-13 | P2 | [Replacing a thread page can desynchronize RecyclerView counts](P2%20-%20Replacing%20a%20thread%20page%20can%20desynchronize%20RecyclerView%20counts.md) | Confirmed |
| TD-14 | P2 | [Click handling uses positions cached at bind time](P2%20-%20Click%20handling%20uses%20positions%20cached%20at%20bind%20time.md) | Risk |
| TD-15 | P2 | [Posting preflight failures can escape normal error reporting](P2%20-%20Posting%20preflight%20failures%20can%20escape%20normal%20error%20reporting.md) | Confirmed |
| TD-16 | P2 | [Compressed uploads retain the source MIME type](P2%20-%20Compressed%20uploads%20retain%20the%20source%20MIME%20type.md) | Confirmed |
| TD-17 | P2 | [Failed uploads bypass temporary-file cleanup](P2%20-%20Failed%20uploads%20bypass%20temporary-file%20cleanup.md) | Confirmed |

### Persistence and input validation

| ID | Severity | Finding | Evidence |
| --- | --- | --- | --- |
| TD-18 | P2 | [Several 'background' database operations run on the UI thread](P2%20-%20Several%20%27background%27%20database%20operations%20run%20on%20the%20UI%20thread.md) | Confirmed |
| TD-19 | P2 | [Database lifetime and migrations are not consistently owned](P2%20-%20Database%20lifetime%20and%20migrations%20are%20not%20consistently%20owned.md) | Risk |
| TD-20 | P2 | [Settings mutate collections returned by SharedPreferences](P2%20-%20Settings%20mutate%20collections%20returned%20by%20SharedPreferences.md) | Confirmed |
| TD-21 | P2 | [Numeric and imported input validation is incomplete](P2%20-%20Numeric%20and%20imported%20input%20validation%20is%20incomplete.md) | Confirmed |

### Lifecycle and background work

| ID | Severity | Finding | Evidence |
| --- | --- | --- | --- |
| TD-22 | P1 | [Jobs and caches are not scoped to an account generation](P1%20-%20Jobs%20and%20caches%20are%20not%20scoped%20to%20an%20account%20generation.md) | Risk |
| TD-23 | P1 | [Posting has no durable outbox and draft recovery is incomplete](P1%20-%20Posting%20has%20no%20durable%20outbox%20and%20draft%20recovery%20is%20incomplete.md) | Risk |
| TD-24 | P2 | [Upload completion events can disappear while another screen is visible](P2%20-%20Upload%20completion%20events%20can%20disappear%20while%20another%20screen%20is%20visible.md) | Confirmed |
| TD-25 | P2 | [Retained fragments outlive their view and activity references](P2%20-%20Retained%20fragments%20outlive%20their%20view%20and%20activity%20references.md) | Risk |
| TD-26 | P2 | [Network cancellation is mostly disconnected from lifecycle cleanup](P2%20-%20Network%20cancellation%20is%20mostly%20disconnected%20from%20lifecycle%20cleanup.md) | Confirmed |
| TD-27 | P2 | [One-shot executors are created without shutdown](P2%20-%20One-shot%20executors%20are%20created%20without%20shutdown.md) | Confirmed |
| TD-28 | P2 | [Notification work reports success regardless of failure](P2%20-%20Notification%20work%20reports%20success%20regardless%20of%20failure.md) | Confirmed |

### Images, memory, and caching

| ID | Severity | Finding | Evidence |
| --- | --- | --- | --- |
| TD-29 | P2 | [Avatar fetcher does not close the file streams it returns](P2%20-%20Avatar%20fetcher%20does%20not%20close%20the%20file%20streams%20it%20returns.md) | Confirmed |
| TD-30 | P2 | [DNS cache is shared unsafely and has no expiry](P2%20-%20DNS%20cache%20is%20shared%20unsafely%20and%20has%20no%20expiry.md) | Risk |
| TD-31 | P2 | [Image metadata cannot recover from a stale successful state](P2%20-%20Image%20metadata%20cannot%20recover%20from%20a%20stale%20successful%20state.md) | Probe-confirmed |
| TD-32 | P2 | [Image upload decodes full-resolution images for thumbnails](P2%20-%20Image%20upload%20decodes%20full-resolution%20images%20for%20thumbnails.md) | Risk |
| TD-33 | P2 | [Image geometry is cached as portrait-sized global state](P2%20-%20Image%20geometry%20is%20cached%20as%20portrait-sized%20global%20state.md) | Confirmed |
| TD-34 | P2 | [Emoji thumbnail ownership is global across composers](P2%20-%20Emoji%20thumbnail%20ownership%20is%20global%20across%20composers.md) | Risk |
| TD-35 | P3 | [Emoji rendering rescans the full draft on each edit](P3%20-%20Emoji%20rendering%20rescans%20the%20full%20draft%20on%20each%20edit.md) | Maintenance |
| TD-36 | P2 | [Progress updates and animations lack lifecycle bounds](P2%20-%20Progress%20updates%20and%20animations%20lack%20lifecycle%20bounds.md) | Confirmed |

### Storage, navigation, and platform compatibility

| ID | Severity | Finding | Evidence |
| --- | --- | --- | --- |
| TD-37 | P2 | [Downloads depend on an obsolete storage permission](P2%20-%20Downloads%20depend%20on%20an%20obsolete%20storage%20permission.md) | Confirmed |
| TD-38 | P2 | [MediaStore saves do not have a complete publication lifecycle](P2%20-%20MediaStore%20saves%20do%20not%20have%20a%20complete%20publication%20lifecycle.md) | Risk |
| TD-39 | P2 | [Cache cleanup bypasses active resource ownership](P2%20-%20Cache%20cleanup%20bypasses%20active%20resource%20ownership.md) | Risk |
| TD-40 | P1 | [The target-30 build needs a staged platform upgrade](P1%20-%20The%20target-30%20build%20needs%20a%20staged%20platform%20upgrade.md) | Migration |

### Build, architecture, and product quality

| ID | Severity | Finding | Evidence |
| --- | --- | --- | --- |
| TD-41 | P1 | [There is no automated regression suite or CI](P1%20-%20There%20is%20no%20automated%20regression%20suite%20or%20CI.md) | Maintenance |
| TD-42 | P2 | [Lint and release shrinking hide useful feedback](P2%20-%20Lint%20and%20release%20shrinking%20hide%20useful%20feedback.md) | Maintenance |
| TD-43 | P2 | [Build reproducibility and dependency review need explicit support](P2%20-%20Build%20reproducibility%20and%20dependency%20review%20need%20explicit%20support.md) | Maintenance |
| TD-44 | P3 | [UI, persistence, parsing, and account state are tightly coupled](P3%20-%20UI%2C%20persistence%2C%20parsing%2C%20and%20account%20state%20are%20tightly%20coupled.md) | Maintenance |
| TD-45 | P2 | [Accessibility and UI text need a dedicated pass](P2%20-%20Accessibility%20and%20UI%20text%20need%20a%20dedicated%20pass.md) | Maintenance |
| TD-46 | P3 | [Crash handling, obsolete code, and setup documentation need cleanup](P3%20-%20Crash%20handling%2C%20obsolete%20code%2C%20and%20setup%20documentation%20need%20cleanup.md) | Maintenance |
| TD-47 | P2 | [Password representation is guessed from its length](P2%20-%20Password%20representation%20is%20guessed%20from%20its%20length.md) | Confirmed |
| TD-48 | P2 | [Navigation and keyboard layout depend on legacy window behavior](P2%20-%20Navigation%20and%20keyboard%20layout%20depend%20on%20legacy%20window%20behavior.md) | Risk |

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
