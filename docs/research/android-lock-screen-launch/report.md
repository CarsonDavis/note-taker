# Launching a Third-Party App from Android's Lock Screen: A System-Level Analysis

## Executive Summary

A third-party note-taking app **can** display over the Android lock screen using the same API the camera uses (`setShowWhenLocked(true)`). The app retains full access to network, microphone, storage, and all previously granted permissions while shown over the keyguard. Text input works. Biometric auth can be triggered from this state. Back/Home return the user to the lock screen automatically.

The hard problem is not **displaying** over the lock screen -- it is **triggering** the launch from a screen-off state. The camera's double-press power button gesture is hardcoded in AOSP and cannot be intercepted or redirected by third-party apps. No equivalent zero-touch hardware trigger exists for non-camera apps.

The most practical approaches for a note-taking app on Android 12-15, ranked by speed of access:

1. **Quick Settings Tile** -- Pull down shade from lock screen, tap tile, app appears over keyguard. Two taps, no unlock needed. Works today on all Android versions.
2. **Lock screen notification action** -- Tap the persistent notification, app opens. Requires touching a specific target on the notification. Works today.
3. **Lock screen widget** (future) -- One tap directly on lock screen. Coming to phones in Android 16 QPR1 (late 2025). Currently only on Pixel Tablets.
4. **AccessibilityService + volume button gesture** -- Triple-press volume-up to launch. Works with screen on, unreliable with screen off. Google Play policy risk.

None of these match the camera's instant, screen-off, no-touch-screen-needed experience. That level of integration is a system privilege.

---

## Part 1: How the Camera Double-Press Actually Works

### The Signal Chain

The camera launch from a double power-button press traverses four system components, all running at system privilege level. No part of this chain is accessible to third-party apps.

```
PhoneWindowManager (interceptPowerKeyDown)
    --> GestureLauncherService (handleCameraGesture)
        --> StatusBarManagerInternal (onCameraLaunchGestureDetected)
            --> SystemUI / KeyguardViewMediator (launch camera over keyguard)
```

**Step 1: PhoneWindowManager** intercepts all power button events at the lowest level of Android's input pipeline. It tracks consecutive presses and their timing. Third-party apps never see power button events -- they are consumed before reaching any app-level callback, including `AccessibilityService.onKeyEvent()`.

