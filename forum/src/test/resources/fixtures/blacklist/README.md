# Blacklist page fixtures

Synthetic pages. They follow the markup the parser selects (`div.blacklist`,
`ul.commonlist a[href*=space.php]`, `input[name=formhash]`, `div.alert_error`) in the
forum's Discuz 7 layout, because the tests cannot log in to the forum. Replace them with
captured pages once available, with usernames, uids and formhash values scrubbed.

| File | Page |
| --- | --- |
| `list.html` | `pm.php?action=viewblack` with users, a duplicate link and a link without a username |
| `empty.html` | The same page when the list is empty |
| `not-logged-in.html` | The forum's error page, which is shown instead when the session has expired |
| `unexpected.html` | A page without the blacklist container |
| `unmarked-empty.html` | A blacklist container that lists no users and does not say it is empty |
| `submit-ok.html` | Reply to `pm.php?action=addblack` or `delblack` that succeeded |
| `submit-error.html` | Reply to `pm.php?action=addblack` or `delblack` that the forum rejected |
