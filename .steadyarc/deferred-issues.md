# Steady Arc Deferred Issues

## Closed items

### SA-MTGA-DEF-001 — Upstream feedback channel for Copilot sessions

- **Summary:** Establish an explicit, low-friction path in SteadyArcWorkflow for assistants that cannot write to multiple repositories in one session to emit "feedback from copilot" in a structured format.
- **Why deferred:** Stage 1 in this repository focuses on local structure initialization only.
- **Suggested upstream addition:** A dedicated template section under bootstrap feedback for "cross-repository tooling constraints observed in sandboxed agents."
- **Local evidence artifact:** `docs/steadyarc-copilot-feedback.md`.
- **Status:** Closed — `docs/steadyarc-copilot-feedback.md` has been populated with observed constraints, structured findings, and an evidence table across Stages 1–4. Ready for upstream transfer by the human repository owner.

### SA-MTGA-DEF-002 — Bootstrapping package integration details

- **Summary:** Introduce Steady Arc bootstrapping package artifacts (`RunWidget.ps1`, helper class/package, and any managed metadata) in a way that fits MTGArenaLogReader build and repository conventions.
- **Why deferred:** Planned for Stage 2 to keep Stage 1 documentation-only.
- **Prerequisite:** Confirm target paths and minimal `pom.xml` integration surface before adding tooling artifacts.
- **Status:** Closed — artifacts delivered in Stage 2 (`RunWidget.ps1`, `BootstrapInfo.java`, `maven-wrapper.properties`). `.gitignore` corrected in Stage 3. CI workflow added in Stage 4.

## Open items

### SA-MTGA-DEF-003 — Live collection-dependent Deck Planner behavior

- **Summary:** Defer product functions that require authoritative owned-card quantities until MTG Arena again publishes a complete collection record in `Player.log` or another explicitly approved authoritative source becomes available.
- **Observed evidence:** Three current production captures on 2026-08-06 included startup, Collection/Deck Builder navigation, arbitrary owned and unowned card selection, and a saved deck named `Collection deck`. The logs exposed the full deck upsert but no `PlayerInventory.GetPlayerCardsV3` response and no structurally equivalent complete numeric ownership map.
- **Truth constraint:** Deck membership, craftability indicators, Scryfall metadata, cosmetics, boosters, and generic `InventoryInfo` are not authoritative evidence of ownership. Missing card IDs must never be converted to zero without a complete snapshot.
- **Deferred tasks:** Collection-status filters and controls; ownership overlays and displayed counts; browser/consideration synchronization of ownership; collection quantities in `MTGA_DECK_BUILD_REQUEST_V1`; ownership-dependent integration and performance evidence; final live framing acceptance for `ArenaCollectionObserver`.
- **Retained implementation:** The strict parser, `-1 / 0 / positive` quantity contract, provenance-bearing snapshot, persistence repository, observer wiring, and focused tests remain in place so integration can resume without redesign when authoritative records return.
- **Resume trigger:** A sanitized current-client log record containing a complete authoritative owned-card map, with enough framing context to establish how it reaches the observer.
- **Status:** Open — blocked by upstream Arena logging behavior.
