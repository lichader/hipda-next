# TD-05: Logging and cleartext policies need explicit boundaries

Category: Security and account data. Status: open.

**P2 · Risk.** The debug interceptor logs full request and response headers, including possible Cookie/Set-Cookie values. `PostHelper` logs raw failure responses through `Logger.e`, which is enabled in release builds. The manifest also allows cleartext traffic globally, although the default forum endpoint is HTTPS.

Source: [LoggingInterceptor.java](../../hipda/src/main/java/net/jejer/hipda/okhttp/LoggingInterceptor.java), [PostHelper.java:227](../../hipda/src/main/java/net/jejer/hipda/async/PostHelper.java#L227), [Logger.java](../../hipda/src/main/java/net/jejer/hipda/utils/Logger.java), [AndroidManifest.xml:26](../../hipda/src/main/AndroidManifest.xml#L26).

**Improve:** Redact authentication headers and private response bodies. Inventory required HTTP image hosts and constrain exceptions through network security configuration. **Verify:** Inspect debug/release logs with dummy accounts and test existing mixed-content posts before changing HTTP policy.

[Review context, evidence definitions, and priorities](README.md).
