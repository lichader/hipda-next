# TD-47: Password representation is guessed from its length

Category: Build, architecture, and product quality. Status: open.

**P2 · Confirmed.** `LoginDialog` stores the entered password directly, but `LoginHelper.processedPassword()` treats every 32-character value as an existing digest and skips hashing. A normal 32-character password therefore follows a different protocol path from other plain passwords.

Source: [LoginDialog.java:79](../../hipda/src/main/java/net/jejer/hipda/ui/widget/LoginDialog.java#L79), [LoginHelper.java:170](../../hipda/src/main/java/net/jejer/hipda/async/LoginHelper.java#L170).

**Improve:** Represent plain passwords versus stored protocol digests explicitly and apply the forum's required transformation consistently; do not infer representation from length. **Verify:** Verify protocol request bodies for 31-, 32-, and 33-character dummy passwords and supported legacy stored credentials.

[Review context, evidence definitions, and priorities](README.md).
