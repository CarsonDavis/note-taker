# Registering an Android App as a Digital Assistant

## Executive Summary

A note-taking app can register as a "digital assistant" and appear in both Android's default assistant picker and Samsung's side button long-press settings. Samsung uses the standard Android `ROLE_ASSISTANT` mechanism -- there is no Samsung-specific API needed.

There are two approaches, and the **recommended path is the full VoiceInteractionService approach** (not just an Activity with `ACTION_ASSIST`). The VoiceInteractionService approach gives you lock screen support, a system-managed session window, and is the officially documented mechanism. Despite requiring three classes, the implementation is minimal -- most are empty stubs.

The tradeoff: setting your app as the default assistant replaces Google Assistant / Gemini / Bixby for the assist gesture. There is no way to have both. The user can still access Google Assistant via its app icon or "Hey Google" voice activation.

---

## Two Approaches Compared

| Aspect | Activity + ACTION_ASSIST | VoiceInteractionService (recommended) |
|--------|--------------------------|---------------------------------------|
| Manifest complexity | 1 intent filter on Activity | 2 services + XML config + Activity |
| Code complexity | None beyond the Activity | 3 stub classes (~10 lines each) |
| Shows in assistant picker | Yes | Yes |
| Shows in Samsung side button | Yes | Yes |
| Lock screen support | Requires manual `setShowWhenLocked` | Built-in via session window + `supportsLaunchVoiceAssistFromKeyguard` |
| Background service | No | Yes (lightweight, always running) |
| Receives assist context data | No | Yes (via `onHandleAssist`) |
| Official/documented | Partially | Fully |
| Risk of breaking on updates | Higher (less documented) | Lower (stable API since Android 5.0) |

**Verdict:** Use VoiceInteractionService. The extra boilerplate is trivial, and you get proper lock screen support and forward compatibility.

---

## Minimal VoiceInteractionService Implementation

### File Structure

```
app/src/main/
  java/com/example/notetaker/
    assist/
      NoteAssistService.kt           # VoiceInteractionService (empty stub)
      NoteAssistSessionService.kt    # Creates sessions
      NoteAssistSession.kt           # Launches your Activity
    NoteCaptureActivity.kt           # Your actual note-taking UI
  res/
    xml/
      assist_service.xml             # Voice interaction config
  AndroidManifest.xml
```

### 1. AndroidManifest.xml

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.notetaker">

    <application
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher">

        <!-- Main launcher activity (your normal app entry point) -->
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Note capture activity (launched by assist session) -->
        <activity
            android:name=".NoteCaptureActivity"
            android:exported="false"
            android:showWhenLocked="true"
            android:turnScreenOn="true" />

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

    </application>

</manifest>
```

**Notes on the manifest:**
- `android:permission="android.permission.BIND_VOICE_INTERACTION"` on both services prevents arbitrary apps from binding to them. You do NOT need to `<uses-permission>` this -- it's declared on the service, not held by your app.
- `android:showWhenLocked="true"` and `android:turnScreenOn="true"` on `NoteCaptureActivity` are the XML equivalents of calling `setShowWhenLocked(true)` / `setTurnScreenOn(true)` in code. These let the activity display over the lock screen.
- No special permissions needed (no RECORD_AUDIO, no CAMERA, etc.) for a simple assist app.

### 2. res/xml/assist_service.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<voice-interaction-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:sessionService="com.example.notetaker.assist.NoteAssistSessionService"
    android:supportsAssist="true"
    android:supportsLaunchVoiceAssistFromKeyguard="true" />
```

**Key attributes:**
- `android:sessionService` -- REQUIRED. Fully qualified class name of your VoiceInteractionSessionService.
- `android:supportsAssist="true"` -- Tells the system this app handles the assist action.
- `android:supportsLaunchVoiceAssistFromKeyguard="true"` -- Enables launching from the lock screen. When the user triggers assist from the lock screen, the system calls `onLaunchVoiceAssistFromKeyguard()` on your service.
- `android:recognitionService` -- OMITTED. Only needed for speech recognition; we don't need it.
- `android:settingsActivity` -- OMITTED. Optional; points to a settings screen for your assistant.

### 3. NoteAssistService.kt (VoiceInteractionService)

