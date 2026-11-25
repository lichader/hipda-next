# TD-48: Navigation and keyboard layout depend on legacy window behavior

Category: Build, architecture, and product quality. Status: open.

**P2 · Risk.** Activity Back handling uses legacy overrides, swipe-back reparents the decor and invokes hidden translucency methods while swallowing failures, and emoji/keyboard layout relies on display-height heuristics and manual content resizing. These mechanisms need explicit lifecycle and window-inset ownership on modern Android and resizable devices. The reported swipe-completion flash remains a hypothesis requiring device evidence.

Source: [BaseActivity.java:130](../../hipda/src/main/java/com/lichader/hipdanext/ui/BaseActivity.java#L130), [SwipeUtils.java:85](../../hipda/src/main/java/com/lichader/hipdanext/ui/widget/swipeback/SwipeUtils.java#L85), [EmojiPopup.java:64](../../emoji/src/main/java/com/vanniktech/emoji/EmojiPopup.java#L64), [AndroidBug5497Workaround.java](../../hipda/src/main/java/com/lichader/hipdanext/ui/widget/swipeback/AndroidBug5497Workaround.java).

**Improve:** Centralize navigation callbacks, use supported transition/translucency APIs, and size the composer/emoji popup from current window and IME insets. Follow the separate [flash investigation](../swipe-back-flash.md) before selecting a flash-specific patch. **Verify:** Completed/canceled swipes, system/toolbar Back, draft confirmation, IME restoration, rotation, split screen, and folded/unfolded displays.

[Review context, evidence definitions, and priorities](README.md).
