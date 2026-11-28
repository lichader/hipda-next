# Login fixtures

Files with `-real` in the name were captured on 2026-11-28 with the debug response recorder (`docs/capturing-forum-pages.md`), during the app's automatic login. They are converted from GBK to UTF-8 and scrubbed: username `test_user`, uid `100001`, formhash `a1b2c3d4`, session IDs `sid=abcdef`, and zeroed Cloudflare request IDs.

The others are synthetic. Capturing a refused login would need a deliberate wrong password on the owner's real account, which counts toward the forum's lockout.

| File | Source | Page |
| --- | --- | --- |
| `login-form-real.html` | Captured | `logging.php?action=login`, the login form with its `formhash` |
| `login-success-real.xml` | Captured | The `inajax` reply to a successful login: "欢迎您回来 <group> <username>" |
| `login-failed.xml` | Synthetic | The real reply with Discuz's refusal text, "登录失败，您还可以尝试 4 次" |
| `login-locked.xml` | Synthetic | The real reply with a lockout notice that is neither success nor "登录失败" |
| `login-form-unavailable.html` | Synthetic | A notice page (`div.alert_info`) in place of the form |
