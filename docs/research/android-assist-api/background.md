# Research: Android Assist API / Digital Assistant Registration

**Date:** 2026-02-09
**Description:** How to register an Android app as a "digital assistant" so it appears in the default assistant picker and Samsung's side button settings. Focus on minimal implementation for a note-taking app.

## Sources

[1]: https://developer.android.com/reference/android/service/voice/VoiceInteractionService "VoiceInteractionService | Android Developers"
[2]: https://code.tutsplus.com/quick-tip-how-to-use-androids-assist-api--cms-27933t "Quick Tip: How to Use Android's Assist API | Envato Tuts+"
[3]: https://android.googlesource.com/platform/development/+/refs/heads/main/samples/VoiceInteractionService/ "VoiceInteractionService sample - AOSP"
[4]: https://source.android.com/docs/automotive/voice/voice_interaction_guide "About voice interaction | AOSP"
[5]: https://dev.to/tkuenneth/on-building-a-digital-assistant-for-the-rest-of-us-part-3-4e0k "On building a digital assistant for the rest of us (part 3)"
[6]: https://samsung.com/us/support/answer/ANS10001575/ "Change the device assistant app on your Galaxy phone | Samsung"
[7]: https://source.android.com/docs/automotive/voice/voice_interaction_guide/app_development "VIA App Development | AOSP"
[8]: https://accessibleandroid.com/setting-google-assistant-to-a-long-press-of-the-side-key-on-a-samsung-phone/ "Setting Google Assistant to Side Key | Accessible Android"
[9]: https://xdaforums.com/t/this-is-how-you-assign-google-assistant-to-the-power-button-instead-of-bixby.4563361/ "Assign Google Assistant to power button | XDA Forums"
[10]: https://nerdytechblog.com/how-to-change-side-button-function-on-samsung/ "How to Change Side Button Function on Samsung | NerdyTechBlog"
[11]: https://android.googlesource.com/platform/development/+/refs/heads/main/samples/VoiceInteractionService/AndroidManifest.xml "AOSP VoiceInteractionService sample - AndroidManifest.xml"
[12]: https://developer.android.com/reference/android/service/voice/VoiceInteractionSession "VoiceInteractionSession | Android Developers"
[13]: https://emanual.github.io/Android-docs/reference/android/service/voice/VoiceInteractionSession.html "VoiceInteractionSession | Android SDK"
[14]: https://android.googlesource.com/platform/cts/+/master/tests/tests/voiceinteraction/service/res/xml/interaction_service.xml "CTS test voice-interaction-service XML config"
[15]: https://developer.android.com/guide/topics/permissions/default-handlers "Permissions used only in default handlers | Android Developers"
[16]: https://support.google.com/googleplay/android-developer/answer/16585319 "Permissions and APIs that Access Sensitive Information | Play Console"
[17]: https://www.addictivetips.com/android/replace-google-assistant-with-any-app-android-no-root/ "How to replace Google Assistant with any app on Android"
[18]: https://developer.android.com/training/articles/assistant "Optimizing Contextual Content for the Assistant | Android Developers"
[19]: https://code.yawk.at/android/android-9.0.0_r35/android/service/voice/VoiceInteractionService.java "VoiceInteractionService source code (Android 9)"
[20]: https://www.samsung.com/us/support/answer/ANS10004856/ "Use Gemini or Google Assistant on your Galaxy phone | Samsung"
[21]: https://www.androidpolice.com/features-samsung-removed-ruined-one-ui-7/ "6 features Samsung removed or ruined in One UI 7 | Android Police"
[22]: https://source.android.com/docs/automotive/voice/voice_interaction_guide/integration_flows "VIA Integration Flows | AOSP"

## Research Log

---

### Search: "Android Assist API VoiceInteractionService register as default digital assistant app"

