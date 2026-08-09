# Steady Arc Handoff

## Active handoffs index

No active handoffs.

The pre–Steady Arc 1.0 Deck Planner record `SA-MTGA-DECK-PLANNER-005` remains preserved at `.steadyarc/handoff-history/SA-MTGA-DECK-PLANNER-005.md`. DP-05 was subsequently accepted by the human on 2026-08-06; that acceptance is recorded in the current Deck Planner roadmap rather than retroactively changing the returned legacy handoff.

## Current ownership

- **Owner:** Human repository owner.
- **Repository baseline inspected:** `0cc4b9e3f7d0c504c40513851f4fbd9885553368` on `main`.
- **Transferred validation:** `.\mvnw.cmd test` passed 279 tests with zero failures/errors/skips on a clean tree.
- **Current arc:** Deck Planner.
- **Completed roadmap item:** `DP-07 — Authoritative AI deck-building protocol`; explicitly accepted by the human after successful real use of the generated export in a deck-design conversation.
- **Active roadmap item:** `DP-08 — Integration, performance, and release evidence`.
- **Human acceptance surface:** Continue using `DeckPlannerWorkspacePreview` for click testing during DP-08, focusing on lifecycle, responsiveness, loading/offline/error behavior, and end-to-end flow rather than re-testing deterministic exporter mechanics already covered automatically.
- **Deferred future mission:** `SA-MTGA-DEF-005` records the human-supplied MTGA process-memory collection-import research. It remains outside DP-08 unless explicitly activated.
- **Safe next action:** Apply and validate the bounded DP-08 real-card UX regression correction: persistent-image fallback without an explicit favorite, immediate panel-boundary relayout, Workspace reset-control sizing, and denser category-aware/multi-card Candidate drag/drop with autoscroll. Re-run the preview click review before opening DP-08.3 release/evaluation evidence.
- **Managed-tool update:** Still blocked on delivery of the verified `steady-arc-knowledge-<version>.zip` release archive. Do not reconstruct or replace managed artifacts from knowledge prose.

### DP-06 interaction-polish amendment — 2026-08-08

- Human click review after the 264-test baseline requested one further Candidate UX pass before filtering work.
- Scope is favorite-art presentation consistency, transient art chooser behavior, compact/collapsible Candidate categories, category-local drag/drop ordering/new-category drops, overlay workspace boundary controls, rarity-aware Candidate selection outline, and Catalog scroll-to-selected-Candidate behavior.
- Filtering/tag taxonomy remains outside this amendment.
- Ownership remains with the patch-exchange assistant for this bounded patch; return to the human for repository-side validation and click review.

### DP-06 favorite/layout polish amendment — 2026-08-08

- Human click review after the 271-test baseline found that persisted favorite printing changes did not invalidate the Catalog's logical-identity image cache, Candidate spacing remained too loose, and floating boundary controls could lag panel layout changes.
- Scope is immediate favorite-art refresh for Catalog presentation, approximately halved Candidate spacing, and transparent circular boundary controls with deterministic post-layout positioning.
- Filtering/tag taxonomy remains outside this amendment.
- Ownership remains with the patch-exchange assistant for this bounded patch; return to the human for repository-side validation and click review.


### DP-06 acceptance / DP-07 activation amendment — 2026-08-08

- Human explicitly accepted DP-06 after real-card click review and the clean 272-test `6a67dcfef2a3298ee8b1794dcd7a166576d6db75` baseline.
- DP-06 is closed; DP-07 becomes the active Deck Planner roadmap item.
- The prior DP-07 fixed closing question is withdrawn.
- DP-07 begins with persisted Candidate Set planning notes and a richer strategic-analysis instruction contract.
- `DeckPlannerWorkspacePreview` remains the human click-test harness for DP-07.
- Ownership remains with the human until the next bounded DP-07 implementation delegation.


### DP-07.1 Candidate Set note implementation amendment — 2026-08-08

