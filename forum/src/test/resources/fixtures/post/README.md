# Post editor fixtures

Captured on 2026-11-28 with the debug response recorder, by opening the editors and backing out without posting. One generated test image was uploaded first, so the editors list it as an unused attachment. Scrubbed:
- the viewing account is `test_user` / `100001`; the form token is `a1b2c3d4` and the upload token `0123456789abcdef0123456789abcdef`;
- the quoted user is `user_1` / `200001`, in the text box and the notice fields;
- thread and post IDs are `3000001` and `4000001`, in links and hidden fields;
- the thread title is `主题 1`;
- quoted text and the edited post are `内容 N`; the forum's "回复" and "user_1 发表于 <date>" stay;
- the uploaded image is `9000001`, with its stored and original file names replaced;
- session and Cloudflare codes are zeroed.

| File | Editor |
| --- | --- |
| `editor-reply.html` | reply to the thread (`post.php?action=reply`), with the unused image |
| `editor-reply-to-post.html` | reply to a post (`&reppost=`): quote and notice fields |
| `editor-quote.html` | quote a post (`&repquote=`) |
| `editor-new-thread.html` | new thread in Discovery: thread types |
| `editor-edit.html` | edit one of the account's posts: deletable |
| `upload.txt` | the forum's answer to an image upload |
| `editor-synthetic.html` | written by hand: a selected type, a size in KB, the post's own images and attachments, links that are not attachments |

In W9a the parser was also compared with the old `PrePostAsyncTask` on the raw pages, which weren't committed.