- **VoiceInteractionService is the primary mechanism** for registering as an assist app. The assistant app must provide an implementation of `VoiceInteractionService`, `VoiceInteractionSessionService`, and `VoiceInteractionSession`, and requires the `BIND_VOICE_INTERACTION` permission ([VoiceInteractionService docs][1])
- **The service runs continuously** when selected as default assistant -- the system keeps the current VoiceInteractionService always running, so it should be kept lightweight ([VoiceInteractionService docs][1])
- **Voice interaction config file** must have a root element `voice-interaction-service` specifying fully qualified names of both the VoiceInteractionService and VoiceInteractionSessionService subclasses ([AOSP voice interaction guide][4])
- **User sets default** via Settings > Apps > Default Apps > Assist & voice input > Assist app, or on newer versions Settings > Apps > Default Apps > Digital assistant app ([VoiceInteractionService docs][1])

---

### Search: "Android manifest register as assist app default digital assistant picker minimal implementation"

- **Minimal approach: ROLE_ASSISTANT** -- To qualify for the assistant role, add an intent filter with `android.intent.action.ASSIST` and `android.intent.category.DEFAULT` to an Activity ([DEV Community article][5])
- **Two paths exist**: (1) simple Activity with ACTION_ASSIST intent filter, or (2) full VoiceInteractionService implementation ([DEV Community article][5])
- **RoleManager** can be used to request `ROLE_ASSISTANT` programmatically since API 29 ([DEV Community article][5])
- **Samsung-specific path**: Samsung has its own settings path: Settings > Advanced Features > Side Button > Press and Hold, where user can choose their assistant app ([Samsung support][6])

---

### Deep dive: DEV Community article (part 3) - Building a digital assistant

- **Activity-only approach works for ROLE_ASSISTANT**: The article demonstrates using ONLY an Activity with `ACTION_ASSIST` intent filter -- no VoiceInteractionService needed to appear in the assistant picker ([DEV Community article][5])
- **Manifest is minimal**: Just add the intent filter to your Activity:
  ```xml
  <activity android:name=".MainActivity">
    <intent-filter>
      <action android:name="android.intent.action.ASSIST" />
      <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
  </activity>
  ```
  ([DEV Community article][5])
- **RoleManager.ROLE_ASSISTANT check**: Use `RoleManager.isRoleAvailable()` and `isRoleHeld()` to check status. Use `Settings.ACTION_VOICE_INPUT_SETTINGS` intent to send user to settings ([DEV Community article][5])
- **CRITICAL: `createRequestRoleIntent()` does NOT work for ROLE_ASSISTANT** -- the standard role request flow fails, so you must navigate to settings instead ([DEV Community article][5])
- **RoleManagerCompat.ROLE_ASSISTANT** is the constant to use ([DEV Community article][5])

---

### Deep dive: VoiceInteractionService API reference

- **Three components required for full VoiceInteractionService**: VoiceInteractionService (top-level, always running), VoiceInteractionSessionService (creates sessions), VoiceInteractionSession (handles UI per session) ([VoiceInteractionService docs][1])
- **Available since API 21** (Android 5.0 Lollipop) ([VoiceInteractionService docs][1])
- **Manifest for full service**:
  ```xml
  <service
      android:name=".YourVoiceInteractionService"
      android:permission="android.permission.BIND_VOICE_INTERACTION">
      <intent-filter>
          <action android:name="android.service.voice.VoiceInteractionService" />
      </intent-filter>
      <meta-data
          android:name="android.voice_interaction"
          android:resource="@xml/voice_interaction_service" />
  </service>
  ```
  ([VoiceInteractionService docs][1])
- **NOT limited to system apps** -- third-party apps CAN implement this, the `BIND_VOICE_INTERACTION` permission is declared on the service to prevent other apps from binding to it ([VoiceInteractionService docs][1])
- **Architecture flow**: VoiceInteractionService -> VoiceInteractionSessionService -> VoiceInteractionSession (handles UI and commands) ([VoiceInteractionService docs][1])

---

### Search: "Samsung side button long press digital assistant app custom app OneUI how to register third party app"

