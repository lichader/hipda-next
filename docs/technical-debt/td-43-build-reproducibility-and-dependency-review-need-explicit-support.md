# TD-43: Build reproducibility and dependency review need explicit support

Category: Build, architecture, and product quality. Status: open.

**P2 · Maintenance.** Required project Gradle settings are ignored, JitPack and flatDir repositories remain broadly configured, the wrapper has no distribution checksum, and there is no dependency locking/verification configuration. Dependencies are old, but this review did not run a CVE or license audit and does not establish specific vulnerabilities from age alone.

Source: [.gitignore](../../.gitignore), [build.gradle:12](../../build.gradle#L12), [gradle-wrapper.properties](../../gradle/wrapper/gradle-wrapper.properties).

**Improve:** Track non-secret project settings, pin the JDK/toolchain, constrain repositories, add dependency/wrapper verification, and review dependency upgrades in compatible groups. **Verify:** Build from a clean checkout and fresh cache in CI; record the resolved dependency graph and audit results.

[Review context, evidence definitions, and priorities](README.md).
