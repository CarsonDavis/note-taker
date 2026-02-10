# Background Research: Android Lock Screen App Launch

**Date:** 2026-02-09
**Description:** Deep research into how Android's double-press-power-to-launch-camera works at the system level, and whether a third-party app can replicate this behavior — launching instantly from a screen-off/locked state, bypassing the lock screen.

## Sources

[1]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/services/core/java/com/android/server/GestureLauncherService.java "GestureLauncherService.java - AOSP"
[2]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/packages/SystemUI/docs/device-entry/keyguard.md "Keyguard (Lock Screen) Documentation - AOSP"
[3]: https://github.com/aosp-mirror/platform_frameworks_base/blob/master/services/core/java/com/android/server/policy/PhoneWindowManager.java "PhoneWindowManager.java - AOSP Mirror"
[4]: https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/res/res/values/config.xml "AOSP config.xml - Framework Configuration"
[5]: https://github.com/aosp-mirror/platform_frameworks_base/blob/master/services/core/java/com/android/server/statusbar/StatusBarManagerInternal.java "StatusBarManagerInternal.java - AOSP"
[6]: https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/app/KeyguardManager.java "KeyguardManager.java - AOSP"
[7]: https://gist.github.com/colinyip/253ab5d3bfd468668e09635b413dec7c "Wake up and show screen when phone is locked - GitHub Gist"
[8]: https://www.giorgosneokleous.com/post/full-screen-intent-notifications-android/ "Full-Screen Intent Notifications - Giorgos Neokleous"
[9]: https://victorbrandalise.com/how-to-show-activity-on-lock-screen-instead-of-notification/ "Show Activity on Lock Screen - Victor Brandalise"
[10]: https://source.android.com/docs/core/display/multi_display/lock-screen "Lock screen - Android Open Source Project"
[11]: https://developer.android.com/guide/components/activities/background-starts "Restrictions on starting activities from the background - Android Developers"
[12]: https://developer.android.com/about/versions/14/behavior-changes-14 "Behavior changes: Apps targeting Android 14 - Android Developers"
[13]: https://source.android.com/docs/core/permissions/fsi-limits "Full-screen intent limits - AOSP"
[14]: https://www.droidcon.com/2025/09/02/%F0%9F%9A%A8-full-screen-intent-fsi-notifications-in-android-14-15-what-changed-why-its-breaking-and-how-to-fix-it/ "Full-Screen Intent Notifications in Android 14 & 15 - droidcon"
[15]: https://developer.android.com/reference/android/app/KeyguardManager "KeyguardManager API Reference - Android Developers"
[16]: https://github.com/aosp-mirror/platform_frameworks_base/blob/master/packages/SystemUI/src/com/android/systemui/keyguard/KeyguardViewMediator.java "KeyguardViewMediator.java - AOSP"
[17]: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService "AccessibilityService API Reference - Android Developers"
[18]: https://developer.android.com/guide/topics/ui/accessibility/service "Create your own accessibility service - Android Developers"
[19]: https://copyprogramming.com/howto/detect-hardware-button-press-while-android-device-is-locked "Detection of hardware button press on locked Android device"
[20]: https://learn.microsoft.com/en-us/dotnet/api/android.app.activity.setshowwhenlocked "Activity.SetShowWhenLocked - Microsoft Learn"
[21]: https://support.google.com/pixelphone/thread/132935488 "How do I get the power button double tap feature to open an app that's not the camera app - Google Pixel Community"
[22]: https://groups.google.com/g/tasker/c/XCpwGVnQllw "Tasker - lost double press power button customization on Pixel 3"
[23]: https://9to5google.com/2020/08/18/android-11-default-camera-app-changes/ "Android 11 force apps to use built-in camera app"
[24]: https://developer.android.com/reference/android/service/dreams/DreamService "DreamService API Reference - Android Developers"
[25]: https://developer.android.com/develop/ui/views/quicksettings-tiles "Create custom Quick Settings tiles - Android Developers"
[26]: https://android-developers.googleblog.com/2025/03/widgets-on-lock-screen-faq.html "Widgets on lock screen FAQ - Android Developers Blog"
[27]: https://developer.android.com/identity/sign-in/biometric-auth "Show a biometric authentication dialog - Android Developers"
[28]: https://developer.android.com/develop/ui/views/notifications/build-notification "Create a notification - Android Developers"
[29]: https://developer.android.com/about/versions/15/behavior-changes-15 "Behavior changes: Apps targeting Android 15 - Android Developers"

