# TD-20: Settings mutate collections returned by SharedPreferences

Category: Persistence and input validation. Status: open.

**P2 · Confirmed.** `setSortByPostTime()` modifies a set obtained directly from `getStringSet()`, and frequency-menu collections are also exposed as mutable objects. Android explicitly requires treating returned sets as immutable. Separate remove/apply and put/apply calls also make individual setting updates unnecessarily non-atomic. [SharedPreferences contract](https://developer.android.com/reference/android/content/SharedPreferences#getStringSet(java.lang.String,%20java.util.Set)).

Source: [HiSettingsHelper.java:504](../../hipda/src/main/java/net/jejer/hipda/bean/HiSettingsHelper.java#L504), [HiSettingsHelper.java:692](../../hipda/src/main/java/net/jejer/hipda/bean/HiSettingsHelper.java#L692).

**Improve:** Copy returned collections, publish immutable snapshots, and use one editor transaction per logical update. **Verify:** Toggle settings repeatedly and concurrently, then restart the process and verify persistence.

[Review context, evidence definitions, and priorities](README.md).
