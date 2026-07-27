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
- [ ] Add stable target references to spells and abilities.
- [ ] Add conservative causal links for counter, bounce, destroy, exile, and damage outcomes.
- [ ] Keep provenance/confidence explicit whenever semantics are enriched or inferred.

## Phase 3 — deterministic token reduction

- [ ] Add a structured `TurnStateDiffer`.
- [ ] Export deterministic turn deltas alongside full snapshots.
- [ ] Add round-trip reconstruction tests before reducing snapshot frequency.

## Phase 4 — coaching-derived facts

- [ ] Add deterministic per-turn metrics derived from canonical state.
- [ ] Keep evaluative concepts such as tempo or board advantage outside the reconstruction layer.

## Validation plan

- [ ] Add golden fixtures for saga chapters, ETB triggers, counters, exile-on-counter, bounce, sacrifice, copies, and simultaneous triggers.
- [ ] Add export-to-simplified-state round-trip tests.
- [ ] Add assertions that every strategically relevant semantic field is represented in the AI export.
