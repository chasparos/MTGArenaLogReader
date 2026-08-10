# Steady Arc Roadmap

## Current arc

- **Arc identifier:** Deck Planner Phase 2 — Analysis and Filtering
- **Arc type:** product integration / filtering / graph-analysis design
- **Area in scope:** Deck Planner ownership presentation and filters, repair and completion of existing filter controls (especially color), followed by collaborative definition of a mechanic/interaction/soft-card-type relation graph and its searchable index.
- **Completion criteria:** To be defined during DPP2-01 after the ownership/filter foundation is reviewed.

### Ordered items

#### DPP2-00 — Ownership data and filter foundation

**State:** planned

Wire the completed `CollectionOwnership` batch API into Deck Planner without exposing scanner implementation details. Display copies owned beside cards, add the wizard-style owned-copy control to filtering, repair incomplete or misbehaving current filters, and give the color filter a focused behavior/design pass. Begin with fresh click-review and user-evidence collection before making concrete UI tuning plans.

#### DPP2-01 — Analysis, relation graph, and indexing definition

**State:** planned — definition requires human discussion

Collaboratively define the intended card-analysis model before implementation: mechanics and interactions, soft card types such as removal and ramp, relation/synergy edges, indexing, and the search/filter experiences the graph should enable. Do not prematurely freeze the schema or implementation plan; this item begins with explanation, examples, and iterative requirements capture.

## Completed arc — Memory-Scan Collection Extraction

- **Arc identifier:** Memory-Scan Collection Extraction
- **Arc type:** Windows-specific research / isolated prototype / persistence
- **Status:** Complete. MSC-01 through MSC-06 are done and human-approved at 338 tests passing.
- **Outcome:** An isolated `app.collection.memory` module exposes ownership only through `getCopiesOwned(ids)` and `attemptRealCollectionUpdate()` / the `CollectionUpdate` session protocol. Publication is atomic, fail-closed, and gated by known-domain consensus plus generation-aware quantity validation. No other application package depends on JNA, process handles, memory layout, or scanner persistence types.
- **Full research/implementation narrative:** `docs/architecture/memory-scan-collection-extraction.md`.
- **Maintenance/drift guidance:** `docs/guides/memory-collection-sync-maintenance.md`.


## Completed arc — Application Shell & UI Consolidation Preparation

- **Arc identifier:** Application Shell & UI Consolidation Preparation
- **Arc type:** integration / refactor / UX architecture
- **Status:** Complete. AS-01 through AS-06 are done and human-approved.
- **Outcome:** Production now has one true top-level `app.ui.MainFrame` that owns module navigation and a central content host. Replay and Deck Planner are genuine embedded modules; Deck Tracker and Draft Assistant are singleton companion windows behind explicit shell adapters; Coaching remains Replay-contextual and Settings remains shell-owned. Developer-only replay fixture/paste behavior lives only in `devtools.ReplayUiHarness`.
- **Full inventory, migration contract, and consolidation findings:** `docs/architecture/ui-consolidation-preparation.md`.

### Current planning decisions

- The Deck Planner arc is complete and human-accepted at clean commit `58d4ba1dc2bbbbb560b0f7ac91dcf4dcfa9307b0` with 294 tests passing.
- Prefer an application shell plus module adapters over immediately converting every module into one shared visual framework.
- Preserve `Application` as the service/composition owner; UI classes consume configured services.
- The Memory-Scan Collection Extraction arc (above) was the arc that followed Application Shell acceptance; its outcome and reference doc are recorded there.
- Full visual/UI consolidation across modules is a later mission informed by `docs/architecture/ui-consolidation-preparation.md`, not a hidden requirement of this arc.

## Concurrent arcs

None.
