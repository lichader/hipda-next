# TD-34: Emoji thumbnail ownership is global across composers

Category: Images, memory, and caching. Status: open.

**P2 · Risk.** `EmojiHandler.IMAGE_MAP` stores upload bitmaps globally. Cleaning up any `EmojiPopup` calls global cleanup and recycles all those bitmaps, even if another composer or span still references them. State and cleanup ownership are not tied to an individual composing session.

Source: [EmojiHandler.java:114](../../emoji/src/main/java/com/vanniktech/emoji/EmojiHandler.java#L114), [EmojiPopup.java:336](../../emoji/src/main/java/com/vanniktech/emoji/EmojiPopup.java#L336).

**Improve:** Scope attachment thumbnails to their owner or use shared reference-counted/image-loader resources instead of manually recycling global bitmaps. **Verify:** Keep multiple composing surfaces alive, close one, and verify the other still renders uploaded-image spans.

[Review context, evidence definitions, and priorities](README.md).