**Step 2: GestureLauncherService** receives the multi-press event and checks whether it qualifies as a camera gesture. The detection threshold is `CAMERA_POWER_DOUBLE_TAP_MAX_TIME_MS = 300` milliseconds between presses. If the gesture qualifies and `mCameraDoubleTapPowerEnabled` is true (set in AOSP's `config.xml`), the service acquires a 500ms wake lock to prevent the device from sleeping during the transition, then calls into StatusBarManagerInternal.

The service checks `isUserSetupComplete()` but does NOT check keyguard state -- the gesture works regardless of whether the device is locked.

**Step 3: StatusBarManagerInternal.onCameraLaunchGestureDetected()** is called with source `CAMERA_LAUNCH_SOURCE_POWER_DOUBLE_TAP`. This is an internal system API -- no third-party app can register a callback here. SystemUI's `StatusBar`/`CentralSurfaces` class implements this interface.

**Step 4: SystemUI / KeyguardViewMediator** handles the actual camera launch. It:
- Wakes the device if screen is off
- Performs the keyguard "bounce" animation (lock screen slides away)
- Resolves the camera app (default camera handling `INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE` or `android.intent.category.APP_CAMERA`)
- Launches the camera activity with `FLAG_SHOW_WHEN_LOCKED`
- When the user leaves the camera (Back/Home), the keyguard re-engages automatically

### What Makes Camera Special

The camera does NOT use a fundamentally different display mechanism. It uses `FLAG_SHOW_WHEN_LOCKED` / `setShowWhenLocked(true)` -- the exact same API available to any third-party app.

**The camera's privilege is in the trigger mechanism, not the display mechanism.** The entire signal chain from power button to `StatusBarManagerInternal` is system-only. A third-party app cannot:
- Intercept power button events
- Register with `GestureLauncherService`
- Receive `onCameraLaunchGestureDetected` callbacks
- Modify the gesture mapping in `PhoneWindowManager`

### Can You Hijack the Camera Gesture?

**Partially, with caveats.** If you set a third-party camera app as the device's default camera, the double-press gesture will launch that app instead of the built-in camera. The system resolves the gesture to whichever app handles `android.intent.category.APP_CAMERA`.

However:
- The app must actually be a camera app (handle camera intents)
- Android 11+ tightened default camera selection -- non-camera apps can't easily register as the default camera
- A note-taking app cannot register as a camera app in a way that would survive Google Play review

---

## Part 2: Lock Screen Display APIs Available to Third-Party Apps

### The Core APIs

Any third-party app can show an activity over the lock screen. No special permissions are required.

| API | Min API Level | Purpose |
|-----|--------------|---------|
| `Activity.setShowWhenLocked(true)` | 27 (Android 8.1) | Show activity on top of keyguard |
| `Activity.setTurnScreenOn(true)` | 27 (Android 8.1) | Turn screen on when activity starts |
| `WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED` | 1 (deprecated at 27) | Legacy equivalent of `setShowWhenLocked` |
| `WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON` | 1 (deprecated at 27) | Legacy equivalent of `setTurnScreenOn` |
| `WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD` | 1 (deprecated at 26) | Dismiss keyguard (requires auth if secure) |
| `KeyguardManager.requestDismissKeyguard()` | 26 (Android 8.0) | Request keyguard dismissal with callback |

### Behavior of `setShowWhenLocked(true)`

- The activity is shown **on top of the lock screen** in the RESUMED state
- The lock screen (keyguard) remains underneath -- it is not dismissed
- The device is still considered "locked" -- other apps cannot access the device
- When the user presses Back or Home, the activity is destroyed/stopped and **the lock screen re-appears automatically**
- This is the same behavior the camera exhibits when launched from the lock screen

### Difference Between Show-When-Locked and Dismiss-Keyguard

**`setShowWhenLocked(true)`**: Shows your activity on top of the lock screen WITHOUT requiring authentication. The keyguard stays active underneath. When the user leaves your activity, the lock screen comes back. Best for: quick-capture scenarios where you don't need full device access.

**`requestDismissKeyguard()`**: Requests that the lock screen be fully dismissed. If the device has a secure lock (PIN/pattern/biometric), the user must authenticate first. After dismissal, the device is fully unlocked. Best for: transitioning from a quick-capture lock screen activity to the full app.

### Security Model

**There is no additional sandboxing for activities shown over the keyguard.** The lock screen is a UI barrier, not a permission barrier. An activity displayed via `setShowWhenLocked(true)` retains ALL of its normal capabilities:

- **Network access**: Full connectivity (if `INTERNET` permission granted)
- **Microphone**: Can record audio (if `RECORD_AUDIO` permission granted)
- **Storage**: Full read/write (if storage permissions granted)
- **Camera hardware**: Can capture photos/video (if `CAMERA` permission granted)
- **Location**: Can access GPS (if location permissions granted)

The "secure camera" convention (where camera apps launched from the lock screen don't show existing photos) is a **voluntary behavior** -- the system does not enforce it. The camera app chooses to restrict its own UI when launched via `INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE`. A note-taking app could show any UI it wants over the lock screen.

### Back/Home/Navigation Behavior

From an activity shown over the lock screen:
- **Back button/gesture**: Activity goes through `onPause()` -> `onStop()` -> `onDestroy()`, then the lock screen reappears
- **Home button/gesture**: Activity goes to stopped state, lock screen reappears
- **Recents/overview**: Shows the activity in recents, but accessing it from recents would require unlock
- **No special handling needed**: The system automatically re-engages the keyguard when the user leaves the show-when-locked activity

---

## Part 3: Triggering the Launch -- The Hard Problem

Showing over the lock screen is easy. Getting there is hard. Here are all the viable trigger mechanisms.

### Approach 1: Quick Settings Tile (Recommended)

A `TileService` provides a custom tile in the Quick Settings panel. Tiles are visible on the lock screen when the user pulls down the notification shade.

**How it works:**
1. User wakes device (single power press or raise-to-wake)
2. User swipes down on lock screen to open notification shade
3. User taps the "Quick Note" tile
4. TileService calls `startActivity()` with `FLAG_ACTIVITY_NEW_TASK`
5. Activity (with `setShowWhenLocked(true)`) appears over the lock screen

**Speed**: ~2-3 seconds from screen-off to app visible. Two deliberate touch actions.

**Advantages:**
- Works on all Android versions from API 24+ (Android 7.0)
- No special permissions needed
- Explicitly supported by Android for lock screen use
- `startActivity()` from a TileService bypasses background activity start restrictions
- `isSecure()` check available to determine if device is locked
- `unlockAndRun()` available if you need authenticated actions

**Limitations:**
- Requires touching the screen (camera gesture doesn't)
- User must know to pull down shade and find the tile
- Tile may not be in the "quick" panel if user has many tiles

**Code for the TileService:**

```kotlin
class QuickNoteTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = "Quick Note"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, LockScreenNoteActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // startActivityAndCollapse works from unlocked state
        // startActivity works from locked state (shows over keyguard)
        if (isLocked) {
            startActivity(intent)
        } else {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        }
    }
}
```

### Approach 2: Persistent Notification with Action Button

A foreground service keeps a persistent notification with a "Quick Note" action button.

**How it works:**
1. User wakes device
2. Notification is visible on lock screen
3. User taps the action button
4. Activity launches over lock screen

**Speed**: ~2 seconds if the notification is visible without scrolling.

**Advantages:**
- Always visible on lock screen (as long as foreground service is running)
- One tap (vs two for QS tile)
- Foreground service can also handle other background work

**Limitations:**
- Requires running a foreground service (battery implications, Android 12+ restrictions on starting from background)
- On Android 12+, notification actions can force unlock before launching -- `setAuthenticationRequired(false)` needed for no-auth launch
- User may dismiss or hide the notification
- Requires `POST_NOTIFICATIONS` permission on Android 13+

### Approach 3: Lock Screen Widget (Future -- Android 16 QPR1+)

Lock screen widgets are returning to Android after being removed in Android 5.0.

**How it works:**
1. User wakes device
2. Widget is visible directly on the lock screen
3. User taps the widget
4. Activity launches (must declare `android:showWhenLocked="true"`)

**Speed**: ~1-2 seconds. One tap directly on the lock screen.

**Advantages:**
- Fastest touch-based approach -- widget is right on the lock screen
- No pull-down or navigation needed
- All widgets automatically compatible (no special code)
- Officially supported API

**Limitations:**
- Not yet available on phones -- coming in post-Android 16 QPR1 (late 2025)
- Currently only on Pixel Tablets
- Availability depends on OEM adoption
- Widget size is approximately 4x3 cells

### Approach 4: AccessibilityService + Volume Button Gesture

An AccessibilityService can detect volume button presses and trigger the app launch.

**How it works:**
1. User triple-presses volume-up
2. AccessibilityService detects the pattern via `onKeyEvent()`
3. Service starts the lock screen activity
4. Activity appears over lock screen

**Speed**: ~1 second from trigger to app visible. No screen touch needed (only volume button).

**Advantages:**
- Hardware trigger -- works without touching the screen
- AccessibilityService is an explicit exemption for background activity starts
- Can potentially work from lock screen (if screen is on)

**Limitations:**
- **Screen-off**: Volume button events may not be delivered to AccessibilityService when screen is off -- behavior varies by device and Android version
- **Google Play policy risk**: Google has tightened enforcement around AccessibilityService usage for non-accessibility purposes since Android 13. An app using it solely for button detection could be rejected from the Play Store
- **User setup required**: Users must manually enable the service in Settings > Accessibility
- **Cannot detect power button**: Power button events are intercepted by PhoneWindowManager before reaching AccessibilityService
- **Volume button conflicts**: Triple-pressing volume-up may interfere with volume adjustment or trigger other system behaviors

### Approach 5: Full-Screen Intent Notification (Limited on Android 14+)

The `fullScreenIntent` on a notification launches an activity over the lock screen when the screen is off. This is how alarm and phone apps work.

**How it works:**
1. Foreground service detects a trigger (e.g., scheduled time, or piggybacks on some event)
2. Service posts a notification with `setFullScreenIntent(pendingIntent, true)`
3. If screen is off, activity launches immediately over lock screen
4. If screen is on, notification appears as heads-up

**Speed**: Near-instant when triggered. The problem is what triggers it.

**Critical limitation on Android 14+ (API 34):** The `USE_FULL_SCREEN_INTENT` permission is restricted to **calling and alarm apps only**. Google Play automatically revokes this permission for other app categories. A note-taking app will NOT receive this permission automatically.

**Workarounds:**
- Users can manually grant the permission via `Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`
- Sideloaded apps (not from Play Store) can request the permission
- Apps installed before the Android 14 upgrade retain the permission

**For sideloaded/personal use**, this approach works well. For Play Store distribution targeting Android 14+, it is effectively unavailable for note-taking apps.

---

## Part 4: API Version Reference

| Feature | API Level | Android Version | Notes |
|---------|-----------|----------------|-------|
| `FLAG_SHOW_WHEN_LOCKED` | 1+ | All | Deprecated at API 27, still works |
| `FLAG_TURN_SCREEN_ON` | 1+ | All | Deprecated at API 27, still works |
| `FLAG_DISMISS_KEYGUARD` | 1+ | All | Deprecated at API 26 |
| `KeyguardManager.requestDismissKeyguard()` | 26 | 8.0 Oreo | Replacement for `FLAG_DISMISS_KEYGUARD` |
| `Activity.setShowWhenLocked()` | 27 | 8.1 Oreo MR1 | Replacement for `FLAG_SHOW_WHEN_LOCKED` |
| `Activity.setTurnScreenOn()` | 27 | 8.1 Oreo MR1 | Replacement for `FLAG_TURN_SCREEN_ON` |
| Background activity start restrictions | 29 | 10 | Blocks most background activity starts |
| `USE_FULL_SCREEN_INTENT` permission | 29 | 10 | Auto-granted to all apps initially |
| `setAuthenticationRequired()` on notification actions | 31 | 12 | Controls whether lock screen actions need auth |
| Foreground service launch restrictions | 31 | 12 | Limits starting FGS from background |
| `POST_NOTIFICATIONS` runtime permission | 33 | 13 | Required for all notifications |
| `USE_FULL_SCREEN_INTENT` restricted | 34 | 14 | Only calling/alarm apps auto-granted |
| PendingIntent BAL explicit opt-in | 35 | 15 | Must explicitly allow BAL on PendingIntents |

---

## Part 5: Complete Working Code

### Lock Screen Note Activity

```kotlin
package com.example.quicknote

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class LockScreenNoteActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // These MUST be called before super.onCreate() and setContentView()
        showOverLockScreen()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock_screen_note)

        // The activity has full capabilities here:
        // - Network access works
        // - Microphone recording works (if permission granted)
        // - Storage read/write works (if permission granted)
        // - Text input (EditText, keyboard) works normally

        val noteInput = findViewById<EditText>(R.id.note_input)
        val saveButton = findViewById<Button>(R.id.save_button)
        val unlockButton = findViewById<Button>(R.id.unlock_full_app)

        saveButton.setOnClickListener {
            val text = noteInput.text.toString()
            saveNote(text)  // Save via network, local DB, etc.
            finish()        // Returns to lock screen
        }

        unlockButton.setOnClickListener {
            // Trigger device unlock, then open full app
            requestDeviceUnlock {
                // This runs after successful unlock
                startMainActivity()
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun requestDeviceUnlock(onUnlocked: () -> Unit) {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE)
            as KeyguardManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguardManager.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        onUnlocked()
                    }
                    override fun onDismissCancelled() {
                        // User cancelled unlock - stay on lock screen activity
                    }
                    override fun onDismissError() {
                        // Error dismissing keyguard
                    }
                }
            )
        }
    }

    private fun saveNote(text: String) {
        // Implementation: save to Room DB, send to server, etc.
        // All normal app capabilities are available here
    }

    private fun startMainActivity() {
        // Launch the full app (device is now unlocked)
        // startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
```

### AndroidManifest.xml Configuration

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.quicknote">

    <!-- For foreground service (persistent notification approach) -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!-- For full-screen intent (Android 12-13 only, or sideloaded) -->
    <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />

    <!-- For SYSTEM_ALERT_WINDOW background activity start exemption -->
    <!-- <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" /> -->

    <application ...>

        <!-- Lock screen note activity -->
        <activity
            android:name=".LockScreenNoteActivity"
            android:showWhenLocked="true"
            android:turnScreenOn="true"
            android:taskAffinity=""
            android:excludeFromRecents="true"
            android:noHistory="true"
            android:launchMode="singleInstance" />

        <!-- Quick Settings Tile -->
        <service
            android:name=".QuickNoteTileService"
            android:icon="@drawable/ic_quick_note"
            android:label="Quick Note"
            android:permission="android.permission.BIND_QUICK_SETTINGS_TILE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.service.quicksettings.action.QS_TILE" />
            </intent-filter>
        </service>

        <!-- Foreground service (for persistent notification approach) -->
        <service
            android:name=".QuickNoteService"
            android:foregroundServiceType="specialUse"
            android:exported="false" />

    </application>
</manifest>
```

### Quick Settings Tile Service (Complete)

```kotlin
package com.example.quicknote

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class QuickNoteTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = "Quick Note"
            contentDescription = "Open quick note capture"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()

        val intent = Intent(this, LockScreenNoteActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    or Intent.FLAG_ACTIVITY_NO_USER_ACTION
            )
        }

        if (isLocked) {
            // Device is locked -- launch over the keyguard
            // startActivity() from TileService works on lock screen
            startActivity(intent)
        } else {
            // Device is unlocked -- collapse QS panel and launch
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(
                        this, 0, intent,
                        PendingIntent.FLAG_IMMUTABLE
                            or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }
}
```

### Foreground Service with Notification Action (Alternative Trigger)

```kotlin
package com.example.quicknote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class QuickNoteService : Service() {

    companion object {
        const val CHANNEL_ID = "quick_note_channel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Quick Note",
                NotificationManager.IMPORTANCE_LOW  // Low = no sound, always visible
            ).apply {
                description = "Persistent notification for quick note capture"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val launchIntent = Intent(this, LockScreenNoteActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quick_note)
            .setContentTitle("Quick Note")
            .setContentText("Tap to capture a note")
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)  // Show on lock screen
            .setContentIntent(pendingIntent)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_mic,
                    "Voice Note",
                    pendingIntent
                )
                .setAuthenticationRequired(false)  // Don't require unlock
                .build()
            )
            .build()
    }
}
```

---

## Part 6: The Complete Flow for a Note-Taking App

### Scenario: Quick Settings Tile Approach

```
State: Screen off, device locked

