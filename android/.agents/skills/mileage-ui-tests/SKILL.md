---
name: mileage-ui-tests
description: Add, review and run Compose mileage screen and activity tests using isolated fakes and lifecycle-aware state.
---
# Mileage UI testing

Read AGENTS.md, UI state and androidTest fixtures. Use configured Compose test APIs and JUnit 4.

- Use createComposeRule for stateless screen rendering/callbacks. Use activity rules only when testing real navigation or recreation; inject a fake graph before launch rather than contacting production Firebase. Hilt is not configured here.
- Cover signed-out, loading, empty, history, validation, save and retry/refresh behavior with fixed domain fixtures and controlled suspend responses.
- Use resource strings and semantics. Interact with lazy list semantics rather than coordinates. Let Compose synchronize; use bounded waitUntil for external work and never Thread.sleep.
- Separate screen-only tests from activity recreation and process death. Passing fixture-backed tests does not verify Google/Firebase availability.
- Run ./gradlew :app:connectedDebugAndroidTest. If no device exists, compile the instrumentation APK and report execution outstanding. Stop only an emulator started for this task.
