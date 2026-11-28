# Simple list fixtures

Captured on 2026-11-28 (the searches in W6b) with the debug response recorder (`docs/capturing-forum-pages.md`), converted from GBK to UTF-8 and scrubbed:

| File | Page | Rows |
| --- | --- | --- |
| `my-replies-real.html` | `my.php?item=posts` | 75 replies, 4 pages |
| `my-threads-real.html` | `my.php?item=threads` | 5 threads |
| `favorites-real.html` | `my.php?item=favorites&type=thread` | 71 threads |
| `attention-real.html` | `my.php?item=attention&type=thread` | 5 threads |
| `messages-real.html` | `pm.php?filter=privatepm` | 5 conversations, without summaries |
| `notices-empty-real.html` | `notice.php` | none: the account had no notices |
| `new-posts-p1-real.html`, `new-posts-p2-real.html` | `search.php?srchfrom=86400`, then page 2 by `searchid` | 75 each, 5 pages |
| `search-title-p1-real.html`, `search-title-p2-real.html` | a title search for "Android" in Buy & Sell, pages 1 and 2 | 50 and 49, 4 pages |
| `search-fulltext-real.html` | a full-text search for "Android" in Buy & Sell | 50 posts, 20 pages |
| `user-threads-real.html` | a user's threads (`srchuid`, redirected to a `searchid`) | 40 threads, 1 page |

Scrubbing:
- The viewing account: `test_user`, uid `100001`, formhash `a1b2c3d4`.
- Every other user: `user_N`, uid `2000NN`, numbered in order of appearance per page. The uid is replaced everywhere it appears, including avatar paths and form values.
- Thread titles: `主题 N`; reply text: `回复 N`; full-text results' post text: `内容 N`. Forum names and the search term are kept.
- Session, Cloudflare request and Cloudflare email-protection codes are zeroed.

`notices-synthetic.html` and `messages-synthetic.html` are written by hand from the parsers' selectors, because the account had no notices and no unread message, and making them would need other people to act. They cover each notice kind (reply to a thread, reply to a followed thread, quote, reply, system and friend notices) and the rows the parsers skip.