1. User presses power button once       --> Screen turns on, lock screen shows
2. User swipes down on lock screen       --> Notification shade / QS panel opens
3. User taps "Quick Note" tile           --> TileService.onClick() fires
4. TileService calls startActivity()     --> LockScreenNoteActivity starts
5. LockScreenNoteActivity.onCreate():
   - setShowWhenLocked(true)             --> Activity shows on top of keyguard
   - setTurnScreenOn(true)               --> Keeps screen on
   - setContentView (text input + mic)   --> UI is ready for interaction
6. User types or dictates note           --> Full keyboard, microphone, network all work
7. User taps "Save"                      --> Note saved (network/local DB)
8. Activity calls finish()               --> Lock screen reappears
```

**Time from screen-off to typing: approximately 3 seconds.**

### Scenario: Persistent Notification Approach

```
State: Screen off, device locked

1. User presses power button once        --> Screen turns on, lock screen shows
2. User sees notification on lock screen  --> "Quick Note: Tap to capture"
3. User taps notification action          --> PendingIntent fires
4. LockScreenNoteActivity starts          --> Shows over lock screen
5. User interacts with note UI            --> Full capabilities available
6. User taps "Save" or presses Back       --> Lock screen reappears
```

**Time from screen-off to typing: approximately 2 seconds.**

### What You Cannot Achieve

```
IMPOSSIBLE without root/system access:

