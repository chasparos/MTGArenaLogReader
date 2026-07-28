# AI coaching reconstruction roadmap

This document tracks the incremental work to improve the compact AI coaching reconstruction while keeping Arena observations, semantic reconstruction, rendering, and coaching evaluation separate.

## Phase 1 — semantic foundation and exporter safety

- [x] Remove persistent user-defined ability names and the replay UI used to create them.
- [x] Preserve the complete untruncated inferred Oracle ability paragraph in semantic data.
- [x] Add ability chapter, effect text, and confidence to `AbilityReference`.
- [x] Include structured ability semantics in the compact AI export.
- [x] Preserve saga chapter labels without replacing their inferred effect.
- [x] Generate the current compact schema directly instead of rendering an older schema and rewriting it.
- [x] Replace global player/card string substitution with field-aware aliases.
- [x] Define and test quoting/escaping for every free-text field.

## Phase 2 — causal and outcome fidelity

- [x] Add structured zone-transition observations and reasons.
  - [x] Repair `ZoneTransferProjectorTest` for the new `transitionEvent(...)` context contract.
  - [x] Update `ObjectLifecycleEventsTest` to assert the structured transition fields instead of comparing the record to legacy text.
  - [x] Import `ZoneTransitionReason` in the structured lifecycle assertion test.
- [x] Add stable target references to spells and abilities.
- [x] Add conservative causal links for counter, bounce, destroy, exile, and damage outcomes.
  - [x] Remove obsolete per-message `GameSession` projection INFO logging from test output.
  - [x] Repair causal-link reason mappings to use the actual `ZoneTransitionReason` enum values.
  - [x] Keep causal-link exporter tests inside `MatchAiExporterTest` without preview-language constructs.
- [x] Keep provenance/confidence explicit whenever semantics are enriched or inferred.
  - [x] Export provenance and confidence for targets, zone-transition reasons, and conservative causal links.

## Phase 3 — deterministic token reduction

- [x] Add a structured `TurnStateDiffer`.
- [x] Export deterministic turn deltas alongside full snapshots.
  - [x] Emit conservative life, hand-size, known-zone, battlefield, and counter changes between reliable snapshots.
  - [x] Repair the turn-delta exporter newline literal without enabling preview features.
- [x] Add round-trip reconstruction tests before reducing snapshot frequency.
  - [x] Reconstruct supported snapshot fields from a deep-copied baseline and reject contradictory deltas.

## Phase 4 — coaching-derived facts

- [x] Add deterministic per-turn metrics derived from canonical state.
  - [x] Export descriptive life, hand, battlefield, known-zone, and counter counts from structured turn deltas.
- [x] Keep evaluative concepts such as tempo or board advantage outside the reconstruction layer.
  - [x] Place coaching-ready metrics in the coaching analysis layer and keep them free of evaluative labels.

## Validation plan

- [ ] Add golden fixtures for saga chapters, ETB triggers, counters, exile-on-counter, bounce, sacrifice, copies, and simultaneous triggers.
  - [x] Add the canonical compressed mega-game raw-log fixture and validate broad replay, export, target, ability, combat, damage, and zone-transition coverage.
  - [x] Emit a deterministic canonical validation report with explicit semantic-category counts and conservative coverage floors.
    - [x] Count both player and planeswalker damage representations in the canonical damage total.
  - [ ] Add focused fixtures for semantic categories not conclusively isolated by the mega-game.
    - [x] Add a raw GRE Saga fixture that verifies chapter identification, self-target suppression, and chapter export.
    - [x] Add a focused raw GRE ETB-trigger fixture that verifies battlefield-entry ordering, triggered classification, and AI export.
    - [x] Repair the Saga and ETB validation patches so their Java tests and raw GRE fixture resources are actually included and discovered by Maven.
    - [x] Repair the focused Saga fixture GRE message discriminator so the production router projects the fixture.
    - [ ] Add focused counter, exile-on-counter, bounce, sacrifice, copy, and simultaneous-trigger fixtures.
- [x] Add export-to-simplified-state round-trip tests.
  - [x] Validate deterministic export and supported snapshot reconstruction across the canonical mega-game.
- [ ] Add assertions that every strategically relevant semantic field is represented in the AI export.
  - [x] Assert canonical export presence for zone transitions, targets, turn deltas, turn metrics, and game result records.
