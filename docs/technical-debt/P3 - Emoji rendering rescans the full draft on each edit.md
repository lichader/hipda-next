# TD-35: Emoji rendering rescans the full draft on each edit

Category: Images, memory, and caching. Status: open.

**P3 · Maintenance.** `EmojiEditText.onTextChanged()` invokes `addEmojis()` for the whole text; that method removes all existing emoji spans and scans the entire draft, including per-character matching work. The attachment matcher also relies on a fixed 32-character lookahead.

Source: [EmojiEditText.java](../../emoji/src/main/java/com/vanniktech/emoji/EmojiEditText.java), [EmojiHandler.java:55](../../emoji/src/main/java/com/vanniktech/emoji/EmojiHandler.java#L55).

**Improve:** Update only affected text ranges with bounded token parsing and preserve spans outside the edit. **Verify:** Measure typing and pasting long emoji-heavy drafts, deletion across token boundaries, and long attachment IDs.

[Review context, evidence definitions, and priorities](README.md).
