# Implementation status

Validated locally on 9 November 2026.

## Delivered

- One initialized local Git repository with Android and backend folders.
- Android Google credential/Firebase integration with external project configuration.
- Shared-car/history screens, fuel-entry form, integer decimal parsing and derived unit price.
- Room cache and pending records, complete-history refresh, WorkManager uploads.
- Express/Firebase API with provisioning and membership checks.
- Firestore transaction handling for duplicate UUIDs, one record per date, and chronological odometers.
- Emulator seed, backup/empty-target restore utility, OpenAPI contract, Dockerfile and GitHub workflows.
- Manual provisioning and deployment documentation.

## Validation results

- Backend TypeScript build and formatting checks passed.
- Six backend tests passed, including real Firestore emulator transactions and a concurrent date conflict.
- Three Android decimal/price unit tests passed.
- One emulator instrumented Room test passed, covering replacement of synced history, preservation of pending/failed records and merging a server-confirmed pending UUID.
- Debug APK built and launched successfully on the Pixel 7 emulator.
- Android lint: zero errors, 18 warnings, chiefly newer dependency/tool versions and Kotlin API style suggestions.
- Production npm dependency audit: zero known vulnerabilities after compatible updates.
- The Firebase CLI development dependency tree still reports moderate advisories; it is excluded from the runtime Docker image. Do not use a forced downgrade to resolve them without checking compatibility.

## Account configuration remaining

The intended GitHub destination is the private repository `pandre2017/car-mileage-tracker`. Google Cloud project ID is `YOUR_PROJECT_ID`.

The GitHub remote has not been created or pushed. Firebase has been enabled, the Android app registered, Google sign-in configured, and local users/cars provisioned. The owner confirmed successful sign-in and shared data access from both an Android emulator and a physical phone using the Mac's LAN address. Cloud Run deployment and migration to hosted Firestore remain outstanding.

Real Firebase identifiers and the local API address are supplied through ignored `android/local.properties`. Backend `.firebaserc` identifies the intended cloud project; emulator commands explicitly choose their local project namespace. No authentication bypass is present in the APK.

## Android conventions refactor verified 11 November 2026

WeatherApp's six AI workflows were copied into `android/.agents/skills/` and adapted to mileage tracking. `android/AGENTS.md` documents this application's actual manual composition root and package boundaries. See `docs/ANDROID_CONVENTIONS.md` for the design and test scope.

- Debug APK build passed.
- 18 JVM tests passed, including architecture direction, domain rules, repository/HTTP behavior and controlled ViewModel state/cancellation tests.
- Six instrumentation tests passed on a disposable API 36 emulator: five Compose screen tests and one Room test. A plain test Application prevents production Firebase graph initialization.
- Kotlin style check passed using pinned ktfmt 0.64, official Kotlin style, explicit imports and `.editorconfig`.
- Android lint passed with zero errors and 21 warnings (dependency/SDK updates, application icon/backup configuration and a KTX style suggestion). Versions were not broadly upgraded as part of this refactor.
- The first device attempt on Android 17 preview failed in Espresso's InputManager initialization before assertions. API 36 was installed and used successfully. One genuine duplicate validation-message rendering issue found by the device test was fixed.
- The CI workflow now contains a formatting check and a separate API 36 UI-test job. Remote workflow execution remains unverified until these changes are pushed.

These checks use fakes and local emulators. They do not verify live Firebase availability, full-activity navigation/recreation or process-death restoration. Existing Room schema and API payloads were preserved. No backend source or cloud configuration was changed by this refactor.

## Three-module architecture

The Android project now includes `:app`, `:domain` and `:data`. Domain is Kotlin/JVM; data is an Android library for Room/API/session implementations. App owns presentation, authentication UI and the composition root. Dependencies are app -> domain/data and data -> domain, with no inward dependency on the application.

Unit verification is split into seven domain tests, five data tests and six app tests. All 18 pass. The debug and instrumentation APKs compile, and app/data lint both pass. The six existing Compose/Room instrumentation tests also pass after the module move on API 36. Skills are at `android/.agents/skills/`, with Android-scoped instructions in `android/AGENTS.md`. README links make the hidden guidance folder directly discoverable.

## Explicit domain UseCases verified 11 November 2026

Added ObserveCarsUseCase, ObserveCarHistoryUseCase, RefreshCarsUseCase, RefreshCarHistoryUseCase, AddFuelEntryUseCase and SyncPendingFuelEntriesUseCase. ViewModels now receive UseCases, and the repository is private to the application composition root. SyncScheduler is a domain port implemented by WorkManagerSyncScheduler. A custom WorkerFactory supplies the synchronization UseCase through the Worker's constructor; the Worker no longer retrieves dependencies from the Application.

Verification: 26 JVM tests pass (15 domain, five data, six app), and nine API 36 instrumentation tests pass (five Compose, one Room and three Worker tests). Debug/instrumentation builds, app/data lint and Kotlin style checks pass. Tests verify validation/save/schedule ordering, error and cancellation propagation, history composition and Worker completion/retry mapping. A broad Worker exception handler that swallowed cancellation was corrected and covered by a regression test. These checks remain fake/emulator-backed and do not assert live Firebase behavior.