1. Screen off                             --> CANNOT detect hardware button presses
2. Triple-press power button              --> Power events are system-only
3. App magically launches                 --> No trigger mechanism available
```

The camera's double-press-to-launch is **not replicable** by a third-party app because the trigger detection happens inside `PhoneWindowManager` at the kernel/system_server boundary, and the gesture is hardcoded to camera.

---

## Part 7: Approach Comparison

| Criterion | QS Tile | Notification | Lock Widget | Accessibility+Volume | FSI Notification |
|-----------|---------|-------------|-------------|---------------------|-----------------|
| Steps from screen-off | 3 (wake, swipe, tap) | 2 (wake, tap) | 2 (wake, tap) | 1-2 (button press) | 0 (auto-launch) |
| Works on Android 12 | Yes | Yes | No | Yes | Yes |
| Works on Android 14+ | Yes | Yes | No | Yes | No (permission revoked) |
| Requires special permissions | No | POST_NOTIFICATIONS | No | Accessibility toggle | USE_FULL_SCREEN_INTENT |
| Play Store compatible | Yes | Yes | Yes | Risky | No (for note apps) |
| Works with screen off | No | No | No | Unreliable | Yes |
| Battery impact | None | Low (FGS) | None | Low-Medium | Low (FGS) |
| User setup required | Add tile to QS | None | Add widget | Enable in Settings | Grant permission |

---

## Part 8: Recommendations for a Note-Taking App

### Primary Approach: Quick Settings Tile

The Quick Settings Tile is the recommended primary trigger. It is officially supported, works on all modern Android versions, requires no special permissions, and is Play Store safe. The two-swipe-and-tap flow is the fastest reliable method available to third-party apps.

### Secondary Approach: Persistent Notification

Add a foreground service with a persistent notification as a secondary option. Users who prefer one-tap access from the lock screen notification can use this. Users who find the persistent notification annoying can disable it while keeping the QS tile.

### Future: Lock Screen Widget

When lock screen widgets become available on phones (post-Android 16 QPR1), add an app widget with `android:showWhenLocked="true"`. This will be the fastest approach -- one tap directly on the lock screen with no navigation required.

### For Sideloaded/Personal Use Only

If distributing outside the Play Store (sideloading on your own device), the `USE_FULL_SCREEN_INTENT` + AccessibilityService approach can work well. The user manually grants the FSI permission, enables the accessibility service, and the app can launch over the lock screen in response to volume button gestures, even turning the screen on. This is the closest to the camera experience achievable without root, but it is not Play Store compatible.

### Two-Tiered Security Model

Regardless of trigger mechanism, implement a two-tiered interaction model:

1. **Quick capture (no auth)**: Text input, voice recording, saving notes -- all work over the keyguard without requiring unlock. Use `setShowWhenLocked(true)`.

2. **Full app access (auth required)**: Browsing existing notes, settings, account management -- trigger `requestDismissKeyguard()` which prompts for fingerprint/face/PIN. After successful auth, transition to the full app.

This mirrors exactly how the camera works: quick capture is available without unlock, but browsing the gallery requires authentication.
