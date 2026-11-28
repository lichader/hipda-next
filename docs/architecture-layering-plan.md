# Architecture layering plan

Status: **in progress**, started on 2026-11-28. The **Blacklist PoC was merged in #16**. Next: the [to-do list](#next-work), from the top.

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
7. **Legacy Java callers that can't call `suspend` functions get a thin Kotlin bridge** that launches on the `@ApplicationScope` scope (for example `async/SmsSender` and `async/FavoriteHelper`). A bridge is deleted when its last caller gets a ViewModel.
8. **No fragment is retained** (`setRetainInstance`), because Hilt crashes retained fragments on configuration changes. A screen's state lives in its ViewModel, or in saved instance state for legacy screens. Activities add their fragment only when `savedInstanceState` is null, so a restored fragment is kept. `ActivityLaunchTest` recreates every screen; new screens get a test there too.
9. **Pages that need a session are fetched with `SessionGuard.page()`.** If a page comes back logged out, the guard logs in again once and fetches it again; form submissions are not retried. A repository that stores a result checks `AccountRepository.generation` before and after the request, and drops the result if the account changed (TD-22). The guard's own login doesn't change the generation: it renews the stored account's session.

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

## Next work

This is the project's to-do list. Work through **Open** from the top, one item per worktree, branch and PR. Update **Status** while working, and move an item to **Done** when its PR is merged. Add new findings here instead of leaving them in results or notes.

**Forum account.** The only account is the owner's real one. On the `hipda-api37` emulator, an agent may do anything that affects only the owner:
- read pages, including the owner's own private messages (scrub them before committing anything);
- add or remove favorites and blacklist entries;
- send a private message to the owner's own account. The forum refuses it (您不能给自己发消息), which still checks the send path up to the forum's answer; checking a message that arrives is the owner's job;
- log out and in;
- upload images without posting them.

Never post, edit or delete posts, vote, or message anyone else. For those steps, give the owner a short checklist like W2's.

### Open