## Research Log

---

### Search: "Android GestureLauncherService double press power button camera launch AOSP source code"

- **GestureLauncherService** detects double-press power gesture ([GestureLauncherService.java][1])
- Detection threshold: `CAMERA_POWER_DOUBLE_TAP_MAX_TIME_MS = 300` ms ([GestureLauncherService.java][1])
- Calls `StatusBarManagerInternal.onCameraLaunchGestureDetected(CAMERA_LAUNCH_SOURCE_POWER_DOUBLE_TAP)` ([GestureLauncherService.java][1])
- Acquires 500ms wake lock ([GestureLauncherService.java][1])
- Checks `isUserSetupComplete()` but NOT keyguard state ([GestureLauncherService.java][1])
- Actual launch delegated to StatusBarService/SystemUI ([GestureLauncherService.java][1])
- Configurable via `mCameraDoubleTapPowerEnabled` in AOSP config.xml ([AOSP config.xml][4])

---

### Search: "Android PhoneWindowManager interceptPowerKeyDown double press camera"

- PhoneWindowManager intercepts all power key events at system level ([PhoneWindowManager.java][3])
- `interceptPowerKeyDown` tracks consecutive taps, triggers GestureLauncherService ([PhoneWindowManager.java][3])
- Power button events never reach apps — intercepted before accessibility services too ([Keyguard docs][2])

---

### Search: "Android AOSP StatusBarManagerInternal onCameraLaunchGestureDetected"

- SystemUI's StatusBar/CentralSurfaces implements the callback ([StatusBarManagerInternal.java][5])
- Camera launched with keyguard "bounce" animation — SystemUI manages the transition
- Same path used by lock screen camera swipe affordance

---

### Search: "Android setShowWhenLocked FLAG_SHOW_WHEN_LOCKED third party app"

- `setShowWhenLocked(true)` (API 27+): Activity shown on top of lock screen in RESUMED state ([Activity.SetShowWhenLocked][20])
- `setTurnScreenOn(true)` (API 27+): Turns screen on for the activity ([Activity.SetShowWhenLocked][20])
- `KeyguardManager.requestDismissKeyguard()` (API 26+): Dismisses keyguard with auth ([KeyguardManager.java][6])
- Available to ANY third-party app, no special permissions ([Activity.SetShowWhenLocked][20])

---

### Search: "Android fullScreenIntent USE_FULL_SCREEN_INTENT Android 14"

- Android 14: `USE_FULL_SCREEN_INTENT` restricted to calling/alarm apps only ([Behavior changes Android 14][12], [Full-screen intent limits][13])
- Google Play revokes for other categories ([Full-screen intent limits][13])
- Users CAN manually grant via settings ([Behavior changes Android 14][12])
- **Note-taking apps will NOT get auto-grant** ([Full-screen intent limits][13])

---

### Search: "Android 10 background activity start restrictions exemptions"

