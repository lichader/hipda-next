# TD-11: ProfileComparator violates the comparator contract

Category: Correctness and user-visible behavior. Status: open.

**P2 · Probe-confirmed.** `compare(a, a)` returns -1, and equal timestamps also violate ordering symmetry. The actual class, compiled without Android, allowed inserting the same key twice into a `TreeMap`, leaving two entries. The account header uses this comparator, although that exact duplicate insertion has not been observed in the UI.

Source: [ProfileComparator.java:13](../../hipda/src/main/java/net/jejer/hipda/bean/ProfileComparator.java#L13), [MainFrameActivity.java:875](../../hipda/src/main/java/net/jejer/hipda/ui/MainFrameActivity.java#L875).

**Improve:** Compare timestamps safely and use a stable username tie-breaker; identical keys must compare as zero. **Verify:** Check reflexivity, antisymmetry, equal timestamps, sorting, and repeated map insertions.

[Review context, evidence definitions, and priorities](README.md).
