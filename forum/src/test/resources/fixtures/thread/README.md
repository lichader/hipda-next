# Thread fixtures

- `thread-real.html`: a real thread page (`viewthread.php`, 12 posts, 3 inline image attachments), captured on 2026-11-28 with the debug response recorder and scrubbed:
  - every user is `user_N` / `2000NN`, including avatar paths, quote headings and "回复 N#" links;
  - each author's sidebar keeps only the author link, dropping profile details (points, dates, online time, titles);
  - post text and signatures are `内容 N`, keeping the markup;
  - links and images to other sites point at `example.invalid`, and attached image file names are `附件 N.jpg`;
  - the title is `主题 1`, the viewing account `test_user`, the token `a1b2c3d4`, and session and Cloudflare codes are zeroed.
- `thread-synthetic.html` (page 2 of 3) and `thread-poll-synthetic.html` are written by hand from the parser's selectors. They cover every branch the real pages didn't: styles and nested font colors, links, emails and URLs in text, each image kind, span and list attachments, tables, lists, unknown tags, videos, quotes, "回复 N#" links, locked and empty posts, post status, warnings and a multiple-choice poll.

In W10a the parser was also compared with the old `HiParserThreadDetail` on 18 raw pages that weren't committed.
