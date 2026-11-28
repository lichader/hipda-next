# Blacklist page fixtures

Pages for the blacklist parser and API tests. Files with `-real` in the name are captured from the forum. Captured pages are converted from GBK to UTF-8, and their usernames, uids and formhash values are replaced: `test_user`, `100001`, `blocked_user_1` and `a1b2c3d4`. The `<meta charset=GBK>` line is left as captured; tests parse the text, so it has no effect.

The other files are synthetic, written from the markup the parser selects (`div.blacklist`, `ul.commonlist a[href*=space.php]`, `input[name=formhash]`, `div.alert_error`). They cover cases the captured pages do not, such as duplicates, links without a username, and error pages.

| File | Source | Page |
| --- | --- | --- |
| `viewblack-real.html` | Captured 2026-11-28 | `pm.php?action=viewblack` with one user |
| `viewblack-empty-real.html` | Captured 2026-11-28 | The same page when the list is empty |
| `list.html` | Synthetic | The same page with several users, a duplicate link and a link without a username |
| `empty.html` | Synthetic | The page when the list is empty |
| `not-logged-in.html` | Synthetic | The forum's error page, shown when the session has expired |
| `unexpected.html` | Synthetic | A page without the blacklist container |
| `unmarked-empty.html` | Synthetic | A blacklist container that lists no users and does not say it is empty |
| `addblack-real.html` | Captured 2026-11-28 | Reply to `pm.php?action=addblack` that succeeded: 指定用户的消息已被屏蔽 in `div.alert_right` |
| `delblack-real.html` | Captured 2026-11-28 | Reply to `pm.php?action=delblack` that succeeded: 屏蔽设置已移除 |
| `submit-error.html` | Synthetic | Reply to `pm.php?action=addblack` or `delblack` that the forum rejected. The app always sends a valid token, so a rejection can't be captured through it |

What the real page showed (2026-11-28):
- `div.blacklist` holds a form with the formhash, for the "accept or block all messages" setting, then `<ul class="commonlist inlinelist">`.
- Each entry is a `space.php?username=` link followed by a "移除" link, `pm.php?action=delblack&formhash=…&user=…`, shown on hover.
- The page has no form for adding a user; the app posts to `pm.php?action=addblack` directly.
- An empty list is a single `<li>暂无数据</li>` in the same `ul.commonlist`, which is the text the parser looks for.
- Replies to add and remove can't be saved from a browser, because the page has no add form. Since S2 they are captured through the app with the debug response recorder (`addblack-real.html`, `delblack-real.html`); only the rejected reply, `submit-error.html`, stays synthetic.