- **Samsung side button routes through standard Android assist mechanism**: Settings > Advanced Features > Side Button > Long press shows available digital assistant apps. The list comes from Android's standard assist app picker ([Samsung support][6], [Accessible Android][8])
- **Samsung Good Lock / Registar workaround exists**: For more customization, Samsung's Good Lock app with the "Registar" module allows remapping buttons to arbitrary apps. But this is separate, non-standard ([NerdyTechBlog][10])
- **Key insight: Samsung's side button "Digital assistant" option lists the same apps as Android's standard default assistant picker** -- not a separate Samsung-specific API ([Samsung support][6])

---

### Search: "Android VoiceInteractionService minimal example Kotlin complete code manifest xml configuration"

- **Minimal VoiceInteractionService Kotlin implementation**:
  ```kotlin
  class MyInteractionService : VoiceInteractionService() {
      override fun onCreate() { super.onCreate() }
  }
  ```
  ([VIA App Development][7])
- **XML config file needed**: Must create `res/xml/interaction_service.xml` with a `<voice-interaction-service>` root element ([VIA App Development][7])

---

### Deep dive: AOSP VoiceInteractionService sample manifest

- **AOSP sample package**: `com.example.android.voiceinteractor` ([AOSP sample][11])
- **Additional services in sample** for hotword detection are NOT required for basic assist ([AOSP sample][11])
- **Permissions in sample** (CAMERA, RECORD_AUDIO, etc.) are for full voice assistant; a simple assist app wouldn't need these ([AOSP sample][11])

---

### Search: "Android assist app lock screen behavior setShowWhenLocked voice interaction over lock screen"

- **VoiceInteractionSession has its own window** that can display over the lock screen automatically -- this is how Google Assistant shows over lock screen ([VoiceInteractionSession docs][12])
- **Two UI approaches for VoiceInteractionSession**:
  1. Override `VoiceInteractionSession#onCreateContentView()` -- renders content directly in the session window
  2. Launch an Activity via `VoiceInteractionSession#startAssistantActivity()` -- starts an activity from the session context
  ([VoiceInteractionSession docs][12], [AOSP voice interaction guide][7])
- **For Activity-based approach, use setShowWhenLocked**: `Activity.setShowWhenLocked(true)` and `Activity.setTurnScreenOn(true)` (API 27+) ([VoiceInteractionSession docs][13])
- **Key distinction**: VoiceInteractionSession window shows over lock screen automatically (system-level window); plain Activity needs `setShowWhenLocked(true)` ([VoiceInteractionSession docs][12])

---

### Search: "Google Play Store policy restrictions app registering as default assistant"

- **No explicit ban on third-party assistants**: Play Store does NOT prohibit apps from registering as assistants. Microsoft Copilot, Alexa, etc. do this ([Play Console policy][16])
- **Core functionality requirement**: App must provide assistant-like functionality when launched ([Play Console policy][16])
- **Permission restrictions only matter for restricted permissions**: A note-taking app that registers as assistant to be launched via side button does not need any restricted permissions ([Default handlers docs][15])

---

### Deep dive: CTS test voice-interaction-service XML config

- **Complete XML config format**:
  ```xml
  <voice-interaction-service xmlns:android="http://schemas.android.com/apk/res/android"
      android:sessionService="com.example.MainInteractionSessionService"
      android:recognitionService="com.example.MainRecognitionService"
      android:settingsActivity="com.example.SettingsActivity"
      android:supportsAssist="false"
      android:supportsLocalInteraction="true" />
  ```
  ([CTS test config][14])
- **Key XML attributes**: sessionService (REQUIRED), recognitionService (optional), settingsActivity (optional), supportsAssist (boolean), supportsLocalInteraction (boolean) ([CTS test config][14])

---

### Deep dive: Envato Tuts+ Android Assist API tutorial

- **VoiceInteractionSessionService is simple** -- just returns a new session:
  ```java
  public VoiceInteractionSession onNewSession(Bundle bundle) {
      return new MyAssistantSession(this);
  }
  ```
  ([Envato tutorial][2])
