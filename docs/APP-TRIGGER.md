# App Trigger: Launch from Lock Screen via Digital Assistant Registration

## Decision Summary

The note-taking app will register as an Android **digital assistant** using the
`VoiceInteractionService` API. This allows the user to long-press the side key
(from screen off or lock screen) to instantly open the note capture UI over the
lock screen, without unlocking the device.

**Target device**: Samsung Galaxy S24 Ultra (SM-S928U1), Android 16, OneUI 8.0

### Why This Approach

| Approach considered | Verdict | Why |
|---|---|---|
| Side key double-press → open app | Rejected | Would replace camera launch |
| Side key long-press → digital assistant | **Chosen** | Clean, native, no custom detection code |
| Triple-press power (foreground service) | Rejected | Screen toggles, conflicts, battery drain, FGS complexity |
| Volume button combo (AccessibilityService) | Rejected | Unreliable screen-off, Play Store policy risk |
| Lock screen widget | Not yet available | Android 16 QPR1+, OEM adoption unclear |
| Quick Settings tile | Backup option | 3 steps from screen-off (wake, swipe, tap) |

The digital assistant approach uses a built-in Android mechanism, requires no
foreground service, no battery drain, no accessibility hacks, and works from
screen off identically to the camera double-press gesture.

### What the User Gives Up

Setting this app as the default assistant means the side key long-press no
longer opens Google Assistant / Gemini / Bixby. However:

- **"Hey Google" voice activation still works** (separate from ROLE_ASSISTANT)
- **Google Assistant app icon still works** from the app drawer
- **Can be swapped back** any time in settings

---

## How It Works

### The Trigger Chain

```
Screen off / Lock screen
    → User long-presses side key
    → Android invokes the default digital assistant
    → System calls VoiceInteractionService.onLaunchVoiceAssistFromKeyguard()
    → Service starts NoteCaptureActivity with FLAG_ACTIVITY_NEW_TASK
    → Activity has showWhenLocked=true, turnScreenOn=true
    → Screen turns on, app appears over lock screen
    → User types/dictates note, taps save
    → Activity calls finish(), lock screen reappears
```

### Why This Works Like the Camera

The camera's double-press isn't special because of how it *displays* — it uses
`setShowWhenLocked(true)`, the same API available to any app. The camera's
privilege is in the *trigger* (hardcoded in `PhoneWindowManager`). By
registering as the digital assistant, we get an equivalent system-level trigger
via the long-press gesture.

### Samsung-Specific Behavior

Samsung's OneUI uses the standard Android `ROLE_ASSISTANT` mechanism. No
Samsung-specific API or manifest entry is needed. When the app registers as a
`VoiceInteractionService`, it automatically appears in:

- **Settings > Apps > Default Apps > Digital assistant app**
- **Settings > Advanced Features > Side Button > Press and hold > Digital assistant**

The user selects the note-taking app in either location (they're the same
picker).

---

## Implementation Requirements

### Components Needed

```
app/src/main/
  java/com/example/notetaker/
    assist/
      NoteAssistService.kt           ← VoiceInteractionService (near-empty stub)
      NoteAssistSessionService.kt    ← Creates sessions (boilerplate)
      NoteAssistSession.kt           ← Launches the note capture Activity
    NoteCaptureActivity.kt           ← The actual note-taking UI
  res/
    xml/
      assist_service.xml             ← Voice interaction config
  AndroidManifest.xml
```

### 1. AndroidManifest.xml — Service Declarations

Two services must be declared with `BIND_VOICE_INTERACTION` permission. The
note capture activity must declare `showWhenLocked` and `turnScreenOn`.

```xml
<!-- VoiceInteractionService: the system binds to this -->
<service
    android:name=".assist.NoteAssistService"
    android:label="@string/app_name"
    android:permission="android.permission.BIND_VOICE_INTERACTION"
    android:exported="true">
    <meta-data
        android:name="android.voice_interaction"
        android:resource="@xml/assist_service" />
    <intent-filter>
        <action android:name="android.service.voice.VoiceInteractionService" />
    </intent-filter>
</service>

<!-- VoiceInteractionSessionService: creates sessions -->
<service
    android:name=".assist.NoteAssistSessionService"
    android:permission="android.permission.BIND_VOICE_INTERACTION"
    android:exported="false" />

<!-- Note capture activity: shows over lock screen -->
<activity
    android:name=".NoteCaptureActivity"
    android:exported="false"
    android:showWhenLocked="true"
    android:turnScreenOn="true" />
```

**Notes:**
- `BIND_VOICE_INTERACTION` on the services prevents arbitrary apps from binding.
  You do NOT `<uses-permission>` this — it's declared *on* the service.
- No special permissions needed (no RECORD_AUDIO, no CAMERA, etc.).
- `showWhenLocked="true"` and `turnScreenOn="true"` are the XML equivalents of
  calling the methods in code. They let the activity display over the lock
  screen and wake the display.

### 2. res/xml/assist_service.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<voice-interaction-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:sessionService="com.carsondavis.notetaker.assist.NoteAssistSessionService"
    android:supportsAssist="true"
    android:supportsLaunchVoiceAssistFromKeyguard="true" />
```

**Key attributes:**
- `sessionService` — required, fully qualified class name
- `supportsAssist="true"` — registers as an assist handler
- `supportsLaunchVoiceAssistFromKeyguard="true"` — enables lock screen launch.
  Without this, the long-press from lock screen does nothing.

### 3. NoteAssistService.kt (VoiceInteractionService)

```kotlin
class NoteAssistService : VoiceInteractionService() {

