# Research Background: Android Power Button Triple-Press Detection

**Date:** 2026-02-09
**Topic:** How to build an Android app that detects 3 rapid presses of the power button and launches the app in response.

## Sources

[1]: https://developer.android.com/reference/android/view/KeyEvent "KeyEvent | Android Developers"
[2]: https://copyprogramming.com/howto/how-to-hook-into-the-power-button-in-android "Android Power Button Hooking: A Guide"
[3]: https://forum.developer.samsung.com/t/how-to-hook-into-the-power-button-in-app/19224 "Samsung Developer Forums - Power Button Hook"
[4]: https://groups.google.com/g/android-platform/c/aQTxT_s-a7g "Android Platform Group - Power Button Event"
[5]: https://www.tutorialspoint.com/how-to-hook-a-function-into-the-power-button-in-android "TutorialsPoint - Power Button Hook"
[6]: https://thinkandroid.wordpress.com/2010/01/24/handling-screen-off-and-screen-on-intents/ "ThinkAndroid - Screen OFF/ON Intents"
[7]: https://groups.google.com/g/tasker/c/XCpwGVnQllw "Tasker Group - Double Press Power Button Pixel"
[8]: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService "AccessibilityService | Android Developers"
[9]: https://blog.joshsoftware.com/2018/03/28/android-accessibility-service-customization-for-keypress-event/ "Josh Software - AccessibilityService KeyPress"
[10]: https://github.com/parthdave93/AccessibilityServiceExample "GitHub - AccessibilityService Key Events Example"
[11]: https://copyprogramming.com/howto/is-it-possible-to-create-an-android-service-that-listens-for-hardware-key-presses "CopyProgramming - Hardware Key Press Service"
[12]: https://support.google.com/android/answer/9319337?hl=en "Google - Emergency SOS Help"
[13]: https://www.androidpolice.com/android-power-button-tricks/ "Android Police - Power Button Tricks"
[14]: https://www.india.com/technology/smartphone-power-button-hidden-features-tricks-camera-sos-flashlight-screenshot-settings-android-ios-samsung-apple-iphone-xiaomi-redmi-vivo-oppo-realme-oneplus-motorola-nokia-lava-micromax-iqoo-infini-8296846/ "India.com - Power Button Hidden Features"
[15]: https://community.oneplus.com/thread/747965 "OnePlus - Disable SOS Emergency Triple Press"
[16]: https://xdaforums.com/t/sos-emergency-triple-click-turn-off.4587869/ "XDA - SOS Emergency Triple Click"
[17]: https://xdaforums.com/t/app-power-button-flashlight-no-root.3839323/ "XDA - Power Button Flashlight No Root"
[18]: https://play.google.com/store/apps/details?id=com.teqtic.clicklight "ClickLight: Power button flash - Google Play"
[19]: https://github.com/anselm94/Torchie-Android "GitHub - Torchie Android"
[20]: https://gist.github.com/glutanimate/42ce2a327f32c744dc45 "GitHub Gist - Toggle flashlight via power button"
[21]: https://en.androidsis.com/clicklight/ "Androidsis - ClickLight"
[22]: https://gist.github.com/ishitcno1/7261765 "GitHub Gist - Screen On/Off Detection"
[23]: https://developer.android.com/about/versions/14/changes/fgs-types-required "Android 14 - Foreground Service Types Required"
[24]: https://developer.android.com/develop/background-work/services/fgs/service-types "Foreground Service Types | Android Developers"
[25]: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start "Restrictions on BG FGS Start | Android Developers"
[26]: https://developer.android.com/develop/background-work/services/fgs/changes "Changes to Foreground Services | Android Developers"
[27]: https://developer.android.com/about/versions/15/changes/foreground-service-types "Android 15 FGS Type Changes"
[28]: https://developer.android.com/about/versions/14/behavior-changes-14 "Android 14 Behavior Changes"
[29]: https://www.samsung.com/us/support/answer/ANS10002033/ "Samsung - Customize Side Button"
[30]: https://www.tomsguide.com/how-to/how-to-change-side-key-settings-samsung-galaxy "Tom's Guide - Samsung Side Key Settings"
[31]: https://support.google.com/googleplay/android-developer/answer/10964491?hl=en "Google Play - AccessibilityService API Policy"
[32]: https://support.google.com/googleplay/android-developer/answer/16585319?hl=en "Google Play - Permissions and Sensitive APIs"
[33]: https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/policy/PhoneWindowManager.java "AOSP - PhoneWindowManager.java"
[34]: https://repo.xposed.info/module/com.spazedog.xposed.additionsgb "Xposed Additions Module"
[35]: https://github.com/LSPosed/LSPosed "LSPosed Framework"
[36]: https://developer.android.com/reference/android/app/admin/DevicePolicyManager "DevicePolicyManager | Android Developers"
[37]: https://developer.android.com/work/device-admin "Device Admin Overview | Android Developers"
[38]: https://developer.android.com/guide/components/activities/background-starts "Background Activity Start Restrictions | Android Developers"
[39]: https://support.google.com/googleplay/android-developer/answer/13392821 "Play Console - Fullscreen Intent Requirements"
[40]: https://developer.android.com/training/monitoring-device-state/doze-standby "Optimize for Doze and App Standby"
[41]: https://dontkillmyapp.com/google "Don't Kill My App - Google"
[42]: https://support.google.com/pixelphone/answer/7443425?hl=en-GB "Pixel - Use Gestures"
[43]: https://support.google.com/pixelphone/thread/132935488 "Pixel Community - Double Tap Custom App"
[44]: https://www.androidcentral.com/phones/google-pixel/how-enable-or-disable-gestures-google-pixel-phone "Android Central - Pixel Gestures"

