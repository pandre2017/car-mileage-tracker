---
name: mvvm-clean-architecture-review
description: Review or shape CarMileageTracker code for MVVM, unidirectional data flow, and proportionate Clean Architecture. Use for architecture decisions, feature planning, or reviews involving Compose, ViewModels, repositories, domain logic, and data sources.
---

# MVVM and Clean Architecture review

Use `AGENTS.md` as the project baseline. The goal is to demonstrate core Android development principles with constructor injection and Clean Architecture. Explain the trade-off behind each layer and keep the design proportional to a small mileage app.

## Review the boundaries

- **UI:** Compose renders state and forwards user actions. It does not fetch data, own business rules, or mutate repository data directly.
- **Presentation:** a screen-level ViewModel coordinates screen work and exposes immutable, complete UI state. It does not hold a View or Activity reference.
- **Domain:** pure Kotlin mileage rules or concepts that have independent value. Keep domain contracts and rules in the pure Kotlin :domain module.
- **Data:** repositories coordinate a source of truth and data sources. API DTOs and storage entities stay at the boundary and are mapped into app/domain models.
- **Dependencies:** source dependencies point toward stable app/domain abstractions. Outer data and platform code implements the contracts needed inward.

## State and flow

- Follow unidirectional data flow: state flows from ViewModel to UI, and actions flow from UI to ViewModel.
- Use immutable UI state with explicit loading, success, and failure representation. Choose a sealed hierarchy for mutually exclusive screen states; use a data class when parts of the screen load independently.
- Give each piece of state one owner. Keep transient widget state in Compose when it does not represent screen/business state.
- Consider `SavedStateHandle` for small restorable screen inputs such as a selected car or query. A ViewModel alone does not survive process death.
- Handle refresh races, stale results, cancellation, and lifecycle-aware collection when relevant. Do not add event channels or MVI reducers unless the behavior needs them.

## Proportional design

- Start with the smallest design that keeps UI, state coordination, and data retrieval independently understandable and testable.
- Add a repository when it owns source selection, caching, refresh, or a useful data contract. Use the explicit domain UseCase layer requested for this project. ViewModels and Workers receive UseCases through constructor injection; repositories implement data access, while SyncScheduler is an outward domain port.
- Keep manual dependency injection while the object graph is small. Propose a DI framework only when the graph or lifecycle scopes justify it.
- Preserve the explicitly requested :app/:domain/:data module split. Add further modules, architecture frameworks, or MVI only when a concrete requirement makes them worthwhile. Explain the benefit and cost of the proposed change.
- Distinguish an architectural violation from a reasonable alternative. State the impact and smallest change that improves the design.

## Response format

For a review, summarize the current flow, then list findings by severity with file and symbol references, impact, and concrete recommendation. For a design proposal, show a compact dependency/data-flow sketch, identify the role of each layer, name the main trade-off, and note what the current project does not yet support. Avoid implying that a proposed library/API is configured until you verify Gradle files.

