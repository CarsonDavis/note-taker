# Android Power Button Triple-Press Detection: Comprehensive Research Report

**Date:** 2026-02-09

## Executive Summary

Building an Android app that detects a triple-press of the power button is achievable but constrained. Android deliberately blocks apps from directly intercepting power button events (`KEYCODE_POWER`). The only viable non-root approach is an **indirect workaround**: run a foreground service that listens for `ACTION_SCREEN_OFF` / `ACTION_SCREEN_ON` broadcasts and counts rapid screen toggles within a time window. This is the same approach used by existing Play Store apps like ClickLight. It works, but has inherent limitations: the screen actually toggles on each press, there are conflicts with built-in system gestures (double-press for camera, triple-press for emergency SOS on some OEMs), and modern Android imposes increasingly strict background execution and foreground service requirements. Samsung devices offer a cleaner alternative since users can assign any app to the side key double-press natively.

---

## Table of Contents

1. [Why Direct Interception Is Impossible](#1-why-direct-interception-is-impossible)
2. [The Screen On/Off Workaround (Primary Approach)](#2-the-screen-onoff-workaround-primary-approach)
3. [Implementation: Complete Code Example](#3-implementation-complete-code-example)
4. [AccessibilityService Approach](#4-accessibilityservice-approach)
5. [Device Admin / Device Owner Approach](#5-device-admin--device-owner-approach)
6. [Root-Based Approaches](#6-root-based-approaches)
7. [Built-In Multi-Press Features and Conflicts](#7-built-in-multi-press-features-and-conflicts)
8. [OEM-Specific Considerations](#8-oem-specific-considerations)
9. [Android Version Compatibility Matrix](#9-android-version-compatibility-matrix)
10. [Foreground Service and Background Restrictions](#10-foreground-service-and-background-restrictions)
11. [Launching the App from Background](#11-launching-the-app-from-background)
12. [Existing Apps and Precedent](#12-existing-apps-and-precedent)
13. [Google Play Store Policy](#13-google-play-store-policy)
14. [Recommended Architecture](#14-recommended-architecture)

---

## 1. Why Direct Interception Is Impossible

Android treats the power button as a **system-level control** that apps cannot intercept. This is by design: the power button is the user's last-resort mechanism to turn off or restart the device, and no app is allowed to compromise that.

**What does not work:**

- `Activity.onKeyDown(KeyEvent.KEYCODE_POWER, ...)` -- never receives power key events
- `Activity.dispatchKeyEvent()` -- power key events never reach the Activity
- `View.onKeyDown()` -- same limitation
- Manifest-declared BroadcastReceiver for `KEYCODE_POWER` -- no such broadcast exists

**Where power button handling lives:**

The power button is processed in `PhoneWindowManager.java`, deep in the Android framework (`frameworks/base/services/core/java/com/android/server/policy/`). The method `interceptPowerKeyDown()` handles the event before it reaches any application layer. Modifying this requires either a custom ROM build or a root-level framework hook.

Sources: [Android Platform Group Discussion](https://groups.google.com/g/android-platform/c/aQTxT_s-a7g), [AOSP PhoneWindowManager](https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/policy/PhoneWindowManager.java)

---

## 2. The Screen On/Off Workaround (Primary Approach)

Since apps cannot intercept the power button directly, the standard workaround is to **observe the side effects** of pressing it: the screen turns off and on.

**How it works:**

1. A foreground service runs persistently with a BroadcastReceiver listening for `Intent.ACTION_SCREEN_OFF` and `Intent.ACTION_SCREEN_ON`.
2. Each time `ACTION_SCREEN_OFF` is received, the service records a timestamp.
3. If 3 `SCREEN_OFF` events occur within a defined time window (e.g., 1.5-2 seconds), the service triggers the app launch.
4. A timeout handler resets the counter if the window expires without reaching the target count.

**Key characteristics of these broadcasts:**

- `ACTION_SCREEN_OFF` fires when the user presses the power button to turn the screen off. It does **not** fire when the screen times out from inactivity. This distinction is important -- it reduces false positives from normal screen timeouts.
- `ACTION_SCREEN_ON` fires when the screen is turned on by any means (power button, fingerprint, notification, etc.).
- These broadcasts are **protected system broadcasts** and must be registered programmatically at runtime, not in the manifest.
- These broadcasts are delivered even during Doze mode because pressing the power button inherently wakes the device.

**Why triple-press and not double-press:**

Existing apps like ClickLight report that triple-press detection is more reliable than double-press because it significantly reduces false positives. A user might accidentally press the power button twice (glancing at the phone, then putting it back in pocket), but three rapid presses are almost always intentional.

**Timing considerations:**

- Each power button press cycles the screen off, then on again. A "triple press" actually generates the sequence: OFF-ON-OFF-ON-OFF-ON (6 events total). Counting SCREEN_OFF events is simpler and more reliable since each press produces exactly one OFF event.
- A time window of **1.5 to 2 seconds** between the first and third SCREEN_OFF event is practical. Too short and users can't press fast enough; too long and false positives increase.
- The screen animation delay between ON and OFF varies by device. Budget approximately 300-500ms per full press cycle.

Sources: [ThinkAndroid](https://thinkandroid.wordpress.com/2010/01/24/handling-screen-off-and-screen-on-intents/), [ClickLight on Play Store](https://play.google.com/store/apps/details?id=com.teqtic.clicklight), [Androidsis ClickLight Review](https://en.androidsis.com/clicklight/)

---

## 3. Implementation: Complete Code Example

Below is a complete implementation in Kotlin targeting Android 14+ (API 34). This covers the foreground service, broadcast receiver, triple-press timing logic, and manifest configuration.

### AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.triplepressapp">

    <!-- Foreground service permission (required for Android 9+) -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <!-- specialUse FGS type permission (required for Android 14+) -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <!-- Post notifications (required for Android 13+) -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <!-- Receive boot completed to restart service after reboot -->
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:theme="@style/Theme.AppCompat">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Foreground service with specialUse type -->
        <service
            android:name=".PowerButtonService"
            android:foregroundServiceType="specialUse"
            android:exported="false">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="Monitors screen on/off events to detect rapid power button presses and launch the app as a user-configured shortcut." />
        </service>

        <!-- Restart service after device reboot -->
        <receiver
            android:name=".BootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>

    </application>
</manifest>
```

### PowerButtonService.kt (Foreground Service + BroadcastReceiver)

```kotlin
package com.example.triplepressapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

class PowerButtonService : Service() {

    companion object {
        private const val TAG = "PowerButtonService"
        private const val CHANNEL_ID = "power_button_channel"
        private const val NOTIFICATION_ID = 1

        // Triple-press detection parameters
        private const val REQUIRED_PRESSES = 3
        private const val TIME_WINDOW_MS = 1500L // 1.5 seconds for all 3 presses
        private const val RESET_DELAY_MS = 2000L // Reset counter after 2 seconds of no presses
    }

    private var pressCount = 0
    private var firstPressTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val resetRunnable = Runnable { resetPressCount() }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    onScreenOff()
                }
                Intent.ACTION_SCREEN_ON -> {
                    // Optional: can be used for additional logic
                    // but SCREEN_OFF is the primary trigger for counting
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        registerScreenReceiver()
        Log.d(TAG, "Service created and receiver registered")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY ensures the system restarts the service if it's killed
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenReceiver)
        handler.removeCallbacks(resetRunnable)
        Log.d(TAG, "Service destroyed")
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ requires specifying export flag
            registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
    }

    private fun onScreenOff() {
        val now = System.currentTimeMillis()

        if (pressCount == 0) {
            // First press in a new sequence
            firstPressTime = now
            pressCount = 1
        } else {
            // Check if still within the time window from the first press
            val elapsed = now - firstPressTime
            if (elapsed <= TIME_WINDOW_MS) {
                pressCount++
            } else {
                // Window expired, start a new sequence
                firstPressTime = now
                pressCount = 1
            }
        }

        Log.d(TAG, "Screen OFF detected. Press count: $pressCount")

        // Cancel any pending reset and schedule a new one
        handler.removeCallbacks(resetRunnable)
        handler.postDelayed(resetRunnable, RESET_DELAY_MS)

        if (pressCount >= REQUIRED_PRESSES) {
            Log.d(TAG, "Triple press detected! Launching app.")
            pressCount = 0
            handler.removeCallbacks(resetRunnable)
            launchApp()
        }
    }

    private fun resetPressCount() {
        Log.d(TAG, "Press count reset (timeout)")
        pressCount = 0
    }

    private fun launchApp() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            Log.e(TAG, "Could not create launch intent")
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Power Button Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Monitoring for power button triple-press"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Triple-Press Active")
            .setContentText("Press power button 3 times to open the app")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
```

### BootReceiver.kt (Restart After Reboot)

```kotlin
package com.example.triplepressapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, PowerButtonService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
```

### MainActivity.kt (Service Control)

```kotlin
package com.example.triplepressapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Simple UI with start/stop buttons
        setContentView(R.layout.activity_main)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100
                )
            }
        }

        findViewById<Button>(R.id.btnStart)?.setOnClickListener {
            startPowerButtonService()
        }

        findViewById<Button>(R.id.btnStop)?.setOnClickListener {
            stopPowerButtonService()
        }
    }

    private fun startPowerButtonService() {
        val intent = Intent(this, PowerButtonService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Triple-press detection started", Toast.LENGTH_SHORT).show()
    }

    private fun stopPowerButtonService() {
        val intent = Intent(this, PowerButtonService::class.java)
        stopService(intent)
        Toast.makeText(this, "Triple-press detection stopped", Toast.LENGTH_SHORT).show()
    }
}
```

### Key Implementation Notes

1. **Counting SCREEN_OFF events**: Each power button press generates exactly one SCREEN_OFF event. We count these, not SCREEN_ON, because SCREEN_ON can be triggered by other sources (fingerprint unlock, notifications, etc.).

2. **Time window**: 1.5 seconds for all 3 presses. This is generous enough for normal human pressing speed but tight enough to avoid false positives. Consider making this user-configurable.

3. **Reset mechanism**: A `Handler.postDelayed()` resets the counter after 2 seconds of no presses. This ensures stale counts from old, slow presses don't carry over.

4. **START_STICKY**: Tells the system to restart the service if it gets killed. Combined with the BootReceiver, this provides good persistence.

---

## 4. AccessibilityService Approach

**Verdict: Does not help for power button detection.**

AccessibilityService provides an `onKeyEvent()` callback that can intercept hardware key events before they reach the rest of the system. This works for:
- Volume up/down buttons
- Keyboard keys (including external keyboards)
- Meta/Windows keys

However, the power button is processed at the system level in `PhoneWindowManager` **before** AccessibilityService gets a chance to see it. The `onKeyEvent()` callback simply does not receive `KEYCODE_POWER` events.

Even if you configure the service with both required flags:
```xml
<accessibility-service
    android:canRequestFilterKeyEvents="true"
    android:accessibilityFlags="flagRequestFilterKeyEvents" />
```

The power button remains invisible to the service. You would still need to register a BroadcastReceiver for SCREEN_ON/SCREEN_OFF within the AccessibilityService, making the AccessibilityService wrapper unnecessary overhead.

**When AccessibilityService IS useful:** If you want to detect volume button triple-presses (or volume-up + volume-down combos) as an alternative trigger, AccessibilityService is the correct approach. The app Torchie uses this pattern.

Sources: [Josh Software Blog](https://blog.joshsoftware.com/2018/03/28/android-accessibility-service-customization-for-keypress-event/), [AccessibilityService Docs](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)

---

## 5. Device Admin / Device Owner Approach

**Verdict: Completely irrelevant.**

The Device Administration API (`DevicePolicyManager`, `DeviceAdminReceiver`) is designed for enterprise device management -- enforcing password policies, remote wiping, disabling cameras, encrypting storage, etc. It provides zero capability for hardware key interception or customization. This applies to both Device Admin and Device Owner modes.

Source: [DevicePolicyManager Docs](https://developer.android.com/reference/android/app/admin/DevicePolicyManager)

---

## 6. Root-Based Approaches

With root access, true power button interception becomes possible through framework-level hooks.

### Xposed / LSPosed Framework

**How it works:** The Xposed Framework (and its modern successor LSPosed) allows hooking into system methods at runtime without modifying APKs or system files directly. You can hook `PhoneWindowManager.interceptPowerKeyDown()` to intercept the actual KEYCODE_POWER event before the system processes it.

**Requirements:**
- Unlocked bootloader
- Root access (typically via Magisk)
- LSPosed framework installed
- Custom Xposed module

**Advantages over screen on/off approach:**
- Intercepts the actual key event, not a side effect
- Screen does not toggle on/off -- no visual disruption
- Can suppress the default system behavior (screen lock)
- Precise timing without screen animation delays
- No conflicts with system gestures (double-press camera, etc.)

**Disadvantages:**
- Requires root, which most users don't have
- Voids warranty, trips SafetyNet/Play Integrity
- Not distributable via Google Play
- Must be maintained across Android version updates

**Existing modules:** "Xposed Additions" is a well-known module that remaps hardware button actions including the power button.

Sources: [LSPosed Framework](https://github.com/LSPosed/LSPosed), [Xposed Additions Module](https://repo.xposed.info/module/com.spazedog.xposed.additionsgb), [AOSP PhoneWindowManager](https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/policy/PhoneWindowManager.java)

---

## 7. Built-In Multi-Press Features and Conflicts

Modern Android and OEM skins assign system-level actions to multi-press power button sequences. These are processed in `PhoneWindowManager` before any app-level detection and will conflict with a triple-press app.

| Gesture | Action | Where |
|---|---|---|
| Double-press | Launch Camera | Stock Android, Samsung, most OEMs |
| Double-press | Open Google Wallet | Pixel (Android 16+) |
| Triple-press | Emergency SOS | OnePlus, some Xiaomi/Realme |
| 5-press | Emergency SOS | Stock Android 12+, Samsung |
| Long-press | Google Assistant / Bixby | Pixel, Samsung |

### Conflict Analysis for Triple-Press

**On stock Android (Pixel):** The double-press camera gesture will intercept the first two presses of a triple-press. The system recognizes the double-press pattern and launches the camera before the third press occurs. **The user must disable the double-press gesture** in Settings > System > Gestures for triple-press to work via screen on/off counting.

**On OnePlus/some OEMs:** Triple-press is already assigned to Emergency SOS at the system level. **This completely blocks app-level triple-press detection.** The user must disable the emergency SOS triple-press in settings.

**On Samsung:** Samsung's OneUI handles the side key through its own settings system. Double-press can be assigned to any app (including your app), but triple-press is not natively assigned to anything. Samsung is actually the best platform for this use case since the double-press camera gesture does not interfere in the same way if the user configures it or disables it.

### User Setup Requirements

For the screen on/off approach to work reliably for triple-press, users must:
1. **Disable double-press for camera** (otherwise the camera launches on press 2)
2. **Disable emergency SOS** if their OEM uses triple-press for it
3. **Disable double-press for Wallet** (if on Pixel with Android 16+)

This setup requirement is a significant usability burden and should be documented prominently in the app.

Sources: [Android Police - Power Button Tricks](https://www.androidpolice.com/android-power-button-tricks/), [Google Emergency SOS](https://support.google.com/android/answer/9319337), [OnePlus Community](https://community.oneplus.com/thread/747965)

---

## 8. OEM-Specific Considerations

### Samsung Galaxy

**Best case scenario.** Samsung's OneUI provides rich side key customization:
- Settings > Advanced Features > Side Button
- Double-press: Can be assigned to **any installed app** (not just Camera)
- Press-and-hold: Bixby or Power Menu

**Recommendation for Samsung users:** Skip the screen on/off workaround entirely. Instruct users to assign your app to the side key double-press. This is native, reliable, and has no screen toggling side effect. The app just needs to include setup instructions.

### Google Pixel

**Constrained.** Double-press is limited to Camera or Wallet only (since Android 12). Cannot assign custom apps. Users must disable the double-press gesture entirely for the screen on/off approach to detect rapid presses. Quick Tap (back-tap) is available as an alternative trigger but uses the accelerometer, not the power button.

### OnePlus / Realme / Oppo (ColorOS / OxygenOS)

**Triple-press conflict.** These OEMs often assign triple-press to Emergency SOS. Must be disabled by the user. They also have aggressive battery optimization that may kill foreground services. Users may need to manually exempt the app from battery optimization.

### Xiaomi / Redmi (MIUI / HyperOS)

**Aggressive background killing.** MIUI is notorious for killing background apps and services. Users must: lock the app in recent apps, disable battery optimization for the app, and potentially enable "autostart" permission. See [dontkillmyapp.com](https://dontkillmyapp.com/) for device-specific instructions.

### Huawei (EMUI / HarmonyOS)

**Most restrictive.** Similar aggressive background killing as Xiaomi. May require multiple manual settings changes to keep the foreground service alive.

Sources: [Samsung Support](https://www.samsung.com/us/support/answer/ANS10002033/), [Pixel Gestures](https://support.google.com/pixelphone/answer/7443425), [Don't Kill My App](https://dontkillmyapp.com/google)

---

## 9. Android Version Compatibility Matrix

| Android Version | API | Key Changes Affecting This Feature |
|---|---|---|
| 8.0 Oreo | 26 | Background execution limits. Must use `startForegroundService()` and call `startForeground()` within 5 seconds. |
| 9.0 Pie | 28 | `FOREGROUND_SERVICE` permission required in manifest. |
| 10 | 29 | **Background activity start restrictions.** Cannot call `startActivity()` from background service. Must use notification or full-screen intent. |
| 11 | 30 | Foreground service launch restrictions from background tightened. |
| 12 | 31 | **System multi-press gestures introduced.** Double-press camera gesture now system-managed. Emergency SOS (5-press) added. Background FGS start restrictions. |
| 12L | 32 | Minor refinements. |
| 13 | 33 | `POST_NOTIFICATIONS` runtime permission required. `RECEIVER_EXPORTED` / `RECEIVER_NOT_EXPORTED` flag introduced for dynamic receivers. |
| 14 | 34 | **FGS type required** in manifest. Must use `specialUse` for screen monitoring. `USE_FULL_SCREEN_INTENT` becomes restricted -- only approved apps can use it. |
| 15 | 35 | FGS dataSync type gets 6-hour timeout. Further tightening of background restrictions. |

**Minimum recommended target:** API 26 (Android 8.0) for broadest compatibility, with proper handling of all API-level-specific requirements.

Sources: [Android 14 FGS Types](https://developer.android.com/about/versions/14/changes/fgs-types-required), [Background Activity Starts](https://developer.android.com/guide/components/activities/background-starts), [Android 14 Behavior Changes](https://developer.android.com/about/versions/14/behavior-changes-14)

---

## 10. Foreground Service and Background Restrictions

### Foreground Service Configuration (Android 14+)

The service must declare the `specialUse` foreground service type because no standard type (camera, location, mediaPlayback, etc.) fits "monitoring screen on/off events."

```xml
<service
    android:name=".PowerButtonService"
    android:foregroundServiceType="specialUse"
    android:exported="false">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Monitors screen state changes to detect user-initiated
            rapid power button presses as an app launch shortcut." />
</service>
```

Required permissions:
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
```

### Surviving Doze Mode

Power button presses are inherently incompatible with Doze mode because pressing the power button wakes the device, exiting Doze. The broadcast receiver will receive SCREEN_OFF/SCREEN_ON events when the user presses the button. A foreground service process is not killed during Doze.

### Surviving OEM Battery Optimization

Many OEMs (Samsung, Xiaomi, Huawei, OnePlus) aggressively kill background processes beyond what stock Android does. Mitigations:

1. **Request battery optimization exemption:**
   ```kotlin
   val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
   intent.data = Uri.parse("package:$packageName")
   startActivity(intent)
   ```
   Note: Google Play restricts when you can request this.

2. **Guide users to OEM-specific settings** (autostart, app lock in recents, etc.)

3. **Use `START_STICKY`** in `onStartCommand()` so the system restarts the service if killed.

4. **Use `BOOT_COMPLETED` receiver** to restart the service after reboot.

Sources: [Foreground Service Types](https://developer.android.com/develop/background-work/services/fgs/service-types), [Doze Optimization](https://developer.android.com/training/monitoring-device-state/doze-standby), [Don't Kill My App](https://dontkillmyapp.com/google)

---

## 11. Launching the App from Background

Once the triple-press is detected, the service needs to bring the app to the foreground. This has become increasingly restricted on modern Android.

### Android 9 and below
Simple `startActivity()` with `FLAG_ACTIVITY_NEW_TASK` from the service works.

### Android 10-13
Background activity starts are blocked. Options:
- **If the foreground service was started while the app was visible** (user opened the app, which started the service): the app retains background activity start privileges temporarily.
- **Full-screen intent notification**: Create a high-priority notification with a full-screen intent. If the screen is off, the full-screen intent fires immediately, bringing up the activity.
- **User interaction exception**: If the user just pressed the power button, the device is technically waking up, and the system may grant a brief window for activity starts.

### Android 14+
`USE_FULL_SCREEN_INTENT` is restricted to calling and alarm apps on Google Play. For non-calling/alarm apps, the practical approach is:
- Use `startActivity()` with `FLAG_ACTIVITY_NEW_TASK` from the foreground service. This often works because the power button press has just woken the device and the user has been interacting (pressing buttons), which may satisfy the system's "recent user interaction" heuristic.
- As a fallback, post a high-priority notification that the user taps to open the app.

**In practice:** Since the user has just pressed the power button 3 times in rapid succession, the device screen is ON (the final press turned it on), and there was recent user interaction. The `startActivity()` call from the foreground service tends to succeed on most devices, though this is not guaranteed by the API contract.

Sources: [Background Activity Start Restrictions](https://developer.android.com/guide/components/activities/background-starts), [Play Console Full-Screen Intent](https://support.google.com/googleplay/android-developer/answer/13392821)

---

## 12. Existing Apps and Precedent

### ClickLight (Play Store)

The most relevant precedent. ClickLight lets users toggle the flashlight by triple-pressing the power button. It:
- Uses the screen on/off BroadcastReceiver approach
- Runs as a foreground service with a persistent notification
- Supports configurable double-click or triple-click (recommends triple for reliability)
- Is listed on Google Play, confirming the approach passes Play Store review

### Power Button Flashlight (XDA)

Used a different approach: registered as a camera intent handler so the system's double-press-for-camera gesture would launch the flashlight app instead. This broke on Android 12+ when Google changed how the camera gesture works. Not recommended.

### Torchie

Uses AccessibilityService to detect volume button combinations (both buttons simultaneously), not the power button. Relevant as an alternative trigger mechanism but not applicable to power button detection.

### Button Mapper

Commercial app that remaps hardware buttons. Can remap volume, Bixby, and squeeze gestures. Does not support power button remapping without root due to the same system-level restrictions discussed above.

Sources: [ClickLight on Play Store](https://play.google.com/store/apps/details?id=com.teqtic.clicklight), [XDA Power Button Flashlight](https://xdaforums.com/t/app-power-button-flashlight-no-root.3839323/)

---

## 13. Google Play Store Policy

### Is this approach allowed?

**Yes**, based on precedent and policy analysis:

1. **ClickLight is live on Play Store** using the same screen on/off approach with a foreground service, confirming Google permits it.

2. **No specific prohibition** on monitoring screen on/off broadcasts or using them to infer power button presses.

3. **`specialUse` FGS type** is designed for exactly these edge cases. The explanation in the `<property>` tag is reviewed by Google Play, so provide a clear, honest description of the use case.

### Potential concerns

- **AccessibilityService misuse**: If you use AccessibilityService solely for screen monitoring (when a BroadcastReceiver suffices), Google may reject the app. Only use AccessibilityService if you have a genuine accessibility use case.

- **Battery optimization exemption**: Requesting `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` requires justification. Google's policy is that only apps whose core function is adversely affected should request it.

- **`USE_FULL_SCREEN_INTENT`**: On Android 14+, Google Play restricts this permission to calling and alarm apps. Don't rely on it for launching your activity.

- **Deceptive behavior**: The app should be transparent about what it does. The foreground notification serves this purpose.

Sources: [Google Play AccessibilityService Policy](https://support.google.com/googleplay/android-developer/answer/10964491), [Play Console Full-Screen Intent](https://support.google.com/googleplay/android-developer/answer/13392821)

---

## 14. Recommended Architecture

### For a non-root, Play-Store-distributable app:

```
                    +---------------------------+
                    |      MainActivity         |
                    |  - Start/Stop service      |
                    |  - Settings (timing, etc.) |
                    |  - Setup instructions       |
                    +---------------------------+
                                |
                    starts/stops
                                |
                    +---------------------------+
                    |   PowerButtonService       |
                    |   (Foreground Service)     |
                    |   - specialUse FGS type    |
                    |   - Persistent notification|
                    |   - START_STICKY           |
                    +---------------------------+
                                |
                    registers dynamically
                                |
                    +---------------------------+
                    |   ScreenStateReceiver      |
                    |   (BroadcastReceiver)      |
                    |   - ACTION_SCREEN_OFF      |
                    |   - ACTION_SCREEN_ON       |
                    +---------------------------+
                                |
                    counts + timestamps
                                |
                    +---------------------------+
                    |   Triple-Press Detector    |
                    |   - Count SCREEN_OFF events|
                    |   - 1.5s time window       |
                    |   - Handler-based reset    |
                    +---------------------------+
                                |
                    on detection
                                |
                    +---------------------------+
                    |   startActivity()         |
                    |   - FLAG_ACTIVITY_NEW_TASK |
                    |   - Fallback: notification |
                    +---------------------------+

    +---------------------------+
    |      BootReceiver         |
    |  - Restart FGS after boot |
    +---------------------------+
```

### User setup checklist (displayed in app):

1. Disable double-press power button gesture (Settings > System > Gestures)
2. Disable emergency SOS triple-press if applicable (Settings > Safety & Emergency)
3. Exempt app from battery optimization (guide per OEM)
4. Grant notification permission (Android 13+)
5. Start the service

### On Samsung specifically:

Consider detecting Samsung devices and offering the cleaner alternative: instruct users to assign the app to the side key double-press in Settings > Advanced Features > Side Button. This avoids the foreground service entirely for that use case.

---

## Summary of Approaches

| Approach | Root Required | Power Button Direct | Reliable | Play Store | Notes |
|---|---|---|---|---|---|
| **Screen on/off counting (FGS)** | No | No (indirect) | Good | Yes | Primary recommended approach. Screen toggles as side effect. |
| **Samsung side key assignment** | No | Yes (native) | Excellent | N/A | Best option on Samsung. No app-level detection needed. |
| **Camera intent hijacking** | No | No (indirect) | Broken | N/A | Broke on Android 12+. Not recommended. |
| **AccessibilityService onKeyEvent** | No | No | N/A | Risky | Does not detect power button. Irrelevant. |
| **Device Admin API** | No | No | N/A | N/A | No key interception capabilities. Irrelevant. |
| **Xposed/LSPosed hook** | Yes | Yes | Excellent | No | True interception. Root + custom module required. |
| **Custom ROM / framework mod** | Yes | Yes | Excellent | No | Maximum control. Impractical for distribution. |
