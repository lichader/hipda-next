# User profile fixtures

Captured from the forum on 2026-11-28 with the debug response recorder (`docs/capturing-forum-pages.md`), converted from GBK to UTF-8 and scrubbed:
- The viewing account: `test_user`, uid `100001`, formhash `a1b2c3d4`.
- The profiled users: `user_1` (uid `100002`) and `user_2` (uid `100003`), including the uid-derived avatar paths.
- Personal fields in `#baseprofile` (gender, location, QQ, MSN, website, 旺旺) are replaced with placeholders.
- Dates, post counts, credits and online hours are replaced with fixed values.
- The Cloudflare per-request ID and timestamp are zeroed.

| File | Page |
| --- | --- |
| `profile-online-real.html` | `space.php?uid=` of a user who is online (`online_buddy.gif` in the title) |
| `profile-offline-real.html` | A user who hides their status (上次访问: 隐身), with an avatar |
