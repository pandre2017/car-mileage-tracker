# Car Mileage Tracker

Personal Android fuel log for two Google-account users sharing cars. Kotlin/Compose Android app, TypeScript/Express API, Firebase Authentication, Firestore, and Cloud Run in one repository.

## Start here

- [Android setup](android/README.md): Android Studio, Firebase configuration, debug APK.
- [Backend setup](backend/README.md): local emulators, API tests, manual user/car provisioning, deployment.
- [Validation and remaining setup](docs/IMPLEMENTATION_STATUS.md)
- [API contract](docs/openapi.yaml)
- [Architecture and scope](ANDROID_MILEAGE_TRACKER_PLAN.md)

## Implemented

Google credential sign-in integration, provisioning/membership authorization, shared-car listing, transactional fueling creation, full-history retrieval, integer money/volume storage, Room caching, pending uploads, WorkManager retry, and a Compose entry/history interface. The API rejects conflicting dates/odometer readings and treats identical request retries as one entry.

Configuration is intentionally external: the APK builds without a Firebase project but sign-in requires your Firebase values. A real two-phone Google sign-in journey requires provisioning and deployment. Target Google Cloud project: `car-mileage-tracker-514210`. Intended private GitHub repository: `pandre2017/car-mileage-tracker`. Remote creation and cloud configuration remain pending authenticated access.

## Checks

```sh
cd backend
npm ci
npm test
npm run test:integration
# Separate terminal / working directory:
cd android
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Use Node.js 24 and JDK 21. Emulator integration tests require Java and download the Firestore emulator on first run. Android and backend CI workflows run independently, with both triggered by API-contract changes.

## Data rules

- km, liters, EUR; one entry per car/date (Europe/Lisbon).
- Liters and total paid accept at most two decimals; integer hundredths are authoritative.
- Unit price is calculated with half-up rounding to two decimals.
- Fuel purchases are recorded; fuel consumption is not inferred.
- One account per installation; clear app data to switch (unsynced entries are lost).
- Both users and all shared cars are provisioned administratively.

Cloud services have usage allowances, not a guaranteed zero bill. Review billing before enabling Cloud Run. Repository source contains no personal account details or cloud credentials.
