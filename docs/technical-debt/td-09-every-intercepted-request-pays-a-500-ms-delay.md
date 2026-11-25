# TD-09: Every intercepted request pays a 500 ms delay

Category: Correctness and user-visible behavior. Status: open.

**P1 · Probe-confirmed.** `RetryIntercepter` starts `tryNum` at 1 and sleeps whenever it is greater than 0, including the first attempt. Both forum and image clients install it. A successful mocked first request invoked the chain once and took 501 ms.

Source: [RetryIntercepter.java:20](../../hipda/src/main/java/com/lichader/hipdanext/okhttp/RetryIntercepter.java#L20), [MyGlideModule.java:79](../../hipda/src/main/java/com/lichader/hipdanext/glide/MyGlideModule.java#L79).

**Improve:** Delay only eligible retries, respect interruption/cancellation, and define a bounded retry policy without retrying unsafe writes blindly. **Verify:** Measure an immediate-success request and test retry limits, cancellation, and POST behavior.

[Review context, evidence definitions, and priorities](README.md).
