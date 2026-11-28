# Thread list fixtures

Two first pages of thread lists, captured on 2026-11-28 with the debug response recorder (`docs/capturing-forum-pages.md`):
- `forumdisplay-real.html`: Discovery (`forumdisplay.php?fid=2&page=1`). 75 rows; 38 have picture attachments and 20 have more than one page. No row is pinned and none has a thread type.
- `forumdisplay-bs-real.html`: Buy & Sell (`forumdisplay.php?fid=6&page=1&filter=type&typeid=`). 75 rows, all with a thread type, and 8 pinned.

Both are converted from GBK to UTF-8 and scrubbed:
- The viewing account: `test_user`, uid `100001`, formhash `a1b2c3d4`.
- Every other user: `user_N`, uid `2000NN`, numbered in order of appearance. On Buy & Sell, the moderator and the announcement's author are `user_moderator` and `user_announcer`.
- Every thread title: `主题 N`. Thread IDs and thread types are kept.
- Session and Cloudflare request IDs are zeroed.

Neither page has a poll, so `ThreadListParserTest` covers polls with a small synthetic row.