| ID | Item | Needs from the owner | Status |
| --- | --- | --- | --- |
| W7-check | **Owner checks private messages** (W7, #32): send a message that arrives, reply in a conversation, clear a conversation. The forum refuses messages to oneself, so an agent can't check these | The owner sends and clears; the steps are in #32 | Open |
| W9 | Migrate **posting and image upload** (`PrePostAsyncTask`, `PostJob`, `ImageUploadJob`) | The owner does every step that posts | Not started |
| W10 | Migrate **thread detail**; keeps the reading position across rotation ([W1 result](#w1-result)) | The owner votes, if a poll needs checking | Not started |
| W12 | **Remove the old plumbing** (phase 5): EventBus, read jobs, `AsyncTask`s, `HiUtils` URL statics, `SingletonEntryPoint` callers, the last bridges | – | Not started |
| D1 | **Decide:** Compose or Views for the redesign | Decision, before the first redesigned screen | Open |
| S1 | Lint for `:forum`: API-level checks against the app's minSdk. **Needs a decision** (found 2026-11-28): Android lint skips API checks (NewApi) in plain JVM modules, even with `checkDependencies`; a probe calling an API 24 method was flagged in app code but not in `:forum`. Options: check `:forum`'s bytecode against Android API 21 signatures with the Animal Sniffer plugin (as OkHttp does), or make `:forum` an Android library with minSdk 21 | The owner picks an option | Open |

### Done

| ID | Item | Result |
| --- | --- | --- |
| PoC | Blacklist on the new layers | #16; [PoC](#poc-blacklist) |
| T1 | Response recorder for capturing real pages (debug builds) | #24; [how to use it](capturing-forum-pages.md) |
| W3 | User info: profile and blacklist menu on the new layers; parity with the old parser checked on real pages | #25 |
| W4a | Login layer: `:forum` `AuthApi` and `AccountRepository` with an account generation; `LoginHelper` is a bridge. Refused-login replies are synthetic fixtures, because capturing one needs a wrong password on the owner's account | #26 |
| W4b | Session recovery: `SessionGuard` logs in again once when a page comes back logged out (blacklist and user info use it); automatic logins are single-flight; the blacklist is not stored across an account change (TD-22) | #27; #28 fixed automatic logins counting as an account change |
| W5 | Thread list on the new layers. Parity with the old parser checked on real Discovery and Buy & Sell pages. Java screens collect ViewModel flows with `FlowObservers.collect()`, and `PageHeader` passes the header's notice count to `NotiHelper` | #29 |
| W6a | Simple lists on the new layers: my threads and replies, messages, notices, new posts, favorites and attention, history. Parity with the old parsers checked on eight real pages and two hand-written ones. The list type (favorites or attention) survives recreation | #30 |
| W6b | Search and a user's threads on the simple list view model. Parity checked on real title, full-text and user-thread pages; search terms stay GBK. A search and its results survive recreation. `SimpleListJob` now serves only the message conversation | #31 |
| W7 | Private messages on the new layers: the conversation through `ConversationViewModel`, sending through the `SmsSender` bridge (rule 7) for the Java screens. Parity checked on a real conversation and the forum's real refusal. Removes `SimpleListJob` and `PostSmsAsyncTask`; the list types moved to `SimpleListTypes` | #32 |
| W8 | Favorites on the new layers: adding and removing through `FavoritesRepository`, the device's copy in `FavoritesCache`, and `FavoriteHelper` as a bridge (rule 7) for the thread and list screens. Parity checked on the forum's real replies. Logout now clears the copy in memory too | #33 |
| S5 | Version comparison (TD-46): `UpgradeMigration.isNewer()` compares the dot-separated numbers in order, so `5.1.0` is newer than `5.0.05` and an upgrade from it doesn't re-run the 5.0.03 migration | #34 |
| S6 | Refreshing new posts starts a new search instead of reusing the first page's `searchid`, so it shows posts made since | #35 |
| S2 | Real blacklist add and remove replies as fixtures, captured through the app with the recorder; they replace the synthetic success reply. The rejected reply stays synthetic: the app always sends a valid token | #36 |
| W11 | Notifications and daily tasks on the new layers: `NotiHelper` reads the new-messages page through `NewMessagesCheck` (rule 7 bridge) and the site adapter; `TaskHelper` is Kotlin and syncs the blacklist through its repository, which removes the `BlacklistHelper` bridge; the image host comes from `SiteApi` (`config.php`). A new-messages page without new messages now sets the count to 0 | #37 |
| W1 | Fix the app-wide crash on configuration changes | #18; [W1 result](#w1-result) |
| W2 | Check the Blacklist PoC with a logged-in account | #19; [W2 result](#w2-result) |
| D2 | Decide about the dead update check | 2026-11-28: remove `UpdateHelper` (S4); the owner plans a new update mechanism |
| S3 | Remove the fake report-post action (D3, TD-08) | #22 |
| S4 | Remove `UpdateHelper` (D2); the settings migration moved to `UpgradeMigration` | #21 |
| D3 | Decide about report post | 2026-11-28: remove the fake action (S3). A working one is a Backlog task in the owner's "Hi-pda next v2" GitHub project; the forum does have a report action (`misc.php?action=report`), as a page captured with T1 showed |

### W1. Fix the app-wide crash on configuration changes

**Symptom.** The app crashes on rotation, a dark mode switch, a font size change or any other configuration change: "IllegalStateException: onAttach called multiple times with different Context! Hilt Fragments should not be retained." On 2026-11-28 it was reproduced on the API 21 emulator by rotating the main screen (`Hilt_ThreadListFragment.onAttach`). It has existed since the fragments became `@AndroidEntryPoint` (#12).

**Cause.** `BaseFragment.onCreate()` calls `setRetainInstance(true)` for every subclass, and Hilt refuses retained fragments.
- **Affected fragments:** eight Hilt fragments extending `BaseFragment` are still retained: `AboutFragment`, `PostFragment`, `SearchFragment`, `SimpleListFragment`, `SmsFragment`, `ThreadDetailFragment`, `ThreadListFragment` and `UserinfoFragment`. `BlacklistFragment` already opts out (rule 8).
- **Why it reaches every screen:** the swipe-back activities (`ThreadDetailActivity`, `PostActivity`, `SimpleListActivity`, `SmsActivity`, `UserInfoActivity`, `SettingActivity`) are translucent. A configuration change also recreates the activities below them, so one retained fragment anywhere in the back stack crashes the app. That is why rotating on the migrated blacklist screen still crashes.

**Plan.**
1. **Failing tests first.** Extend `ActivityLaunchTest` so every screen is also recreated (`scenario.recreate()`), as `settingsBlacklistSurvivesRecreation` does. These tests fail today with the Hilt error.
2. **Remove retention.** Delete `setRetainInstance(true)` from `BaseFragment.onCreate()`. Remove the opt-out in `BlacklistFragment` and rule 8, which are then redundant.
3. **Keep in-flight results.** `BaseFragment.onCreate()` gives every instance a new `mSessionId`, and job results arrive as sticky EventBus events filtered by that ID. Without retention, results of requests started before the change would be dropped and could leave a screen loading forever. Save `mSessionId` in `onSaveInstanceState()` and restore it in `onCreate()`, so the recreated fragment still receives them.
4. **Check what each screen loses.** Only `PostFragment` saves its own state (`onSaveInstanceState`). The other seven reload their data after a configuration change, and may lose positions:
   - thread detail: page cache, goto floor, author-only mode, quick reply;
   - thread list and simple lists: list and page;
   - search: query and results;
   - SMS: the conversation.
   Reloading is acceptable as a first fix, because it replaces a crash. Save small state, such as the page or the query, where losing it is annoying. The real fix for state is each screen's ViewModel (phase 4).
5. **Rejected alternative:** `android:configChanges` on the activities. It hides recreation but stops resources from reloading for dark mode, font scale and landscape layouts, and the app would still break when the system recreates activities.

**Verify.**
- `./gradlew :forum:test :hipda:testDebugUnitTest`, including the new recreation tests.
- On the `hipda-api21` and `hipda-api37` emulators, on every screen and in a deep swipe-back stack (main → thread → user info → private message): rotate, toggle dark mode, and change the font size. Repeat with the developer option "Don't keep activities" on.
- Confirm that a request in progress during the change still finishes on the recreated screen.

Fixing W1 also resolves most of [TD-25](technical-debt/td-25-retained-fragments-outlive-their-view-and-activity-references.md); update that file in the same PR.

#### W1 result

Done on 2026-11-28 in #18.

**Changes**
- `BaseFragment` no longer calls `setRetainInstance(true)`. It saves `mSessionId` in `onSaveInstanceState()` and restores it in `onCreate()`. `BlacklistFragment`'s own opt-out is removed.
- `SimpleListActivity` and `SmsActivity` replaced their fragment in every `onCreate()`, even when it had just been restored. They now add it only when `savedInstanceState` is null, like the other activities.
- `ActivityLaunchTest.assertLaunches()` now recreates every screen after launching it. It checks that the screen is still injected and that each `BaseFragment` keeps its session ID. This replaces `settingsBlacklistSurvivesRecreation`.

**Tests**
- Before the fix, 8 of the 10 screen tests failed: seven with "Hilt Fragments should not be retained", and the blacklist with a changed session ID. The first run with the fix also caught the `SimpleListActivity`/`SmsActivity` replacement.
- After the fix, 68/68 pass (26 `:forum`, 42 app). Debug and release builds pass; lint finds nothing in the changed files.

**Device checks (2026-11-28).** Each step was checked for an app crash by process ID and log, and a recreation by the activity's identity hash in `dumpsys activity top`.
- **`hipda-api21` (logged out):**
  - main screen: rotation both ways;
  - main → settings → 论坛选项 → 黑名单: rotation both ways, then Back through all screens;
  - history list and About: rotation;
  - "Don't keep activities" on: `dumpsys` confirmed the three screens below the blacklist were destroyed with saved state, and Back rebuilt each one with its content.
  - Changing `font_scale` through `settings` has no effect on API 21, so font scale was not tested there.
- **`hipda-api37`** (logged in; read-only actions only):
  - main screen: rotation, dark mode and font scale, each both ways, each recreating the activity;
  - a thread rotated half a second after opening, so the page was still loading: it loaded and showed on the recreated screen;
  - main → thread → user info: rotation, dark mode and font scale, both ways; light and dark rendering correct; Back rebuilt the thread with content, then main;
  - search: rotation and dark mode. The same `SearchFragment` instance was kept.
- No app crash in any of these steps. All emulator settings were restored to their recorded values.
- Not tested on a device: the post editor and private messages, to keep the logged-in account read-only. Both are covered by the recreation tests in `ActivityLaunchTest`.

**What a configuration change still loses** (no crash; fix with each screen's ViewModel):
- **Thread detail** reloads from its arguments, so the reader returns to the page the thread was opened on, not the page they were reading. Author-only mode switched on later is also lost.
- **Search** loses text typed into the search box but not yet submitted, because the box lives in the toolbar menu, which is rebuilt. (Since W6b the submitted search and its results are kept.)
- Nothing in the lists, private messages or user info reloads any more: the thread list keeps its data since W5, the simple lists and search since W6, the conversation since W7, and user info since W3.
- **Post editor:** typed text is restored by its views, and the draft is kept, being keyed by session ID. The thumbnails of images uploaded in this session are lost, but the forum lists those images as unused attachments, and the editor already offers to add them again.

### W2. Check the Blacklist PoC with a logged-in account

**Why.** The PoC was checked only while logged out (PoC step 14). The listing of real entries, the remove and add requests, the formhash handling across sessions and the `BlacklistHelper` bridge have been tested only against synthetic pages. Agents and tests cannot log in, so the account owner has to do this. Use a test account and a throwaway username where possible, because the steps change the forum's blacklist.

**Checklist** (debug build of current `master`):
1. **Open the list:** settings → 论坛选项 → 黑名单.
   - The list matches the forum's own page (`pm.php?action=viewblack` in a browser).
   - "黑名单数据已同步" appears.
   - Back in settings, the summary shows the right count and the new sync time.
2. **Pull to refresh:** the list reloads once.
3. **Remove a user:**
   - The remove button hides while the request runs, and the row then disappears.
   - The settings count drops.
   - The user's threads appear again in thread lists.
4. **Failed removal:** turn on airplane mode and remove a user. "请检查网络连接" appears and the remove button returns.
5. **From user info:** open a user's page and use the menu.
   - "加入黑名单" shows "已经将用户 <name> 添加至黑名单", and the menu switches to remove.
   - Removing shows "已经将用户 <name> 从黑名单移除".
   - The forum's web page agrees.
6. **New session:** log out and log in again, as another account if possible, then add and remove once more. Both must work; `FormhashCache` must not reuse the old session's token.
7. **Background sync:** the app syncs the blacklist through `BlacklistHelper.syncBlacklists()` right after every login (`TaskHelper.runDailyTask(true)`). It also syncs at start-up when the last sync is more than 24 hours old. After logging in, the sync time in the settings summary updates.
8. **Real fixtures:**
   - From a logged-in browser, save the blacklist page with entries and when empty, and the replies to an add and a remove that succeeded and that failed.
   - Convert them to UTF-8 (`iconv -f GBK -t UTF-8`) and scrub usernames, uids and formhash values.
   - Replace the synthetic pages in `forum/src/test/resources/fixtures/blacklist/` and update its README.
   - `./gradlew :forum:test` must still pass. Where it fails, the real markup differs from the assumptions, and the parser needs a fix.

**Record** the results in PoC step 14 and the PoC notes. Fix anything found in a separate PR.

#### W2 result

Done on 2026-11-28. The account owner ran the checklist on the `hipda-api37` emulator with the `master` debug build `8d58a009`, and the app's log was recorded at the same time.

**Steps 1–9 all passed.** The log confirms each one:
- **Viewing:** 7 blacklist requests, all successful except one in airplane mode, which failed with "Failed to connect" as step 4 expects.
- **Adding:** 3 successful `addblack` requests from user info, two of them after a new login.
- **Removing:** 2 successful `delblack` requests, one from the blacklist screen and one from user info, which goes through the `BlacklistHelper` bridge.
- **New session:** a new login at 13:01 was followed by the automatic blacklist sync (step 7). The adds and removes after it used the new session's form token (step 8).
- **Stability:** no app crash.

On the first pass the log showed no removal from user info; it was repeated, and the log then showed it (13:11).

**Real fixtures (step 8)**
- `viewblack-real.html` and `viewblack-empty-real.html` are captured, converted to UTF-8 and scrubbed.
- The parser, the form-token reader and the full API pass on both, including the GBK-encoded form.
- The real markup matches the parser's assumptions, and an empty list says "暂无数据". See the fixtures' README.

**Not captured**
- The forum's replies to add and remove: the blacklist page has no add form, so they couldn't be saved from a browser. S2 captured them through the app with the recorder (`addblack-real.html`, `delblack-real.html`); only the rejected reply stays synthetic.
- A rejected request, such as adding a user that doesn't exist, is still covered only by a synthetic page.

## Status

### Feature migration tracker

| Feature | Screens and callers | Old path | Status |
| --- | --- | --- | --- |
| Blacklist | `BlacklistFragment`; `UserinfoFragment` (add/remove); `TaskHelper` (daily sync) | `BlacklistHelper` + inline Jsoup in fragment | **Migrated (PoC, #16)**, checked with a logged-in account ([W2](#w2-result)); the daily sync uses the repository since W11 (#37) |
| User info | `UserinfoFragment` | `asyncGet` + `HiParser.parseUserInfo` in fragment; `SimpleListJob` for threads | **Migrated (W3, #25)**; its threads list in W6b (#31); its send dialog moves with W7 |
| Thread list | `ThreadListFragment` | `ThreadListJob` + EventBus | **Migrated (W5, #29)** |
| Simple lists, search | `SimpleListFragment`, `SearchFragment` | `SimpleListJob` (11 types) + EventBus | **Migrated (W6a #30, W6b #31)** |
| Private messages | `SmsFragment`, `BaseFragment` SMS dialog | `SimpleListJob`, `PostSmsAsyncTask`, `asyncGet` | **Migrated (W6a list, W7 #32)**; sending goes through the `SmsSender` bridge until the screens get view models for it |
| Favorites | Thread detail menu, lists | `FavoriteHelper` | **Migrated (W6a lists, W8 #33)**; `FavoriteHelper` is a bridge until thread detail (W10) gets a view model |
| Posting, upload | `PostFragment`, quick reply, vote, delete | `PrePostAsyncTask`, `PostJob`, `ImageUploadJob` | Not started (W9) |
| Thread detail | `ThreadDetailFragment` | `ThreadDetailJob` + EventBus, `asyncGet` for warnings | Not started (W10) |
| Login, session | `LoginDialog`, jobs' relogin | `LoginHelper` | **Migrated (W4a, W4b)**. `LoginHelper` bridges the Java callers. The jobs keep their own relogin until their features migrate, but it now shares the single-flight login |
| Notifications | `NotiWorker`, parser side effects | `NotiHelper` | **Migrated (W11, #37)**; the page header's notice count reaches `NotiHelper` from each migrated screen; thread detail still passes its page (W10) |

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
| 14 | Device check: open the blacklist, pull to refresh, remove a user; add/remove from user info | **Done.** Logged out on the API 21 emulator (see [PoC notes](#poc-notes)); logged in on the API 37 emulator in [W2](#w2-result) |

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
  - Fixed for the whole app by [W1](#w1-fix-the-app-wide-crash-on-configuration-changes). `BlacklistFragment`'s opt-out has been removed with the rest of the retention.

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

- **Compose or Views for the redesign?** Tracked as D1 in the [to-do list](#next-work). It doesn't affect the layers below the ViewModel, since both consume `StateFlow`.
- **Real fixtures.** The blacklist now has captured, scrubbed pages ([W2](#w2-result)); its add and remove replies are still synthetic. Each later feature should start from captured, scrubbed pages (phase 0), saved by the account owner from a browser.
