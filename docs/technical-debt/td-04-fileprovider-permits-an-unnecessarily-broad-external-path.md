# TD-04: FileProvider permits an unnecessarily broad external path

Category: Security and account data. Status: open.

**P2 · Risk.** `provider_paths.xml` includes `<external-path path="."/>`. The provider is not exported and uses URI grants, so this is not evidence that arbitrary files are already publicly readable. It does increase the impact of an incorrect URI grant. Android recommends narrow shared paths. [FileProvider guidance](https://developer.android.com/privacy-and-security/risks/file-providers).

Source: [provider_paths.xml](../../hipda/src/main/res/xml/provider_paths.xml).

**Improve:** Use dedicated shared-image/cache directories and grant only the required access to individual files. **Verify:** Verify legitimate image sharing and reject URIs outside the intended directories.

[Review context, evidence definitions, and priorities](README.md).