```kotlin
package com.example.notetaker.assist

import android.content.Intent
import android.service.voice.VoiceInteractionService

class NoteAssistService : VoiceInteractionService() {

    override fun onLaunchVoiceAssistFromKeyguard() {
        // Called when assist is triggered from the lock screen.
        // Launch the note capture activity with lock screen flags.
        val intent = Intent(this, com.example.notetaker.NoteCaptureActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }
}
```

This class is mostly an empty stub. The only method worth overriding is `onLaunchVoiceAssistFromKeyguard()` for lock screen support. The system keeps this service running while your app is the default assistant, so keep it lightweight -- no heavy initialization in `onCreate()`.

### 4. NoteAssistSessionService.kt (VoiceInteractionSessionService)

```kotlin
package com.example.notetaker.assist

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class NoteAssistSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return NoteAssistSession(this)
    }
}
```

This is boilerplate. It creates a new session instance when the system requests one.

### 5. NoteAssistSession.kt (VoiceInteractionSession)

```kotlin
package com.example.notetaker.assist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession

class NoteAssistSession(context: Context) : VoiceInteractionSession(context) {

    override fun onPrepareShow(args: Bundle?, showFlags: Int) {
        super.onPrepareShow(args, showFlags)
        // Disable the default session overlay window.
        // We want to show our own Activity instead.
        setUiEnabled(false)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        // Launch the note capture activity.
        val intent = Intent(context, com.example.notetaker.NoteCaptureActivity::class.java)
        startAssistantActivity(intent)
    }

    override fun onHandleAssist(
        data: Bundle?,
        structure: android.app.assist.AssistStructure?,
        content: android.app.assist.AssistContent?
    ) {
        // Called with context from the foreground app.
        // For a note-taking app, you can ignore this or use it
        // to pre-fill note content from the current screen.
    }
}
```

