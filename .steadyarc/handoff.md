# Steady Arc Handoff

## Active handoffs index

No active handoffs.

The pre–Steady Arc 1.0 Deck Planner record `SA-MTGA-DECK-PLANNER-005` remains preserved at `.steadyarc/handoff-history/SA-MTGA-DECK-PLANNER-005.md`. DP-05 was subsequently accepted by the human on 2026-08-06; that acceptance is recorded in the current Deck Planner roadmap rather than retroactively changing the returned legacy handoff.

## Current ownership

- **Owner:** Human repository owner.
- **Repository baseline inspected:** `58d4ba1dc2bbbbb560b0f7ac91dcf4dcfa9307b0` on `main`.
- **Transferred validation:** `.\mvnw.cmd test` passed 294 tests with zero failures/errors/skips on a clean tree.
- **Completed arc:** Deck Planner. DP-08 is human-accepted after real-card click review confirmed the card-image cancellation race fix and the full planner workflow.
- **Current arc:** Application Shell & UI Consolidation Preparation.
- **Completed roadmap item in current arc:** `AS-01 — Inventory current UI ownership and define the shell migration contract`.
- **Next roadmap item:** `AS-02 — Introduce the true application MainFrame and module host`.
- **Architecture artifact:** `docs/architecture/ui-consolidation-preparation.md`.
- **Temporary diagnostics:** Targeted `CardImageTrace` logging for Marketback Walker / Agent Maria Hill is removed in the AS-01 transition patch; the confirmed root cause was viewport cancellation comparing logical identities with `identity#face=N` pending keys.
- **Intended next arc after shell acceptance:** `SA-MTGA-DEF-005` MTG Arena process-memory collection extraction research.
- **Safe next action:** Apply and validate the AS-01 transition/cleanup patch. After a green return, explicitly delegate AS-02 to implement `app.ui.MainFrame` and the minimal module host.
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


### Deck Planner acceptance / Application Shell activation amendment — 2026-08-09

- Human explicitly accepted the Deck Planner after the image cancellation race was confirmed fixed in real-card preview use.
- Accepted repository baseline: clean `58d4ba1dc2bbbbb560b0f7ac91dcf4dcfa9307b0`, 294 tests passing.
- DP-08 and the Deck Planner arc are closed.
- Temporary targeted card-image diagnostics are removed after serving their purpose.
- The new primary arc is `Application Shell & UI Consolidation Preparation`.
- `AS-01` documents the current UI/window ownership and target shell/module boundary before production refactoring starts.
- The intended next arc after Application Shell acceptance is `SA-MTGA-DEF-005` process-memory collection extraction research.
- Ownership returns to the human for transition-patch application and validation before AS-02 implementation.