- **VoiceInteractionService itself can be an empty stub** ([Envato tutorial][2])
- **VoiceInteractionSession.onHandleAssist()** is where actual work happens ([Envato tutorial][2])
- **Both services need BIND_VOICE_INTERACTION permission** in manifest ([Envato tutorial][2])

---

### Search: "Android 16 changes assist API VoiceInteractionService digital assistant 2025 2026"

- **No major Android 16 changes to the Assist API** -- the API has been stable since API 21 with no breaking changes or deprecation signals ([VoiceInteractionService docs][1])

---

### Search: "can you have both Google Assistant and custom assist app Android switch between assistants"

- **One-or-the-other for default assistant** -- Android only allows one app to hold `ROLE_ASSISTANT` at a time ([Optimizing for Assistant docs][18])
- **Switching is easy but manual** -- ~5 seconds via Settings ([Replace Google Assistant guide][17])
- **Google Assistant remains installed** -- just won't be triggered by the assist gesture. User can still open it from app icon or "Hey Google" ([Replace Google Assistant guide][17])

---

### Search: "android voice-interaction-service supportsLaunchVoiceAssistFromKeyguard attribute XML"

- **`supportsLaunchVoiceAssistFromKeyguard` is a real attribute**: When true, `onLaunchVoiceAssistFromKeyguard()` is called when assist triggered from lock screen ([VoiceInteractionService source code][19])
- **Lock screen implementation**: Must start a new activity with `setShowWhenLocked(true)` to display on top of the lock screen ([VoiceInteractionService docs][1])
- **Complete XML config with all attributes**:
  ```xml
  <voice-interaction-service xmlns:android="http://schemas.android.com/apk/res/android"
      android:sessionService="com.example.MySessionService"
      android:recognitionService="com.example.MyRecognitionService"
      android:settingsActivity="com.example.MySettingsActivity"
      android:supportsAssist="true"
      android:supportsLaunchVoiceAssistFromKeyguard="true"
      android:supportsLocalInteraction="true" />
  ```
  ([VoiceInteractionService source code][19])

---

### Search: "Samsung OneUI 7 OneUI 8 side button digital assistant list"

- **OneUI 7 changed default from Bixby to Gemini** (Jan 2025, Galaxy S25) ([Samsung Gemini/Assistant support][20])
- **Side button long-press goes to Settings > Apps > Default Apps > Digital Device Assistant** -- standard Android picker ([Samsung Gemini/Assistant support][20])
- **OneUI 7 removed swipe-from-corner assistant gesture** -- side button long-press is now the primary trigger ([Android Police][21])
- **Any app registered as an Android digital assistant appears in Samsung's picker** -- confirmed standard ROLE_ASSISTANT mechanism ([Samsung support][6], [Samsung Gemini/Assistant support][20])

---

### Search: "VoiceInteractionSession startAssistantActivity onShow launch activity"

- **Pattern for launching an Activity from VoiceInteractionSession**:
  ```java
  @Override
  protected void onPrepareShow(Bundle args, int showFlags) {
      super.onPrepareShow(args, showFlags);
      setUiEnabled(false); // Disable default session UI
  }

  @Override
  protected void onShow(String action, Bundle args, int showFlags) {
      closeSystemDialogs();
      Intent intent = new Intent(getContext(), NoteCaptureActivity.class);
      startAssistantActivity(intent);
  }
  ```
  ([VIA Integration Flows][22])
- **`startAssistantActivity()` automatically sets FLAG_ACTIVITY_NEW_TASK** ([VoiceInteractionSession docs][12])
- **`setUiEnabled(false)` in onPrepareShow()** disables the default session overlay window, allowing the activity to be the only UI ([VIA Integration Flows][22])
- **Communication between session and activity** requires internal Intents or service binding -- when `onHide()` is called, the session must be able to notify the activity ([VIA Integration Flows][22])
