---
name: mileage-bug-analysis
description: Investigate a CarMileageTracker defect, establish an evidence-backed root cause, and provide both detailed technical and brief non-technical explanations. Include an optional fix proposal or patch when useful or requested.
---

# CarMileageTracker bug analysis

Use this workflow for reported bugs, crashes, unexpected UI state, incorrect mileage data, and regressions. Treat the report as a symptom, not proof of a cause. Ground conclusions in the source, logs, reproduction steps, or test evidence available in the task.

## Investigation

1. Restate the observed behavior, expected behavior, affected screen or flow, and reproduction conditions. Mark missing information as unknown.
2. Trace the path from user action or lifecycle event through Compose, ViewModel, domain logic, repository, and data source as applicable. This starter app may not have all of these layers yet.
3. Identify the earliest incorrect assumption, state transition, or data transformation that explains the symptom. Check plausible alternatives before choosing a cause.
4. Separate confirmed facts from hypotheses. Cite file paths, symbols, line numbers when available, and logs or reproduction evidence. State confidence and what would confirm an unresolved hypothesis.
5. Assess scope and risk: affected states/devices, data impact, lifecycle or concurrency implications, and regression surface.

## Required report

### Technical root cause

Give a detailed explanation for an Android engineer: reproduction, relevant call/state flow, causal mechanism, evidence, alternatives ruled out, scope, and confidence. Explain the distinction between the visible symptom and the underlying defect.

### Brief explanation

In two to four plain-language sentences, explain what went wrong, what a user would notice, and why. Avoid framework terms unless briefly defined.

### Fix proposal (optional)

Include this section when a concrete correction is identified or the user asks for one. Explain the smallest suitable change, trade-offs, and regression checks. If asked to implement the fix, edit the source and report the changed files. Otherwise provide a patch or code proposal without modifying source files. Do not invent successful test results.

## Patch rules

- Keep a patch focused on the confirmed cause; do not bundle unrelated cleanup.
- Preserve structured concurrency and cancellation behavior.
- Include a regression test suggestion for the failing scenario when appropriate.
- If producing a `.patch`, ensure it is based on the current working tree and clearly identify its path. Do not claim it applies cleanly unless that was checked.
- If the evidence is insufficient, return a ranked set of hypotheses and the next observation needed rather than presenting a guess as root cause.

