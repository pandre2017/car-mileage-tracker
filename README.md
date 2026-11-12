# Car Mileage Tracker

Personal Android fuel log for two Google-account users sharing cars. Kotlin/Compose Android app, TypeScript/Express API, Firebase Authentication, Firestore, and Cloud Run in one repository.

## Start here

- [Android setup](android/README.md): Android Studio, Firebase configuration, debug APK.
- [Android architecture](android/architecture.md): Clean Architecture modules, UseCases and dependency injection.
- [Backend setup](backend/README.md): local emulators, API tests, manual user/car provisioning, deployment.
- [Validation and remaining setup](docs/IMPLEMENTATION_STATUS.md)
- [API contract](docs/openapi.yaml)
- [Architecture and scope](ANDROID_MILEAGE_TRACKER_PLAN.md)
- [Android conventions and test boundaries](docs/ANDROID_CONVENTIONS.md)

## AI guidance and Android modules

AI instructions are in [android/AGENTS.md](android/AGENTS.md). The six copied skills are in the hidden [`android/.agents/skills/`](android/.agents/skills/) folder. These instructions apply to Android. Open `android/` in Project view to browse them; `.agents` is a hidden folder.

| Skill | Workflow |
| --- | --- |
| [android-kotlin-style](android/.agents/skills/android-kotlin-style/SKILL.md) | Kotlin and Compose conventions |
| [mvvm-clean-architecture-review](android/.agents/skills/mvvm-clean-architecture-review/SKILL.md) | Architecture and state ownership |
| [android-feature-development](android/.agents/skills/android-feature-development/SKILL.md) | Feature implementation |
| [mileage-bug-analysis](android/.agents/skills/mileage-bug-analysis/SKILL.md) | Evidence-based debugging |
| [mileage-unit-tests](android/.agents/skills/mileage-unit-tests/SKILL.md) | Domain, data and ViewModel tests |
| [mileage-ui-tests](android/.agents/skills/mileage-ui-tests/SKILL.md) | Isolated Compose/device tests |

The Android Gradle project contains three modules:

- `android/app/`: Compose UI, ViewModels, Android entry points and manual dependency composition.
- `android/domain/`: pure Kotlin models, application UseCases, repository/scheduling contracts and business rules.
- `android/data/`: Android library implementing domain contracts with Room, Retrofit and Firebase session adapters.

Dependencies: `app -> domain`, `app -> data`, `data -> domain`. The domain module has no Android plugin or dependency on app/data. Data cannot import application code because it has no app dependency.

## Implemented

Google credential sign-in integration, provisioning/membership authorization, shared-car listing, transactional fueling creation, full-history retrieval, integer money/volume storage, Room caching, pending uploads, WorkManager retry, and a Compose entry/history interface. The API rejects conflicting dates/odometer readings and treats identical request retries as one entry.

Firebase and API settings are supplied through local configuration. See the Android and backend setup guides for details.

## Checks

```sh
cd backend
npm ci
npm test
npm run test:integration
# Separate terminal / working directory:
cd android
./gradlew :domain:test :data:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :data:lintDebug
```

Use Node.js 24 and JDK 21. Emulator integration tests require Java and download the Firestore emulator on first run. Android and backend CI workflows run independently, with both triggered by API-contract changes.

## Data rules

- km, liters, EUR; one entry per car/date (Europe/Lisbon).
- Liters and total paid accept at most two decimals; integer hundredths are authoritative.
- Unit price is calculated with half-up rounding to two decimals.
- Fuel purchases are recorded; fuel consumption is not inferred.
- One account per installation; clear app data to switch (unsynced entries are lost).
- Both users and all shared cars are provisioned administratively.
