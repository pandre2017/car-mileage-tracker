# Android app

Open this folder in Android Studio. Use JDK 21 for Gradle and Android SDK 36. Build with the committed wrapper:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The unconfigured debug build opens and explains the missing sign-in configuration. Supply these settings in `android/local.properties` alongside `sdk.dir`. This file is ignored by Git. Gradle project properties (from `-P` or `~/.gradle/gradle.properties`) take precedence over `local.properties`:

```properties
API_BASE_URL=http://10.0.2.2:8080/
FIREBASE_PROJECT_ID=YOUR_PROJECT_ID
FIREBASE_API_KEY=YOUR_FIREBASE_ANDROID_API_KEY
FIREBASE_APP_ID=YOUR_FIREBASE_ANDROID_APP_ID
GOOGLE_WEB_CLIENT_ID=YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
```

Use the **web/server OAuth client ID**, not the Android OAuth client ID. Register `com.pandre.mileage` and your signing SHA fingerprints in Firebase, enable Google Authentication, and restrict the Firebase API key appropriately. Firebase is initialized programmatically, so a `google-services.json` file and the Google Services Gradle plugin are not required. These client configuration identifiers do not grant backend access by themselves; the API checks provisioning and car membership.

The debug API default is `http://10.0.2.2:8080/` for an Android emulator reaching the host. Debug builds allow cleartext for local development; release builds require HTTPS. Real Google sign-in needs a configured Firebase project and a backend verifying tokens for that same project. The backend emulator/password setup is intended for API development; no authentication bypass is built into the Android app.

After configuration, provision both Firebase UIDs and shared cars using the backend runbook. Select a car, enter date/odometer/liters/total paid, and save. Pending entries persist in Room and upload through WorkManager once online. Open history or tap refresh to fetch the complete server history. Failed records remain visible with their reason; administrative corrections currently require the project administrator.

Only one Google UID belongs to an installation. Sign-in renewal must use that account. To switch accounts, clear app data (which removes unsynced entries). The app disables Android backup to avoid restoring another installation's local cache.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. A signed release key and public Play Store distribution are not configured.

## Configure Firebase before Google sign-in

1. Open Firebase Console and add Firebase to your existing Google Cloud project `YOUR_PROJECT_ID`.
2. Register an Android app with package `com.pandre.mileage`. Run `./gradlew :app:signingReport` and register the debug signing SHA-1 and SHA-256 fingerprints.
3. In Authentication, enable Google as a sign-in provider and choose the support email. Download the updated Android `google-services.json` after enabling Google.
4. Copy values into `local.properties`: `FIREBASE_PROJECT_ID` from `project_info.project_id`, `FIREBASE_APP_ID` from the matching client's `client_info.mobilesdk_app_id`, `FIREBASE_API_KEY` from that client's `api_key[].current_key`, and `GOOGLE_WEB_CLIENT_ID` from `oauth_client[].client_id` whose `client_type` is **3** (web). Select the client matching `com.pandre.mileage`; an OAuth client of type 1 is the Android client and is not the value required here.
5. Sync Gradle and rebuild/reinstall. Properties are compiled into BuildConfig, so changing them requires rebuilding.

### Real Google sign-in with the API still running locally

Google accounts create users in real Firebase Authentication; the seeded emulator password users are separate identities. To test the actual Android sign-in while retaining local Firestore, restart the API in a terminal with:

```sh
export GCLOUD_PROJECT=YOUR_PROJECT_ID
unset FIREBASE_AUTH_EMULATOR_HOST
export FIRESTORE_EMULATOR_HOST=127.0.0.1:8081
npm run dev
```

Keep the Firestore emulator running. With the Auth emulator variable unset, the server verifies real Firebase ID tokens for the real project. In the Firestore emulator UI, select the real project namespace and manually provision `users/{realFirebaseUid}` and shared cars using the backend runbook. Records seeded under `demo-car-mileage` are in a different project namespace and will not appear. Both real users must sign in once before you copy their UIDs from Firebase Authentication. If needed, restart the emulators with `--project YOUR_PROJECT_ID` instead of the demo command so the emulator UI uses the same project namespace.

This setup uses real Google Authentication with local application data. Cloud Run and a hosted Firestore database can be configured later. For an Android emulator, `10.0.2.2` reaches your Mac; for a physical phone, use a reachable LAN address instead and keep the connection local/trusted.
