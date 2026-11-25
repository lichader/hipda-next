# TD-21: Numeric and imported input validation is incomplete

Category: Persistence and input validation. Status: open.

**P2 · Confirmed.** `FragmentUtils` parses digit-only page strings with `Integer.parseInt()` without overflow handling. Glide cache settings can overflow integer MB arithmetic or fail parsing; recent-emoji timestamps and profile JSON also assume usable values. Imported font names are used as filesystem names after only an extension check.

Source: [FragmentUtils.java:106](../../hipda/src/main/java/net/jejer/hipda/ui/FragmentUtils.java#L106), [MyGlideModule.java:51](../../hipda/src/main/java/net/jejer/hipda/glide/MyGlideModule.java#L51), [SettingNestedFragment.java:512](../../hipda/src/main/java/net/jejer/hipda/ui/setting/SettingNestedFragment.java#L512).

**Improve:** Use bounded parsing and long size arithmetic; validate nullable JSON, font basenames/canonical destinations, and import limits. Use locale-independent account keys. **Verify:** Test page `2147483648`, malformed preferences/JSON, oversized cache settings, unusual font names, and Turkish locale account lookup.

[Review context, evidence definitions, and priorities](README.md).
