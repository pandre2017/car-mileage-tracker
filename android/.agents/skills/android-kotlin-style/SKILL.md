---
name: android-kotlin-style
description: Apply and review Kotlin, Android, and Jetpack Compose conventions in this CarMileageTracker project. Use when writing, editing, or reviewing Kotlin source, Compose UI, coroutines, or Android lifecycle code.
---

# Android and Kotlin style for CarMileageTracker

Follow the Android-root `AGENTS.md` and this repository's actual Gradle configuration first. The project uses Kotlin's official style, three modules (:app, :domain, :data), Jetpack Compose, and Material 3. Do not assume dependencies or APIs that are not present in the build files.

## Kotlin

- Prefer clear names, small functions, immutable `val`s and data, and expression-oriented Kotlin where it improves readability.
- Use nullability and sealed types to represent real alternatives. Avoid `!!`, unexplained `lateinit`, force casts, and broad `Any`-typed APIs.
- Keep functions and classes focused. Prefer composition and explicit constructor dependencies over global state or hidden service lookup.
- Use Kotlin formatting and import conventions already established by the project. Do not introduce a formatter or style plugin without a concrete need.

## Android and Compose

- Keep `MainActivity` focused on Android entry-point setup. Put screen content in focused composables and screen state/logic in a ViewModel when that screen has business or data state.
- Make reusable UI components stateless when practical: pass values and callbacks in, and hoist state to the appropriate owner.
- Use `remember` for composition-scoped UI state and `rememberSaveable` when small UI state should survive recreation. Do not use either as a replacement for repository or screen business state.
- Collect ViewModel flows with lifecycle-aware Compose APIs when available in the configured dependencies; if not, identify the missing dependency instead of assuming the API compiles.
- Use Material 3 components and theme tokens. Provide meaningful content descriptions for informative icons and avoid redundant descriptions for decorative elements.
- Keep composables declarative and side effects controlled with the appropriate Compose effect API. Do not perform I/O or launch unmanaged work during composition.

## Coroutines and errors

- Tie screen work to `viewModelScope`; avoid `GlobalScope`, `runBlocking`, and blocking work on the main thread.
- Keep suspend APIs main-safe by moving blocking work to the layer that performs it. Inject a dispatcher only when it is needed for blocking work or test control.
- Rethrow `CancellationException` when catching broad exceptions. Map technical failures to a UI-understandable state at the presentation boundary.
- Keep mutable state private and expose read-only `StateFlow` or another well-justified observable state type.

## Review output

When reviewing code, lead with concrete findings and file/line references. Separate correctness and lifecycle issues from preferences. For each recommendation, explain the user-visible or maintenance impact and show a concise Kotlin example only when it clarifies the change. Do not claim a build or test passed unless it was actually run.

