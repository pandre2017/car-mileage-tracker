---
name: mileage-unit-tests
description: Add, review and run JVM tests for mileage rules, repository mapping, synchronization and ViewModel state.
---
# Mileage unit testing

Follow AGENTS.md and inspect configured dependencies and existing tests. Domain tests live in domain/src/test/kotlin, data tests in data/src/test/java, and presentation/architecture tests in app/src/test/java.

- Use JUnit 4, runTest and constructor-injected fakes. ViewModel tests replace Dispatchers.Main and reset it in teardown. Clear ViewModelStore to check lifecycle cancellation.
- Test decimal parsing/rounding, date validation, pending entry preservation, DTO mapping, retry decisions and loading/error/success transitions. Verify cancellation propagates and selection changes cannot display stale history.
- Use MockWebServer for HTTP contracts with bounded request waits; never read local Firebase credentials or call live services.
- Prefer observable outcomes over private helper details. Use explicit km/liter/cents fixtures and deterministic coroutine scheduling; never sleep.
- Run ./gradlew :domain:test :data:testDebugUnitTest :app:testDebugUnitTest from the Android root. Report actual tasks/results and remaining gaps.
