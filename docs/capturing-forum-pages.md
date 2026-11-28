# Capturing forum pages for test fixtures

Parser and API tests need real forum pages, but the tests can't log in. Debug builds can record the responses the app receives while someone uses it, and the recorded pages then become fixtures under `forum/src/test/resources/fixtures/`.

## What is recorded

`ResponseRecorder` (`hipda/src/debug/`) is an interceptor on the app's HTTP client.
- **Debug builds only.** Release builds don't contain it.
- **Off by default.** It records only while the directory `files/forum-captures/` exists in the app's private storage.
- **Text responses only:** HTML pages, `inajax` XML replies, JSON and plain text. Each is saved exactly as received, still GBK-encoded, and `index.tsv` lists each file with its method, status, content type and URL.
- **Never recorded:** request bodies (so no passwords or post text), headers and cookies. Images and other binary responses are skipped, and avatars and post images aren't requested through this client at all.

## Using it

With a debug build installed and logged in on a device or emulator:

```sh
scripts/forum-captures.sh start                  # turn recording on
# use the app: open the screens whose pages you need
scripts/forum-captures.sh pull /tmp/captures     # copy the files to this machine
scripts/forum-captures.sh stop                   # turn recording off, delete the files on the device
```

File names start with the time and a sequence number, then the method and the URL path, for example `20261128-131500-0003-GET-forum_pm.php_action=viewblack.html`. When testing with the owner's account, follow the account rules in the plan's [to-do list](architecture-layering-plan.md#next-work): only actions that affect the owner.

## From capture to fixture

Recorded pages are **personal data**: the account's username and uid, form tokens in URLs and pages, other users' names, and private messages. Keep raw captures outside the repository, and never commit them.

1. Convert the page to UTF-8: `iconv -f GBK -t UTF-8 page.html > page.utf8.html`.
2. Replace the personal values, then check that none are left. Use the same placeholders as the existing fixtures:
   - the account's username → `test_user`;
   - its uid → `100001`;
   - each formhash → `a1b2c3d4`;
   - other users' names → `user_1`, `user_2`, …
   - replace the text of private messages and the account's own posts entirely.
3. Save it in `forum/src/test/resources/fixtures/<feature>/` with `-real` in its name. Describe it in that folder's README: where it came from, the date, and what was replaced.