## Research Log

<!-- Each search is a ### Search: entry, separated by --- dividers, in chronological order -->

---

### Search: "Android KeyEvent KEYCODE_POWER app intercept power button press restrictions"

- **Power button is system-protected**: Android intentionally prevents apps from intercepting KEYCODE_POWER. ([Android Platform Group][4], [Samsung Developer Forums][3])
- **Standard key event methods don't work**: `onKeyDown()`, `dispatchKeyEvent()` cannot intercept power button presses. ([CopyProgramming Guide][2])
- **Framework modification required**: Must modify `PhoneWindowManager.java` — requires custom ROM or root. ([CopyProgramming Guide][2])
- **BroadcastReceiver workaround exists**: Listen for screen on/off events as a proxy. ([CopyProgramming Guide][2])

---

### Search: "Android detect power button triple press screen off broadcast receiver timing code example"

- **ACTION_SCREEN_OFF fires on manual power press** but NOT on screen timeout. ([ThinkAndroid][6])
- **Service-based persistence**: Register BroadcastReceiver inside a Service. ([CopyProgramming Guide][2])
- **Tasker users lost double-press customization on Android 12**: System-level multi-press handling conflicts. ([Tasker Group][7])

---

### Search: "Android AccessibilityService detect power button press key event global action"

- **onKeyEvent() does NOT detect power button**: Works for volume/keyboard keys only. ([Josh Software Blog][9], [CopyProgramming][11])
- **Must still use SCREEN_ON/SCREEN_OFF** even with AccessibilityService. ([CopyProgramming][11])

---

### Search: "Android 12 13 14 power button double press camera emergency SOS five presses"

- **Double-press for camera**: Most devices. Configurable. ([Android Police][13])
- **Five-press for Emergency SOS**: Android 12+. ([Google Support][12])
- **Triple-press for SOS on some OEMs**: OnePlus, others. ([OnePlus Community][15])
- **CRITICAL CONFLICT**: Built-in multi-press features intercept before app-level detection.

---

### Search: "Power Button Flashlight Button Mapper Torchie app how it works"

