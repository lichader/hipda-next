# TD-03: Backup rules omit encrypted credentials and session data

Category: Security and account data. Status: open.

**P2 · Risk.** Auto Backup is enabled, but the rules exclude only obsolete Evernote job files. They do not exclude `encrypted_shared_prefs.xml` or define a session-cookie policy. Restoring encrypted preferences without their device key can break account recovery. Android explicitly advises excluding these preferences from backup. [EncryptedSharedPreferences guidance](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences).

Source: [backup_config.xml](../../hipda/src/main/res/xml/backup_config.xml), [AndroidManifest.xml:18](../../hipda/src/main/AndroidManifest.xml#L18).

**Improve:** Define backup rules for credentials, cookies, drafts, history, and temporary uploads; add the newer extraction rules during the target-SDK migration. **Verify:** Test restore onto another device and verify safe reauthentication while preserving intended user data.

[Review context, evidence definitions, and priorities](README.md).
