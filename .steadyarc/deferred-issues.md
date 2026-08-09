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
- **Deferred tasks:** Collection-status filters and controls; ownership overlays and displayed counts; browser/candidate synchronization of ownership; collection quantities in `MTGA_DECK_BUILD_REQUEST_V1`; ownership-dependent integration and performance evidence; final live framing acceptance for `ArenaCollectionObserver`.
- **Retained implementation:** The strict parser, `-1 / 0 / positive` quantity contract, provenance-bearing snapshot, persistence repository, observer wiring, and focused tests remain in place so integration can resume without redesign when authoritative records return.
- **Resume trigger:** A sanitized current-client log record containing a complete authoritative owned-card map, with enough framing context to establish how it reaches the observer.
- **Status:** Open — blocked by upstream Arena logging behavior.

### SA-MTGA-DEF-004 — Card subtype / tribe tag taxonomy and long-list interaction

- **Summary:** Add filter/tag resolution for creature tribes and other card subtypes without flooding the compact Deck Planner tag surface.
- **Observed evidence:** DP-06 real-Standard click review on 2026-08-08 exposed that subtype/tribal terms are not currently represented by the semantic tag resolver and will produce a substantially longer vocabulary than the existing curated tag categories.
- **Why deferred:** The current DP-06 pass is intentionally limited to candidate caching/import, category presentation, scalable rendering, and drag feedback. Subtype tags need their own taxonomy, count/faceting rules, search/navigation interaction, and human review rather than being appended opportunistically.
- **Resume trigger:** A bounded Deck Planner filter/tag item explicitly designs long-list subtype discovery and selection against the real Standard catalog.
- **Status:** Open — product/filter design work required.

### SA-MTGA-DEF-005 — Full collection import via MTG Arena process-memory scanning

- **Summary:** Research an optional Windows collection synchronization path that reads the complete MTG Arena collection from the running `MTGA.exe` process when current `Player.log` output does not expose authoritative collection snapshots.
- **Source:** Human-supplied research note `Deferred_Feature_MTGA_Memory_Collection_Import.md`, referencing the MIT-licensed `NthPhantom10/MTGA-collection-exporter` project as a reference implementation rather than a runtime dependency.
- **Proposed direction:** Hide the capability behind a provider interface such as `MemoryCollectionProvider`; for the first Windows implementation, prefer JNA over JNI and use `OpenProcess`, `VirtualQueryEx`, `ReadProcessMemory`, and `CloseHandle`.
- **Candidate extraction strategy:** Locate candidate `(grpId, quantity)` sequences in readable Arena memory, validate `grpId` values against the application's Arena card database, and score candidate blocks using known-card membership, plausible quantities, anchor matches, and penalties for unknown IDs or implausible quantities rather than selecting only the largest block.
- **Product boundary:** This complements log parsing rather than replacing it. Logs remain authoritative for matches, decks, and gameplay events; the memory scanner would provide a manual full-collection synchronization action.
- **Risks / research questions:** Arena updates may change memory layout; elevated permissions may sometimes be required; Arena UI state may affect discoverability; initial implementation is Windows-specific; robustness and anti-cheat/process-access implications must be investigated before productization.
- **Relationship to SA-MTGA-DEF-003:** This is a possible future route for satisfying the missing authoritative collection source, not evidence that the current ownership gap is resolved.
- **Resume trigger:** Human explicitly opens a bounded research/prototype arc for collection-memory import and supplies or approves investigation of the referenced implementation.
- **Status:** Resumed — promoted into the `Memory-Scan Collection Extraction` arc after explicit human approval on 2026-08-09. The new arc supersedes the earlier loose provider proposal with a stricter two-operation application port, isolated H2 table, and harness-first evidence plan.

