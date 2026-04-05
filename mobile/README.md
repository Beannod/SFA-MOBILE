# SFA Mobile (Android skeleton)

This is a small Android Kotlin skeleton for the SFA app (Compose-based placeholder UI).

Prerequisites
- Android Studio (recommended: latest stable) with Android SDK for API 33 installed
- JDK 11 (project uses Java 11 compatibility)

Quickstart
1. Open the `mobile` folder in Android Studio.
2. Let Gradle sync and install suggested plugins.
3. Run the app on an emulator or device from Android Studio.

Build from command line (Windows PowerShell):

```powershell
cd mobile
.\gradlew.bat clean assembleDebug
```

Notes about the project
- The project uses Android Gradle Plugin `8.0.0` and Kotlin `1.8.0` (see `mobile/build.gradle` and `mobile/app/build.gradle`).
- `compileSdk` and `targetSdk` are set to 33; `minSdk` is 24.
- The app's `MainActivity` is a Compose-based placeholder at `mobile/app/src/main/java/com/example/sfa/MainActivity.kt`.
- The project is configured to use Java 11 compatibility.

Configuring the API base URL
You can provide an API base URL via a Gradle property (recommended for dev/local overrides). Example: add the following to `mobile/gradle.properties` or your `~/.gradle/gradle.properties`:

```
SFA_API_BASE_URL=https://localhost:5001
```

Then reference this value in `build.gradle` (example) to expose it to code via `BuildConfig`:

```groovy
buildTypes {
  debug {
    buildConfigField "String", "SFA_API_BASE_URL", "\"${SFA_API_BASE_URL}\""
  }
}
```

Planned next steps
- Add networking (Retrofit + Moshi/Gson) to call the SFA API
- Implement product add/edit screens and local persistence (Room)
- Add authentication (JWT), token storage, and background sync (WorkManager)

If you want, I can (A) update the project to expose `SFA_API_BASE_URL` in `BuildConfig`, or (B) prepare a short upgrade plan for Gradle/Kotlin versions.
# SFA Mobile (Android skeleton)\r\n\r\nThis is a minimal Android Kotlin skeleton for the SFA app. It's intentionally small — open it in Android Studio:\r\r\n1. Open the `mobile` folder in Android Studio.\r\n2. Let Gradle sync and install suggested plugins.\r\n3. Run the app on an emulator or device.\r\r\nPlanned next steps:\r\n- Add networking (Retrofit) to call the SFA API.\r\n- Implement product add/edit screens and local persistence (Room).\r\n- Add authentication (JWT) and sync logic.\r\n