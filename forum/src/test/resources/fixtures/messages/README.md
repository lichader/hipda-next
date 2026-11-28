# Private message fixtures

- `conversation-real.html`: a conversation with another user (`pm.php?daterange=5&uid=…`), captured on 2026-11-28 with the debug response recorder and scrubbed like the list fixtures. The other user is `user_1` / `200001` everywhere, including the heading and the add-to-blacklist link. The message's HTML is replaced by `消息 1`. The viewing account is `test_user` / `100001` and the token is `a1b2c3d4`.
- `send-refused-real.xml`: the forum's reply to a message the owner sent to their own account. The forum refuses messages to oneself, so this reply holds no personal data.
- `conversation-synthetic.html` and `send-sent-synthetic.xml` are written by hand. A successful send can't be captured without messaging someone else. They cover the account's own messages, unread and avatar-less messages, skipped rows, and a reply holding the new message.
