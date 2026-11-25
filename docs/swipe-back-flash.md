# Brief Flash After Swipe Back

Status: user-reported; root cause unconfirmed. Investigated on 2026-11-17.

## Reported Behavior

A brief flash appears immediately after sliding a page to the right to return to the main screen. The reporter clarified that the effect occurs after the page closes, rather than as overlapping content during the drag.

| Environment | Reported value |
| --- | --- |
| Device | Pixel 11 Pro Fold |
| Display state | Unfolded |
| Android version | Android 17 |
| Installed app version | Not recorded |
| Exact originating page, theme, and system navigation mode | Not recorded |
| App screen-orientation setting (**屏幕方向**) and whether the app is letterboxed | Not recorded |
| Frequency and folded-display comparison | Not recorded |

## Reproduction

1. Open the app and navigate from the main screen to another page.
2. Slide that page to the right to return to the main screen.
3. Observe the final frames immediately after the page closes.

Expected: the main screen remains visible through a smooth transition.

Actual: a brief flash is visible at the end of the return transition.

These steps describe the user's report. The issue has not been reproduced from this checkout. No device was connected through ADB during investigation.

## Confirmed Code Findings

The inspected thread-detail path uses `ThreadDetailActivity -> SwipeBaseActivity -> BaseActivity`. It uses the same custom swipe mechanism on tablets; the tablet check in `ThreadDetailActivity` adjusts the floating button size. Confirm the exact affected page before treating this as the reporter's runtime path.

- [SwipeBaseActivity](../hipda/src/main/java/com/lichader/hipdanext/ui/SwipeBaseActivity.java) installs the swipe helper when `isGestureBack()` is enabled and makes the touch edge span the screen width.
- [SwipeBackActivityHelper](../hipda/src/main/java/com/lichader/hipdanext/ui/widget/swipeback/SwipeBackActivityHelper.java) makes the window background transparent, clears the decor background, and requests translucency during edge interaction.
- [SwipeBackLayout.attachToActivity()](../hipda/src/main/java/com/lichader/hipdanext/ui/widget/swipeback/SwipeBackLayout.java) reparents the window's decor child into the draggable layout and applies the theme background to that child.
- `SwipeBackLayout.onViewPositionChanged()` calls `mActivity.finish()` when swipe progress reaches 1, then calls `overridePendingTransition(0, 0)`.
- [BaseActivity.finish()](../hipda/src/main/java/com/lichader/hipdanext/ui/BaseActivity.java) first calls `super.finish()` and requests `slide_out_right` as the exit animation.

For the inspected thread-detail path, the completion sequence is:

```text
Swipe progress reaches 1
  -> BaseActivity.finish()
     -> super.finish()
     -> overridePendingTransition(0, slide_out_right)
  -> overridePendingTransition(0, 0)
```

The final override explicitly attempts to suppress the exit animation. This sequence does not prove that two animations actually play. Also, `BaseActivity.finishWithNoSlide()` still uses the animated `activity_close_exit` resource; it is not an animation-free alternative.

[SwipeUtils](../hipda/src/main/java/com/lichader/hipdanext/ui/widget/swipeback/SwipeUtils.java) invokes hidden `getActivityOptions()` and `convertToTranslucent()` methods through reflection. It passes a null conversion callback and silently catches every `Throwable`, so failures cannot currently be diagnosed from its own logs. Whether reflection fails on the reported device is unknown.

## Working Hypothesis

The leading suspect is the handoff between the manually moved page and Android's activity/window close transition. Transparent window composition and the successive animation overrides are candidates for a frame mismatch at completion.

A secondary candidate is specific to the unfolded display. `BaseActivity.onCreate()` applies the app's **屏幕方向** setting through `setRequestedOrientation()`. When that setting locks portrait or landscape, an app targeting API 30 has its requested orientation honored on large screens, so the app may be letterboxed on the unfolded display. The letterbox background and its transition are another place a flash could appear. This is untested; the default setting follows the system orientation and would not letterbox.

The source supports investigating these areas, but it does not establish the exact flashing frame or prove an Android 17 or foldable-specific defect. The repository still targets API 30; running on Android 17 alone does not establish that a target-37 behavior change caused the symptom.

Android's [Activity API documentation](https://developer.android.com/reference/android/app/Activity) describes supported translucency APIs and the replacement for deprecated `overridePendingTransition()`. These are relevant migration options, not evidence that changing either API will resolve this report.

## Investigation Checklist

- [ ] Record the installed version, exact affected page, theme, navigation mode, **屏幕方向** setting, flash appearance, and reproduction frequency.
- [ ] If **屏幕方向** is locked and the app is letterboxed on the unfolded display, repeat the swipe with the setting following the system orientation.
- [ ] Disable the app's **右划手势返回** setting, reopen the affected page, and return with the toolbar back arrow. This comparison was requested but no result has been reported.
- [ ] Compare folded and unfolded displays, slow and fast swipes, completed and canceled swipes, and toolbar/system Back navigation.
- [ ] Capture the final transition frames and log swipe completion, `finish()`, transition overrides, translucency failures, and main-screen lifecycle callbacks.
- [ ] Test a dedicated animation-free swipe-completion path that bypasses the normal exit-animation override. Compare it with the original behavior before drawing a causal conclusion.
- [ ] Evaluate supported translucency handling separately, and check the main-screen window/background transition if the flash also occurs with custom swiping disabled.

## Resolution Criteria

Confirm a root cause only after a controlled change removes a reproducible flash, with evidence identifying the responsible path. Verify normal Back navigation, swipe cancellation, repeated open/close cycles, both display states, and light/dark themes before marking the issue resolved.

Related work: [Android 17 migration plan](android-17-migration.md), especially navigation and layout behavior.
