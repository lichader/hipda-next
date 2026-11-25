# TD-02: Credential storage silently falls back to plaintext

Category: Security and account data. Status: open.

**P1 · Confirmed.** `HiSettingsHelper` assigns ordinary preferences to `mEncrytedSharedPref` on API 21–22 and after encryption initialization errors on newer versions. Passwords, security answers, and saved profiles subsequently use that reference. A keystore failure therefore changes the storage protection without user involvement.

Source: [HiSettingsHelper.java:220](../../hipda/src/main/java/net/jejer/hipda/bean/HiSettingsHelper.java#L220).

**Improve:** Separate credential storage from ordinary preferences. Define an explicit policy for older devices and key failures; preserve recoverable data and request login again instead of silently storing secrets in plaintext. **Verify:** Inject encryption/key initialization failures and verify that no secret is written to ordinary preferences.

[Review context, evidence definitions, and priorities](README.md).