- **Power Button Flashlight**: Exploits camera gesture. Broke on Android 12+. ([XDA][17])
- **Torchie**: Uses volume buttons via AccessibilityService, not power button. ([GitHub - Torchie][19])

---

### Search: "ClickLight power button flashlight app screen on off count"

- **ClickLight uses screen on/off counting**: Foreground service, configurable clicks. Play-Store-approved. ([Androidsis][21], [Google Play][18])

---

### Search: "github android power button screen off BroadcastReceiver service"

- **Basic pattern**: Register BroadcastReceiver for ACTION_SCREEN_ON/OFF programmatically. ([GitHub Gist][22])

---

### Search: "Android foreground service requirements Android 14 15 Doze"

- **Android 14 requires FGS type**: `specialUse` type needed. ([Android 14 FGS Types][23])
- **Background FGS start restrictions (Android 12+)**: Limited exceptions. ([BG FGS Restrictions][25])
- **Doze mode**: Screen on/off broadcasts still delivered on screen changes. ([Android 14 Behavior][28])

---

### Search: "Samsung side key settings power button double press Galaxy S23 S24"

- **Samsung side key fully customizable**: Can assign any app to double-press. ([Samsung Support][29], [Tom's Guide][30])

---

### Search: "Google Play Store policy accessibility service power button intercept"

- **Accessibility API policy strict**: Only true accessibility tools qualify. ([Google Play Policy][31])
- **ClickLight confirms approach is allowed**: Screen on/off monitoring via FGS is Play-Store-approved.

---

### Search: "Android root Xposed framework power button hook PhoneWindowManager"

- **PhoneWindowManager handles power at framework level**: `interceptPowerKeyDown()`. ([AOSP][33])
- **Xposed/LSPosed can hook it**: Root + Magisk + LSPosed. True interception. ([LSPosed][35], [Xposed Additions][34])

---

### Search: "Android DeviceAdminReceiver DevicePolicyManager power button intercept"

- **Device Admin API does NOT help**: No key interception capabilities. ([DevicePolicyManager Docs][36])

---

### Search: "Android start activity from background restriction Android 10+ fullscreen intent"

- **Android 10+ blocks background activity starts**: System blocks startActivity() from background. ([Background Activity Starts][38])
- **Full-screen intent workaround**: High-importance notification with full-screen intent. `USE_FULL_SCREEN_INTENT` permission. ([Background Activity Starts][38])
- **Android 14 restricts full-screen intent**: Only calling/alarm apps typically approved on Google Play. ([Play Console][39])

---

### Search: "Android battery optimization whitelist REQUEST_IGNORE_BATTERY_OPTIMIZATIONS Doze"

- **Foreground service survives Doze**: Process not killed. ([Doze Optimization][40])
- **Power button wakes device from Doze**: Screen on/off broadcasts will be delivered. ([Doze Optimization][40])
- **OEM-specific battery killers**: Samsung, Xiaomi, Huawei aggressively kill background apps. See dontkillmyapp.com. ([Don't Kill My App][41])

---

### Search: "Google Pixel power button double press configure disable Android 14 15 gesture settings"

- **Pixel double-press limited to Camera or Wallet**: Since Android 12, Google removed ability to launch custom apps via double-press power button on Pixel. Settings > System > Gestures > Double-press power button. Can choose Camera, Wallet, or disable entirely. ([Pixel Gestures][42], [Pixel Community][43])
- **Cannot assign custom apps on Pixel**: Unlike Samsung where any app can be assigned, Pixel only allows Camera or Wallet. Users requesting custom app launch have been told it's not possible. ([Pixel Community][43])
- **Disabling double-press on Pixel**: Users can turn off the double-press gesture entirely, which would stop the system from intercepting double-presses and allow the screen on/off counting approach to work for those presses. ([Android Central - Pixel Gestures][44])
- **Quick Tap alternative on Pixel**: Uses back-tap (accelerometer) gesture instead of power button. Can launch any app. Not relevant to power button but worth noting as an alternative trigger. ([Android Central - Pixel Gestures][44])
