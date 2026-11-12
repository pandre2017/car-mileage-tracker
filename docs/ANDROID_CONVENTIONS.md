# Android conventions and verification

## Guidance copied from WeatherApp

`android/AGENTS.md` and six workflows in `android/.agents/skills/` carry over WeatherApp's Kotlin, MVVM/Clean Architecture, feature development, bug analysis, unit testing, and Compose UI testing practices. Project-specific weather examples, Hilt requirements, and module paths were adapted to the mileage app. Skills are guidance for future AI-assisted work; passing checks remain the engineer's responsibility.

## Dependency boundaries

```text
Compose route -> stateless MileageContent + immutable MileageUiState
                       |
                MileageViewModel
                       |
             domain UseCases
                       |
        domain MileageRepository contract and models
                       ^
             DefaultMileageRepository
               /                  \
       Room entities/DAO       Retrofit DTOs/API
```

`domain/` contains pure Kotlin models, input/amount rules, totals, repository contracts and failure categories. `data/` maps Firestore API DTOs and Room entities to those models and handles authentication/network error translation. The ViewModel observes domain flows, saves selected car identity through SavedStateHandle, and exposes a read-only StateFlow. It owns no UI callbacks, DAO, Activity, Firebase or Retrofit types. Selection changes cancel obsolete refresh jobs and clear previous-car history while new data loads.

Google account UI is isolated in an Activity-scoped `platform/GoogleAccountSignIn` adapter. `MileageApplication` is a small manual composition root. The database tables, schema version, API contract, and persisted pending entries are preserved.

Three Gradle modules implement the requested Clean Architecture demonstration. `:domain` uses Kotlin/JVM only and owns contracts, models and rules. `:data` is an Android library because Room requires Android; it depends on domain and never app. `:app` depends on both, owns presentation and the manual composition root. The compiler now prevents data from importing app classes and domain from importing either outer module. The source architecture test adds presentation/package checks with nonempty-scope guards.

Domain tests are in `android/domain/src/test/kotlin`, data tests in `android/data/src/test/java`, and ViewModel/architecture tests in `android/app/src/test/java`. Device tests remain in app because they exercise the installed application and Room integration.

## Kotlin style

Use official Kotlin style, four-space indentation, explicit imports, constructor injection, immutable public state and focused functions. UI labels live in Android string resources. The `.editorconfig` settings match the IDE conventions.

From `android/`, with JDK 21 selected:

```sh
bash scripts/kotlin-style.sh --write
bash scripts/kotlin-style.sh --check
```

The script downloads ktfmt 0.64 if necessary, verifies its pinned SHA-256, and formats Kotlin/Gradle Kotlin sources without adding a Gradle plugin. The check runs in Android CI.

## Unit tests

From `android/`:

```sh
./gradlew :domain:test :data:testDebugUnitTest :app:testDebugUnitTest
```

The suite covers decimal parsing and rounding, totals excluding rejected records, invalid dates, DTO/entity mapping, retryable upload preservation, local duplicate dates, domain error translation, Retrofit request shape, architecture direction, loading/error/retry/save state transitions, stale selection cancellation and ViewModel lifecycle cancellation. Coroutine tests use runTest, a controlled Main dispatcher and constructor-injected fakes. The HTTP test uses MockWebServer and closes it in teardown.

## Device tests

Use an API 36 emulator, then run from `android/`:

```sh
./gradlew :app:connectedDebugAndroidTest
```

Five Compose screen tests exercise sign-in callbacks, car selection, fueling input/derived price/validation/save, refresh after failure, and loading/empty-state behavior. One Room instrumentation test checks full-history replacement and pending/failed record preservation. Three Worker device tests check constructor injection, result mapping and cancellation through a fake domain repository. These are screen and database tests; they do not launch the production MainActivity, prove activity recreation/process-death restoration, or verify live Google/Firebase services.

The test runner substitutes a plain Application so screen tests never initialize the production Firebase/WorkManager dependency graph. Fixtures are explicit and interactions use semantics rather than coordinates or sleeps. A dedicated GitHub Actions job runs them on API 36; CI execution is verified only after the workflow actually runs remotely.

Compose's transitive Espresso 3.5.0 dependency failed during input-device initialization on newer emulator images, before screen assertions. The app explicitly declares Espresso 3.7.0, which replaces the reflective InputManager.getInstance call with getSystemService. CI continues to use API 36; compatibility with other images must be verified by running the suite on them.

## Application UseCases and outward ports

Every application action has an explicit domain UseCase in `android/domain/src/main/kotlin/com/pandre/mileage/domain/usecase/`:

| UseCase | Responsibility |
| --- | --- |
| ObserveCarsUseCase | Expose cached accessible cars through the domain contract |
| ObserveCarHistoryUseCase | Combine domain entries and refresh time into CarHistory |
| RefreshCarsUseCase | Refresh cars and schedule pending-upload recovery on success |
| RefreshCarHistoryUseCase | Validate car identity, refresh its history, and schedule upload recovery |
| AddFuelEntryUseCase | Validate the entry, save locally, then schedule upload |
| SyncPendingFuelEntriesUseCase | Run repository synchronization and return COMPLETE or RETRY |

The ViewModel receives UseCases rather than a repository or a scheduling lambda. The repository remains private to the composition root and UseCases. The WorkManager adapter implements the domain SyncScheduler port; a custom WorkerFactory injects SyncPendingFuelEntriesUseCase into SyncWorker. The Worker maps domain completion/retry/failure outcomes to WorkManager Result without looking up the Application.

This explicit layer is intentional for the project's Clean Architecture demonstration, including the simple observation wrapper. Business orchestration is independently testable while Room transactions, HTTP mapping, cache replacement and retryable transport handling remain data responsibilities. Domain model validation is also repeated defensively by the data repository at its write boundary.

UseCase tests verify validation before writes, saving before scheduling, no scheduling after failure/cancellation, history composition and explicit sync outcomes. Device Worker tests exercise the factory injection and WorkManager result mapping against a fake repository. These tests do not contact live Firebase.
