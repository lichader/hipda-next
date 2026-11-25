# TD-10: HTTP error and early-return paths leave responses open

Category: Correctness and user-visible behavior. Status: open.

**P2 · Confirmed.** `OkHttpHelper.getResponseBody()` throws on unsuccessful status before consuming or closing the body. `PrePostAsyncTask` also returns from verification/unsuccessful-response paths without closing its response; image upload has a similar error path. Successful `bytes()`/`string()` consumption is a different case and should not be reported as the same leak.

Source: [OkHttpHelper.java:237](../../hipda/src/main/java/com/lichader/hipdanext/okhttp/OkHttpHelper.java#L237), [PrePostAsyncTask.java:68](../../hipda/src/main/java/com/lichader/hipdanext/async/PrePostAsyncTask.java#L68), [UploadImgHelper.java:205](../../hipda/src/main/java/com/lichader/hipdanext/async/UploadImgHelper.java#L205).

**Improve:** Make response ownership explicit and close responses with try-with-resources on every branch. **Verify:** Repeat unsuccessful and verification-required requests and check resource/connection reuse.

[Review context, evidence definitions, and priorities](README.md).
