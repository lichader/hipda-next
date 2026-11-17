# TD-45: Accessibility and UI text need a dedicated pass

Category: Build, architecture, and product quality. Status: open.

**P2 · Maintenance.** Controls such as composer emoji switches and viewer download/share buttons have no local contentDescription; no setContentDescription calls were found in Java. The composer emoji button is 32 dp. Many dialog labels/messages are hard-coded in Java, and custom gesture/click handlers need keyboard and screen-reader testing. A scan found 41 image widgets without local descriptions, including decorative images that need separate classification.

Source: [fragment_post.xml:109](../../hipda/src/main/res/layout/fragment_post.xml#L109), [activity_image_viewer.xml](../../hipda/src/main/res/layout/activity_image_viewer.xml), [RecyclerItemClickListener.java](../../hipda/src/main/java/net/jejer/hipda/ui/adapter/RecyclerItemClickListener.java).

**Improve:** Label actionable icons, mark decorative images appropriately, enlarge touch targets, extract strings/plurals, and support accessible click actions. **Verify:** Test TalkBack, external keyboard navigation, large fonts, contrast, and the unfolded layout.

[Review context, evidence definitions, and priorities](README.md).