- 13 exemptions listed ([Background starts][11])
- Key: AccessibilityService (#6), SYSTEM_ALERT_WINDOW (#13)
- Android 14+: explicit opt-in required ([Background starts][11])

---

### Search: "Android camera app special case keyguard SECURE_CAMERA"

- `MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE` for lock screen camera
- Camera uses `FLAG_SHOW_WHEN_LOCKED` — same API any app can use
- **Key insight: Camera's privilege is in the TRIGGER, not the display mechanism**

---

### Search: "Android AccessibilityService detect power button volume button"

- `onKeyEvent()` can detect volume buttons ([AccessibilityService API][17])
- Cannot detect power button — intercepted by system first ([Detection of hardware button press][19])
- Screen-off: unreliable key event delivery ([Detection of hardware button press][19])
- Android 13+: tightened Play Store restrictions ([Create accessibility service][18])

---

### Search: "Android app shown over lock screen security limitations"

- No additional sandboxing — app retains ALL normal permissions ([Activity.SetShowWhenLocked][20])
- Lock screen is UI barrier, not permission barrier
- Network, microphone, storage all accessible if previously granted

---

### Search: "Android alarm clock fullScreenIntent implementation"

- FSI pattern: `setFullScreenIntent(pendingIntent, true)` + `setShowWhenLocked(true)` + `setTurnScreenOn(true)` ([Full-Screen Intent Notifications][8])
- Category `CATEGORY_ALARM` or `CATEGORY_CALL` for best behavior

---

### Search: "Android third party app as default camera double press power button"

- Setting third-party camera as default redirects double-press gesture ([Google Pixel Community][21])
- Non-camera apps cannot receive this gesture — resolves to `APP_CAMERA` category only
- Tasker workaround possible ([Tasker thread][22])
- Android 11+: harder to set non-camera app as default camera ([9to5Google][23])

---

### Search: "Android DreamService screen saver API"

- DreamService is screen saver API — charging/idle only ([DreamService API][24])
- Not viable for quick-access launch

---

### Search: "Android Quick Settings Tile TileService launch from lock screen"

- **Tiles CAN appear on lock screen** ([Quick Settings tiles docs][25])
- **`startActivity()` from tile on locked device** launches activity on top of lock screen ([Quick Settings tiles docs][25])
- **`unlockAndRun()`** for auth-required actions ([Quick Settings tiles docs][25])
- 2-step process: pull down shade + tap tile

---

### Search: "Android activity setShowWhenLocked back button home behavior biometric"

- Back button from show-when-locked activity: `onDestroy()` then lock screen re-appears
- Home button: stopped state, returns to lock screen
- Lock screen re-engages automatically when user leaves

---

### Search: "Android BiometricPrompt from activity shown over lock screen"

- BiometricPrompt CAN be shown from over-keyguard activity ([Biometric auth docs][27])
- `requestDismissKeyguard()` triggers system unlock UI
- Two-tiered approach possible: quick capture (no auth) + full app (auth required)

---

### Search: "Android lock screen widget WIDGET_CATEGORY_KEYGUARD"

- Coming back in post-Android 16 QPR1 ([Widgets on lock screen FAQ][26])
- Currently on Pixel Tablets only
- Widgets launching activities must declare `android:showWhenLocked="true"` ([Widgets on lock screen FAQ][26])

---

### Search: "Android persistent notification action button launch activity from lock screen"

**Notification action buttons on lock screen:**

- **Notification action buttons CAN appear on lock screen** — users can tap them ([Create a notification][28])
- **BUT on Android 12+ (API 31)**: Notification actions can be configured to require device unlock before the app invokes the action — using `setAuthenticationRequired(true)` ([Create a notification][28])
- **Default behavior**: Tapping a notification action from lock screen will show the unlock prompt first, THEN launch the activity
- **For an ongoing foreground service notification**: The notification persists, and action buttons provide a tap target from the lock screen
- **Key limitation**: The user must tap the notification, then potentially unlock — still 2+ steps from lock screen to app
- **Compared to camera**: Camera gesture is 0-step (just double-press power, no touch screen needed) — notification approach is slower but works reliably

---

### Search: "Android 15 background activity launch changes restrictions"

**Android 15 (API 35) BAL changes:**

- **Apps targeting Android 15+ no longer implicitly grant BAL privileges to PendingIntents** they create ([Behavior changes Android 15][29])
- Must explicitly opt in via `setPendingIntentBackgroundActivityStartMode(MODE_BACKGROUND_ACTIVITY_START_ALLOWED)` ([Behavior changes Android 15][29])
- **Android 16 adds more granularity**: `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE` and `MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS` ([Background starts][11])
- **Trend is clear**: Google is progressively tightening background activity starts, making it harder for apps to interrupt the user
- **SYSTEM_ALERT_WINDOW exemption still holds** as of Android 15 ([Background starts][11])
- **AccessibilityService exemption still holds** as of Android 15 ([Background starts][11])
