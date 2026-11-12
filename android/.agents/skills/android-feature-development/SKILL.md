---
name: android-feature-development
description: Plan or implement CarMileageTracker features that demonstrate core Android development principles with constructor injection, Clean Architecture, MVVM, and responsible AI-assisted development. Use for feature requests and technical design work.
---

# Android feature development workflow

Deliver a user-visible mileage app feature with clear dependency boundaries, explicit state flow, and documented design trade-offs. Follow the Android-root `AGENTS.md`, inspect the current project state, and keep implementation proportional to the request.

## Before changing code

- Identify the user outcome, relevant screen/state/data flow, and constraints in the current implementation.
- State assumptions about mileage data, car selection, offline behavior, and API access. Never fabricate live mileage or credentials; use clearly labeled sample data if the feature is a UI prototype.
- Choose the smallest useful architecture. Explain which behavior belongs in Compose, a ViewModel, domain logic, or a repository, and why.
- Check configured Gradle dependencies and SDK versions before relying on an API. Call out required dependencies instead of silently assuming them.

## During implementation

- Keep UI rendering and state transitions explicit. Prefer immutable UI models and one-way events.
- Separate external data models from screen presentation when a real API/data source is introduced.
- Handle loading, empty, error, and success states that are relevant to the feature.
- Keep secrets out of source and committed files. Use placeholders and document required local configuration without exposing values.
- Add or update focused tests when they meaningfully protect business rules, mappings, state transitions, or user-visible behavior.

## Explain the implementation and verification

Summarize:

1. What changed and which user problem it solves.
2. The data/state flow and the reason for each boundary.
3. The important trade-off or simpler alternative considered.
4. How the change was verified, naming actual commands/results only.
5. Where AI helped (for example, exploring options, drafting a mapper, or reviewing edge cases), what the engineer checked, and what remains uncertain.

Do not claim that AI independently verified correctness. The author owns the design and should be able to explain every generated or edited line.
