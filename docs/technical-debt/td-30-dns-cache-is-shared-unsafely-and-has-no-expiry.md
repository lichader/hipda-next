# TD-30: DNS cache is shared unsafely and has no expiry

Category: Images, memory, and caching. Status: open.

**P2 · Risk.** `CachedDns` uses a static unsynchronized HashMap from concurrent clients and a lazy singleton without safe publication. Records have no TTL and are cleared only when the main activity is destroyed, so changing networks or server addresses may retain stale results.

Source: [CachedDns.java:17](../../hipda/src/main/java/net/jejer/hipda/okhttp/CachedDns.java#L17), [HiApplication.java:146](../../hipda/src/main/java/net/jejer/hipda/ui/HiApplication.java#L146).

**Improve:** Prefer system DNS behavior unless caching is justified; otherwise use bounded thread-safe entries with expiry and network-change invalidation. **Verify:** Run concurrent lookups/clears and simulate changed address records and Wi-Fi/mobile transitions.

[Review context, evidence definitions, and priorities](README.md).