**Key points:**
- `setUiEnabled(false)` in `onPrepareShow()` -- prevents the system from creating a default overlay window. Without this, you'd see both the session overlay and your Activity.
- `startAssistantActivity(intent)` -- launches your Activity with `FLAG_ACTIVITY_NEW_TASK` automatically set. This is the preferred way to launch an Activity from a session.
- `onHandleAssist()` -- receives context data from the foreground app (what's on screen). For a note-taking app, this is optional but could be useful for "capture what's on screen" features.

### 6. NoteCaptureActivity.kt (Your actual UI)

```kotlin
package com.example.notetaker

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class NoteCaptureActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // For API < 27 compatibility (if needed):
        // setShowWhenLocked(true)
        // setTurnScreenOn(true)

        setContentView(R.layout.activity_note_capture)
        // Your note-taking UI here
    }
}
```

Since `android:showWhenLocked="true"` and `android:turnScreenOn="true"` are set in the manifest, you don't need to call them in code (unless you need dynamic control).

---

## Guiding the User to Set Your App as Default

You cannot programmatically set your app as the default assistant. The user must do it manually. Here's how to guide them:

```kotlin
import android.app.role.RoleManager
import android.content.Intent
import android.provider.Settings

fun checkAndPromptAssistantRole(activity: Activity) {
    val roleManager = activity.getSystemService(RoleManager::class.java)

    if (!roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
        // Device doesn't support the assistant role (rare)
        return
    }

    if (roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
        // Already the default assistant
        return
    }

    // IMPORTANT: createRequestRoleIntent() does NOT work for ROLE_ASSISTANT.
    // You must send the user to system settings instead.
    val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
    activity.startActivity(intent)
}
```

**Why `createRequestRoleIntent()` doesn't work:** Unlike other roles (SMS, browser, etc.), the `ROLE_ASSISTANT` role does not support the standard request flow. The system simply ignores the request. You must send the user to Settings instead.

On Samsung, the equivalent path is: Settings > Advanced Features > Side Button > Press and Hold > Digital assistant, which leads to the same system picker.

---

## Samsung Side Button Behavior

Samsung's side button long-press "Digital assistant" option uses the standard Android `ROLE_ASSISTANT` mechanism. There is no Samsung-specific API or manifest entry needed.

**How it works on Samsung (OneUI 7+):**
1. Settings > Advanced Features > Side Button > "Press and hold" must be set to "Digital assistant" (not "Power off menu")
2. The "Digital assistant" option shows a link to Settings > Apps > Default Apps > Digital assistant app
3. That picker lists every app on the device that qualifies for `ROLE_ASSISTANT`
4. Your app will appear there if it has the VoiceInteractionService (or ACTION_ASSIST Activity) registered

**OneUI 7 change (Jan 2025):** Samsung switched the default assistant from Bixby to Gemini. They also removed the swipe-from-corner gesture for launching the assistant. The side button long-press is now the primary hardware trigger for the assistant.

---

## Lock Screen Behavior

Three scenarios:

1. **VoiceInteractionSession window (onCreateContentView):** Shows over the lock screen automatically. The session window is a system-level window that renders above the keyguard. No extra flags needed.

2. **Activity launched via startAssistantActivity():** Add `android:showWhenLocked="true"` and `android:turnScreenOn="true"` to the Activity in the manifest. The Activity will display on top of the lock screen without requiring unlock.

3. **Lock screen trigger (supportsLaunchVoiceAssistFromKeyguard):** When this XML attribute is true and the user triggers the assist from the lock screen, the system calls `onLaunchVoiceAssistFromKeyguard()` on your VoiceInteractionService. Your implementation must start an Activity with `FLAG_ACTIVITY_NEW_TASK` and `showWhenLocked` behavior.

For a note-taking app, the recommended approach is option 2 + 3: set `supportsLaunchVoiceAssistFromKeyguard="true"` in the XML config, implement `onLaunchVoiceAssistFromKeyguard()`, and mark your `NoteCaptureActivity` with `showWhenLocked="true"`.

---

## Play Store Policy

No restrictions prevent a note-taking app from registering as a digital assistant. The Play Store policy around default handlers primarily restricts access to sensitive permissions (SMS, call logs, etc.) that are only available to default handlers. A note-taking app:

- Does not need any restricted permissions
- Provides legitimate assistant-like functionality (quick note capture)
- Follows the same pattern as Microsoft Copilot, Alexa, and many other third-party apps that register as assistants

The only requirement is that your app actually functions as an assistant when launched -- it must do something useful when the user triggers the assist action.

---

## The "Losing Google Assistant" Tradeoff

Android only allows one default assistant at a time. Setting your note-taking app as the default means:

**What the user loses:**
- Side button long-press no longer opens Gemini/Google Assistant
- The home button long-press / gesture swipe no longer opens Google Assistant

**What the user keeps:**
- Google Assistant app still works from its icon in the app drawer
- "Hey Google" voice activation still works (if enabled separately)
- All other Google Assistant features remain functional

**Mitigation strategies:**
- Make it easy to switch back: add a "Restore Google Assistant" button in your app's settings
- Consider adding a "launch Google Assistant" button within your note-taking UI
- On Samsung, users can use Good Lock's Routines+ as an alternative to map the side button to any app without changing the default assistant

---

## Android Version Considerations

The VoiceInteractionService API has been stable since Android 5.0 (API 21). No breaking changes have been introduced through Android 16 / OneUI 8.0.

| Feature | Minimum API |
|---------|-------------|
| VoiceInteractionService | API 21 (Android 5.0) |
| RoleManager / ROLE_ASSISTANT | API 29 (Android 10) |
| setShowWhenLocked() / setTurnScreenOn() | API 27 (Android 8.1) |
| Manifest `showWhenLocked` / `turnScreenOn` attributes | API 27 (Android 8.1) |
| FLAG_SHOW_WHEN_LOCKED (deprecated) | API 1 (use for pre-27 compat) |

For a modern app targeting recent Samsung devices (OneUI 7+), API 29+ is a safe minimum. This covers all Galaxy S21 and newer devices.

---

## Complete Checklist

To get your note-taking app to appear in Samsung's side button "Digital assistant" picker:

1. Create `NoteAssistService` extending `VoiceInteractionService`
2. Create `NoteAssistSessionService` extending `VoiceInteractionSessionService`
3. Create `NoteAssistSession` extending `VoiceInteractionSession`
4. Create `res/xml/assist_service.xml` with `<voice-interaction-service>` config
5. Declare both services in `AndroidManifest.xml` with `BIND_VOICE_INTERACTION` permission
6. Mark your note-capture Activity with `showWhenLocked="true"` and `turnScreenOn="true"`
7. In the session's `onShow()`, call `startAssistantActivity()` to launch your Activity
8. Guide the user to Settings > Apps > Default Apps > Digital assistant app to select your app
9. On Samsung: Settings > Advanced Features > Side Button > Press and Hold > Digital assistant

No special permissions. No Samsung-specific APIs. No Play Store policy concerns.