    override fun onLaunchVoiceAssistFromKeyguard() {
        // Called when assist is triggered from the lock screen.
        val intent = Intent(this, NoteCaptureActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }
}
```

This is the lock screen entry point. When the user long-presses the side key
while the device is locked, Android calls `onLaunchVoiceAssistFromKeyguard()`
on this service. The service just starts the note capture activity.

The system keeps this service bound while the app is the default assistant —
keep it lightweight (no heavy work in `onCreate()`).

### 4. NoteAssistSessionService.kt (Boilerplate)

```kotlin
class NoteAssistSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return NoteAssistSession(this)
    }
}
```

Pure boilerplate. Creates a session when the system requests one (which happens
when the assist is triggered while the device is *unlocked*).

### 5. NoteAssistSession.kt (Unlocked Launch Path)

```kotlin
class NoteAssistSession(context: Context) : VoiceInteractionSession(context) {

    override fun onPrepareShow(args: Bundle?, showFlags: Int) {
        super.onPrepareShow(args, showFlags)
        setUiEnabled(false)  // Don't show the default session overlay
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val intent = Intent(context, NoteCaptureActivity::class.java)
        startAssistantActivity(intent)
    }
}
```

This handles the *unlocked* case (user long-presses side key while device is
already unlocked). The locked case goes through `onLaunchVoiceAssistFromKeyguard()`
on the service instead.

- `setUiEnabled(false)` — prevents the system from showing a default overlay
  window on top of your activity
- `startAssistantActivity()` — the preferred way to launch an activity from a
  session (automatically adds `FLAG_ACTIVITY_NEW_TASK`)

### 6. NoteCaptureActivity.kt (Lock Screen Behavior)

The activity itself just needs standard lock screen flags. Since
`showWhenLocked` and `turnScreenOn` are set in the manifest, no code-level
calls are needed. The activity has full access to:

- Network (send note to GitHub API)
- Storage (queue notes offline)
- Keyboard / text input
- Microphone (if permission granted, for the keyboard's voice-to-text)

When the user presses Back or Home, the activity finishes and the lock screen
reappears automatically.

To transition from quick-capture to the full app (e.g., viewing note history),
use `KeyguardManager.requestDismissKeyguard()` to prompt for
fingerprint/PIN/face, then launch the main activity.

---

## User Setup (One-Time)

The app cannot programmatically set itself as the default assistant. The user
must do it manually. The app should detect on launch whether it holds
`ROLE_ASSISTANT` and guide the user if not:

```kotlin
val roleManager = getSystemService(RoleManager::class.java)
if (!roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
    // Guide user to settings
    startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
}
```

**Important**: `RoleManager.createRequestRoleIntent()` does NOT work for
`ROLE_ASSISTANT`. The system ignores the request. You must send the user to
the settings screen directly.

### Samsung Setup Path

1. Open the app, tap "Set as default assistant" (opens system settings)
2. **Settings > Apps > Default Apps > Digital assistant app** > select this app
3. **Settings > Advanced Features > Side Button > Press and hold** > ensure
   "Digital assistant" is selected (not "Power off menu")

Step 3 is critical — if long-press is set to "Power off menu", the side key
won't trigger the assistant regardless of which app is default.

---

## Lock Screen Security Model

Activities shown via `setShowWhenLocked(true)` are NOT additionally sandboxed.
The lock screen is a UI barrier, not a permission barrier. The app retains all
granted permissions.

For the note-taking app, implement a two-tier model (same as camera):

1. **Quick capture (no auth)** — text input, save note. Works over the keyguard.
2. **Full app access (auth required)** — browsing notes, settings, account.
   Trigger `requestDismissKeyguard()` which prompts for biometric/PIN.

This mirrors how the camera works: capture is instant, but viewing the gallery
requires authentication.

---

## API Stability

The `VoiceInteractionService` API has been stable since Android 5.0 (API 21)
with no breaking changes through Android 16.

| Feature | Minimum API |
|---|---|
| VoiceInteractionService | API 21 (Android 5.0) |
| RoleManager / ROLE_ASSISTANT | API 29 (Android 10) |
| Manifest `showWhenLocked` / `turnScreenOn` | API 27 (Android 8.1) |

For this app targeting the S24 Ultra (Android 16), all of these are available.
A reasonable minimum SDK for broader distribution would be API 29 (Android 10),
covering all Galaxy S21+ devices.

---

## Alternatives Explored

Detailed research on the alternatives that were considered and rejected is
available in the research folder:

- [`research/android-power-button-triple-press/report.md`](research/android-power-button-triple-press/report.md)
  — Triple-press detection via SCREEN_OFF counting, foreground service approach,
  OEM conflicts, Play Store policy
- [`research/android-lock-screen-launch/report.md`](research/android-lock-screen-launch/report.md)
  — How the camera double-press works internally (AOSP PhoneWindowManager →
  GestureLauncherService chain), lock screen display APIs, Quick Settings tile,
  notification actions, full-screen intents
- [`research/android-assist-api/report.md`](research/android-assist-api/report.md)
  — VoiceInteractionService implementation details, Samsung ROLE_ASSISTANT
  behavior, lock screen support, Play Store policy for assist apps
