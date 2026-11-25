# TD-01: Auth cookies are attached using URL substring matching

Category: Security and account data. Status: open.

**P1 · Probe-confirmed.** `Utils.download()` checks `url.contains(HiUtils.ForumUrlPattern)` before adding `Cookie: cdb_auth=...`. `TextViewWithEmoticon` uses the same substring test to turn rendered post links into download actions. An external URL such as `https://example.invalid/.4d4y.com/forum/attachment.php?aid=1` passes both checks. The local probe confirmed selection of this foreign host; no live cookie was sent.

Source: [Utils.java:529](../../hipda/src/main/java/com/lichader/hipdanext/utils/Utils.java#L529), [TextViewWithEmoticon.java:117](../../hipda/src/main/java/com/lichader/hipdanext/ui/widget/TextViewWithEmoticon.java#L117).

**Improve:** Parse URLs and validate the trusted HTTPS origin and allowed paths before attaching credentials; select cookies for that origin rather than by name alone. **Verify:** Test foreign hosts, matching text in paths/query strings, valid forum downloads, and cross-origin redirects using dummy credentials.

[Review context, evidence definitions, and priorities](README.md).
