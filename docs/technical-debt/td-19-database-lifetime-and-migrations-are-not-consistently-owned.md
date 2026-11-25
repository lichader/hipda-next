# TD-19: Database lifetime and migrations are not consistently owned

Category: Persistence and input validation. Status: open.

**P2 · Risk.** DAO methods repeatedly close database instances obtained from shared singleton helpers, while some read methods lack the writers' synchronization. This makes concurrent ownership difficult to reason about. `ContentDBHelper.onUpgrade()` also assumes every version increase means adding the username column; `HistoryDBHelper` has no migration logic.

Source: [ContentDao.java:20](../../hipda/src/main/java/com/lichader/hipdanext/db/ContentDao.java#L20), [HistoryDao.java:97](../../hipda/src/main/java/com/lichader/hipdanext/db/HistoryDao.java#L97), [ContentDBHelper.java:43](../../hipda/src/main/java/com/lichader/hipdanext/db/ContentDBHelper.java#L43).

**Improve:** Centralize database ownership, close cursors locally, serialize related operations, and implement version-specific migrations. **Verify:** Run concurrent cleanup/read/write tests and migrate real version-1/version-2 database fixtures without losing drafts/history.

[Review context, evidence definitions, and priorities](README.md).
