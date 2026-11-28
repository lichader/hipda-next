# Architecture layering plan

Status: **in progress**, started on 2026-11-28. Current milestone: **PoC, Blacklist feature**. Implemented and tested, and checked on the API 21 emulator while logged out; a check with a logged-in account is still outstanding ([status](#status)).

This plan prepares the app for a UI redesign. It puts the code that talks to the forum website behind one abstraction layer and drives each screen from a ViewModel. Then a new UI can be built on stable models without touching HTML, URLs or HTTP. The plan is incremental: every step ships on its own and keeps the app working.

## Why

The current code mixes UI, networking, parsing, persistence and account state. See [TD-44](technical-debt/td-44-ui-persistence-parsing-and-account-state-are-tightly-coupled.md) and also [TD-22](technical-debt/td-22-jobs-and-caches-are-not-scoped-to-an-account-generation.md), [TD-25](technical-debt/td-25-retained-fragments-outlive-their-view-and-activity-references.md) and [TD-26](technical-debt/td-26-network-cancellation-is-mostly-disconnected-from-lifecycle-cleanup.md). A review on 2026-11-28 found:

- **Forum access is spread over about 25 files and uses four mechanisms:**
  - Job + sticky EventBus: thread list, thread detail, simple lists, posting and upload.
  - `AsyncTask`: `PrePostAsyncTask` and `PostSmsAsyncTask`.
  - Raw `OkHttpHelper` callbacks with Jsoup parsing inside fragments: `ThreadDetailFragment`, `UserinfoFragment`, `SmsFragment` and `BlacklistFragment`.
  - Helpers that show toasts and dialogs themselves: `BlacklistHelper`, `FavoriteHelper`, `UpdateHelper` and `TaskHelper`.
- **Parsers have side effects:**
  - They start notification fetches and write settings (`setUsername`, `setMaxPostsInPage`).
  - They apply blacklist and sticky-thread policy.
  - They import `ui.textstyle`, `GlideHelper` and `SimpleListJob`.
- **About 31 endpoint URLs are mutable static strings** in `HiUtils`, read from about 30 files, including UI classes.
- **`OkHttpHelper.getErrorMessage()` returns UI text** in Chinese, so the network layer produces display strings.
- **Account and session state live in `HiSettingsHelper`** next to the display preferences.
- **Fragments are both controller and state holder.** `ThreadDetailFragment` has 1,627 lines and there are no ViewModels.
- **Responses come in several formats:** full HTML pages, `inajax=1` XML responses and JSON (`config.php`), in GBK or UTF-8.

## Target architecture

The target is MVVM. The ViewModel plays the "controller" role and survives configuration changes, and the Fragment/Activity (or Compose later) only renders.

```
View          Fragment / Activity / Adapter (or Compose): renders UiState, forwards user actions
                │ collects StateFlow<UiState>, Flow<message>     ▲ user actions
ViewModel     @HiltViewModel per screen: screen state, error → user text
                │ calls suspend functions in viewModelScope
Repository    app module, data/<feature>: combines the site adapter with local storage
                │ (settings, DB, caches) and app policy (blacklist filtering, account scope)
Site adapter  :forum module: the only code that knows about HTML, XML, JSON, URLs and form tokens
                ├ ForumConfig      server, charset, user agent, session key (supplied by the app)
                ├ HiPdaEndpoints   URLs built from ForumConfig (replaces the HiUtils statics)
                ├ ForumTransport   OkHttp; charset; cancellable suspend calls; typed errors
                ├ FormhashCache    the forum's form token, scoped to the login session
                ├ parsers          pure: Document → model
                └ <Feature>Api     interface + HiPda implementation per feature
Model         :forum module: data classes, ForumException (sealed)
```

### Rules

1. **Only `:forum` may use Jsoup, build forum URLs or touch request and response bodies.** UI, ViewModels and repositories never see HTML, URLs or formhash.
2. **`:forum` is a pure Kotlin/JVM module.** It has no Android SDK, no Hilt and no app classes, and the compiler enforces this. The app module wires it in `di/ForumModule.kt`.
   - Because Android Lint does not check this module for API levels, use only `java.*` APIs that exist on API 21. For example, use `URLEncoder.encode(String, String)`, not the `Charset` overload.
3. **Site adapter functions are main-safe `suspend` functions.** OkHttp runs the request on its own threads; parsing runs on `Dispatchers.Default`. When a coroutine is cancelled, its HTTP call is cancelled too.
4. **Errors are `ForumException` subclasses:** `Network`, `Timeout`, `Http(code)`, `Server(message)` for the forum's own `alert_error` text, and `Parse`.
   - The app's `ForumErrorFormatter` turns them into user text, so `:forum` holds no UI text.
   - The forum's own messages pass through unchanged.
5. **ViewModels expose an immutable `StateFlow<UiState>` and a one-shot `Flow<String>` for messages.** The messages come from a buffered `Channel`, so messages sent while the screen is stopped are delivered on return.
6. **Repositories are interfaces with a `Default…` implementation**, so ViewModel tests use plain fakes.
7. **Legacy Java callers that can't call `suspend` functions get a thin Kotlin bridge** that launches on the `@ApplicationScope` scope (for example `async/BlacklistHelper`). A bridge is deleted when its last caller gets a ViewModel.
8. **A migrated fragment turns off `BaseFragment`'s instance retention (`retainInstance = false`).** Its state lives in the ViewModel, and Hilt crashes retained fragments on configuration changes. Add an `ActivityLaunchTest` that recreates the screen.

### Package layout

| Where | Package | Contents |
| --- | --- | --- |
| `forum/` | `com.lichader.hipdanext.forum` | `ForumConfig`, `HiPdaEndpoints`, `ForumException` |
| | `…forum.http` | `ForumTransport` |
| | `…forum.session` | `FormhashCache` |
| | `…forum.parser` | Shared parsing helpers |
| | `…forum.<feature>` | `XxxApi`, `HiPdaXxxApi`, the feature's parser and models |
| `hipda/src/main/kotlin` | `…data.<feature>` | `XxxRepository`, `DefaultXxxRepository` |
| | `…data` | `SettingsForumConfig` |
| | `…di` | `ForumModule`, `RepositoryModule`, `ApplicationScope` |
| | `…ui…` | `XxxViewModel` next to its fragment; `ForumErrorFormatter` |

### Testing

- **`:forum:test`** is plain JUnit with HTML fixtures under `forum/src/test/resources/fixtures/` and OkHttp `MockWebServer`. It needs no device, no Robolectric and no forum access.
- **ViewModel tests** are plain JUnit in `hipda/src/test/kotlin` with fake repositories and `kotlinx-coroutines-test`.
- **The Robolectric smoke tests** (`ActivityLaunchTest`, `AppStartupTest`) keep checking that every screen launches and every service resolves.

## Phases

The phases are layers. The PoC goes through all of them for one feature, and each later screen migration does the same for its own feature.

| Phase | Content |
| --- | --- |
| 0. Fixture tests | Each parser gets tests against saved pages (scrubbed of usernames, uids and formhash) before it is changed |
| 1. Pure parsers, injectable endpoints | Parsers return what they currently write to settings; they lose their Android, UI and side-effect dependencies; URLs move to `HiPdaEndpoints` |
| 2. Site adapter | `ForumTransport`, typed errors, `SessionGuard` (log-in detection and one retry, currently copied into each job), feature APIs in `:forum` |
| 3. Repositories, account boundary | `AccountRepository` takes over credentials and session from `HiSettingsHelper` and adds an account generation (TD-22); `ThreadRepository` owns `ThreadDetailCache` and replaces `ThreadUpdatedEvent` |
| 4. ViewModels per screen | Each fragment only renders; its Job/Event/`@Subscribe`/direct OkHttp code is deleted |
| 5. Remove old plumbing | EventBus, read jobs, `AsyncTask`s, static URL fields and `SingletonEntryPoint` callers; posting moves to WorkManager with the outbox (TD-23) |

**How this relates to the redesign:** screens that will be redesigned don't need their old fragment converted. Build the ViewModel together with the new UI, then delete the old fragment.

## Status

### Feature migration tracker

| Feature | Screens and callers | Old path | Status |
| --- | --- | --- | --- |
| Blacklist | `BlacklistFragment`; `UserinfoFragment` (add/remove); `TaskHelper` (daily sync) | `BlacklistHelper` + inline Jsoup in fragment | **Migrated (PoC)**. Logged-in device check outstanding; `BlacklistHelper` bridge remains until `UserinfoFragment` and `TaskHelper` migrate |
| User info | `UserinfoFragment` | `asyncGet` + `HiParser.parseUserInfo` in fragment; `SimpleListJob` for threads | Not started |
| Thread list | `ThreadListFragment` | `ThreadListJob` + EventBus | Not started |
| Simple lists, search | `SimpleListFragment`, `SearchFragment` | `SimpleListJob` (11 types) + EventBus | Not started |
| Private messages | `SmsFragment`, `BaseFragment` SMS dialog | `SimpleListJob`, `PostSmsAsyncTask`, `asyncGet` | Not started |
| Favorites | Thread detail menu, lists | `FavoriteHelper` | Not started |
| Posting, upload | `PostFragment`, quick reply, vote, delete | `PrePostAsyncTask`, `PostJob`, `ImageUploadJob` | Not started |
| Thread detail | `ThreadDetailFragment` | `ThreadDetailJob` + EventBus, `asyncGet` for warnings | Not started |
| Login, session | `LoginDialog`, jobs' relogin | `LoginHelper` | Not started |
| Notifications | `NotiWorker`, parser side effects | `NotiHelper` | Not started |

### PoC: Blacklist

The Blacklist feature was chosen because it is the smallest complete slice. It has one screen with one read and one write, no jobs or EventBus, inline parsing and only three external callers. It exercises every layer and the Java-interop bridge.

| # | Step | Status |
| --- | --- | --- |
| 1 | `:forum` Kotlin/JVM module: build file, settings, CI test step | Done. CI runs `:forum:test` and publishes its results |
| 2 | `ForumConfig`, `HiPdaEndpoints` (blacklist URLs), `ForumException` | Done |
| 3 | `ForumTransport`: GBK/UTF-8 decoding, form encoding, cancellable calls, error mapping | Done |
| 4 | `FormhashCache` scoped to the session cookie | Done |
| 5 | `BlacklistParser` + `HiPdaBlacklistApi` (fetch, add, remove) | Done |
| 6 | Fixture and `MockWebServer` tests for steps 3–5; parity check against `HiParser.parseBlacklist` | Done: 26 tests pass. See [PoC notes](#poc-notes) |
| 7 | App wiring: `SettingsForumConfig`, `ForumModule`, `ForumErrorFormatter`, `@ApplicationScope` | Done. `SettingsForumConfigTest` checks that the endpoints derive the same base URL as `HiUtils.BaseUrl` |
| 8 | `BlacklistRepository` (updates the stored blacklist and sync time) | Done |
| 9 | `BlacklistViewModel` + ViewModel tests | Done: 8 ViewModel tests and 4 formatter tests |
| 10 | `BlacklistFragment` rewritten in Kotlin over the ViewModel | Done. Same layout, `TAG_KEY` and entry point; `ActivityLaunchTest.settingsBlacklist` passes |
| 11 | `BlacklistHelper` turned into a bridge; `UserinfoFragment` and `TaskHelper` moved to it | Done. `UserinfoFragment` no longer keeps a formhash |
| 12 | Old code removed: `HiParser.parseBlacklist`, blacklist URLs in `HiUtils`, unused `addBlacklist2` | Done |
| 13 | Build, unit tests, lint, release build (R8) | Done: debug and release builds pass, 69/69 unit tests pass (26 `:forum`, 43 app). Lint finds nothing new in changed files |
| 14 | Device check: open the blacklist, pull to refresh, remove a user; add/remove from user info | **Partly done.** On the API 21 emulator, logged out: open, refresh and rotation work. Listing and removing real entries, and add/remove from user info, still need a logged-in account. See [PoC notes](#poc-notes) |

### PoC notes

- **Parity check (step 6):** a temporary Robolectric test ran the old `HiParser.parseBlacklist`, `parseFormhash` and `parseErrorMessage` on every fixture, and the results were compared with `:forum`. They match on every fixture except one: for a `space.php?uid=` link without a username, the old parser added an empty username `""`, and the new one skips it. The test was then deleted.
- **The cancellation test checks something real.** With the transport's `invokeOnCancellation { call.cancel() }` removed, `ForumTransportTest.cancellingTheCallerCancelsTheHttpCall` fails.
- **Device check (step 14), 2026-11-28, `hipda-api21` emulator (API 21), logged out, live forum:**
  - Opening the blacklist sent one `GET pm.php?action=viewblack`. The GBK page was decoded correctly.
  - The forum's error box became `ForumException.Server`, and its text was shown unchanged: "您无权进行当前操作，原因如下： 对不起，您还未登录，无法进行此操作。" The empty state shows "没有数据". This message is the real counterpart of the synthetic `not-logged-in.html` fixture.
  - Pull to refresh sent one more request.
  - Rotation recreated the screen without a new request, because the ViewModel survived.
  - No crash came from the new code. The ART log line about `kotlin.internal.jdk8.JDK8PlatformImplementations` at start-up is informational and comes from the Kotlin standard library.
- **Pre-existing, app-wide crash on any configuration change (rotation, dark mode, font scale).**
  - Cause: `BaseFragment.onCreate()` calls `setRetainInstance(true)`, and Hilt refuses retained fragments ("onAttach called multiple times with different Context! Hilt Fragments should not be retained."). It has existed since the fragments became `@AndroidEntryPoint` (PR #12).
  - It is reproduced on the unchanged main screen (`ThreadListFragment`).
  - `BlacklistFragment` now opts out (`retainInstance = false`), and `ActivityLaunchTest.settingsBlacklistSurvivesRecreation` covers it. The test fails with this exact crash when the opt-out is removed.
  - The app as a whole still crashes on rotation from the blacklist screen. The swipe-back activities are translucent, so `MainFrameActivity` underneath is recreated as well and crashes.
  - Fixing it for good means removing `setRetainInstance(true)` from `BaseFragment`, and checking each screen that relies on retention (see TD-25). That is a separate change; each screen moved to a ViewModel needs retention no more.

## Decisions

| Date | Decision |
| --- | --- |
| 2026-11-28 | Use MVVM (ViewModel as controller) with a site-adapter layer, adopted step by step |
| 2026-11-28 | Write new layers in Kotlin (PR #15 added Kotlin) with coroutines and `StateFlow` |
| 2026-11-28 | The PoC feature is Blacklist |
| 2026-11-28 | The site adapter lives in its own pure Kotlin/JVM module `:forum` from the start, so the compiler enforces the boundary |
| 2026-11-28 | Model classes are new Kotlin data classes per feature, created as each feature migrates; the old `bean` classes stay until their last user moves |
| 2026-11-28 | The formhash is an adapter detail. APIs fetch and cache it themselves, keyed by the `cdb_auth` cookie so a new login never reuses an old token |

## Open questions

- **Compose or Views for the redesign?** This doesn't affect the layers below the ViewModel, since both consume `StateFlow`.
- **Real fixtures.** The PoC fixtures are synthetic. They were written from the parser's selectors because the tests cannot log in to the forum. Replace them with captured, scrubbed pages when possible.