- Human delegated the first DP-07 implementation slice.
- Candidate Set persistence now carries an optional free-form note with a backward-compatible schema migration.
- Candidate UI exposes `Edit note`; saving the note saves the named Candidate Set, and loading the set restores the note.
- `DeckPlannerWorkspacePreview` is retargeted from DP-06 acceptance wording to DP-07 note-workflow human review while remaining the same repository-owned click-test harness.
- No AI export schema or instruction payload is implemented in this slice.
- Return owner after patch delivery: Human repository owner for application, automated validation, and click review.


### DP-07.2/DP-07.3 exporter implementation amendment — 2026-08-08

- Human confirmed DP-07.1 looked good after the clean 274-test `0680b6046113db4011defb4cffe80590099687e9` baseline and delegated continuation.
- Scope is the first deterministic `MTGA_DECK_BUILD_REQUEST_V1` schema, authoritative Candidate Set/card encoding, the stable deck-analysis instruction brief, and a copyable modeless `AI export` review window in the existing preview harness.
- Export resolution is local/cache-only through `CardNameRepository.resolveIdentity`; generating the request does not perform network enrichment or block on Scryfall.
- Unresolved Candidate identities remain explicit `status=UNRESOLVED`; the instruction contract forbids inventing missing card facts.
- DP-07 remains active after this patch. Human click review of the generated request is required before acceptance, and additional golden coverage for specialized card layouts may still be added if review exposes protocol gaps.
- Return owner after patch delivery: Human repository owner for application, automated validation, and click review.


### DP-07 acceptance / DP-08 activation amendment — 2026-08-08

- Human accepted DP-07 after successfully using the generated `MTGA_DECK_BUILD_REQUEST_V1` prompt in a real deck-design conversation.
- The accepted baseline is clean `14c490a691d43bda6b6b2f005c4f996e5d738a20` with 278 tests passing.
- DP-07 is closed and DP-08 becomes active.
- `DeckPlannerWorkspacePreview` remains the human click-test surface for DP-08 integration and release-evidence work.
- The human supplied a possible future MTGA collection-import route based on process-memory scanning; it is recorded as deferred issue `SA-MTGA-DEF-005` and does not expand DP-08.
- Ownership remains with the human until the next bounded DP-08 implementation delegation.


### DP-08.1 integration/lifecycle amendment — 2026-08-09

- Scope is evidence, not new planner functionality: exercise the production preview catalog path through startup, filter refinement, Candidate import, deterministic AI export, and shutdown.
- `DeckPlannerWorkspacePreview` human review is retargeted from DP-07 prompt quality to DP-08 startup/lifecycle, offline/degraded states, end-to-end workflow continuity, and clean shutdown/relaunch.
- Expected test discovery change: +1 integration fixture (278 → 279).
- Ownership returns to the human for repository-side validation and click review after this patch.


### DP-08.2 performance-evidence amendment — 2026-08-09

- DP-08.1 is complete on clean commit `438e2e4a5640721709697894ba3b5b26c350a132` with 279 tests passing.
- DP-08.2 adds observability only where evidence was previously unavailable: persistent image-cache hit/network counters and browser image-window/cache cardinality.
- Evidence fixtures intentionally avoid hard wall-clock acceptance thresholds. They record elapsed values while asserting qualitative contracts: cached startup is usable without refresh, EDT remains serviceable while background work waits, viewport materialization does not request the full catalog, revisiting a viewport reuses images, scrolling grows cache only with distinct requested cards, and persistent image cache transitions disk→memory without network.
- No optimization or product behavior change is authorized by this slice unless the measurements expose a concrete defect.


### DP-08 real-card review correction amendment — 2026-08-09

- DP-08.2 performance evidence is integrated on clean `0cc4b9e3f7d0c504c40513851f4fbd9885553368` with 283 tests passing.
- Human click review found that cards whose cached metadata lacked image URLs could still render as missing art even when their image file was already persisted. A no-favorite/default-printing path must reuse the persistent image by stable Scryfall/Arena identity before declaring art unavailable.
- Filter collapse must synchronously relayout/reposition the floating boundary controls; Reset filters should match the neighboring Workspace control height.
- Candidate organization needs a denser content-width flow layout, category-aware card drops, whole-category whitespace drag handles with boundary-line feedback, reliable multi-card drag preservation, and hover autoscroll during drag.
- DP-08.3 remains blocked until this correction is green and passes another real-card click review.
