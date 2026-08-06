# Steady Arc Handoff

## State

- **Status:** Active
- **Handoff ID:** SA-MTGA-DECK-PLANNER-005
- **From:** Human repository owner
- **To:** Codex
- **Created:** 2026-08-06
- **Return owner:** Human repository owner
- **Return condition:** DP-05 delivers the filter interaction model, click-first controls, explicit catalog states, focused tests, and passing validation.

## Engineering context

- **Current arc:** Deck Planner
- **Active roadmap item:** DP-05 active
- **Authoritative baseline:** DP-01 complete; DP-02 parser/model/persistence/observer implemented with 171 passing tests at source commit `a3e9d3b2646087b500a0d422d16f707a9c671be4`.
- **Material upstream limitation:** Current production Arena logs do not publish an authoritative complete owned-card map even after Collection and Deck Builder interactions. Ownership-dependent product behavior is deferred in `SA-MTGA-DEF-003`.
- **Relevant durable constraints:** Scryfall catalog metadata may drive filtering and semantic tags. It cannot establish collection ownership. DP-03 must operate correctly with collection quantity unknown.

## Delegation

- **Requested action:** Proceed to DP-05 after human approval of DP-04.
- **Completion criteria:** Color/color-identity semantics, base types, mana ranges, semantic tags, structured filtering, selected-tag AND behavior, and pre-tag-layer cloud counts are deterministic and tested.
- **Current authority:** Implement and validate DP-05 only. DP-06 and later items remain inactive except for roadmap/deferred dependency annotations.
- **Constraints:** Do not add ownership inference or block catalog filtering on collection data. Keep the filter model independent of future Swing widgets.
- **Files or areas in scope:** Deck Planner filter state, controls, interaction quality, explicit catalog states, focused tests, and Steady Arc continuity memory.
- **Files or areas explicitly out of scope:** Consideration persistence, AI export, production navigation/release integration, and any ownership inference from non-authoritative data.

## Activity amendments

- **Date:** 2026-08-06
- **Changed by:** Human repository owner
- **Transition or material change:** Accepted the absence of current authoritative collection records as an upstream blocker; deferred ownership-dependent product tasks and activated DP-03.
- **Reason:** Repeated live experiments published deck definitions but no complete ownership payload; further probing is not justified before continuing the roadmap.
- **Authority after change:** Codex for bounded DP-03 implementation.
- **Return condition after change:** DP-03 acceptance evidence is complete or a material catalog-metadata ambiguity is returned.

## Return report

- **Returned:** 2026-08-06
- **Work completed:** DP-03 immutable catalog filtering, color and color-identity semantics, multi-face base-type extraction, layout-aware/fractional mana-value ranges, deterministic categorized semantic tags, selected-tag AND behavior, and structured-filter-derived tag-cloud counts.
- **Verification:** The first DP-03 patch passed local Maven validation with 174 tests and no failures, errors, or skips at source commit `41a86e54c6c1d77b6003096f7b79ef3d9134b8e8`. The return patch adds focused acceptance tests for split/adventure/modal/land mana values, invalid ranges, and same-category tag AND behavior; the human patch sequence must produce the final validation artifacts.
- **Repository changes:** `app.deckplanner.filter` immutable models/index/tag rules and focused tests; roadmap, engineering notes, deferred issue, and handoff continuity updates.
- **Durable notes added or changed:** Explicit within-group filter semantics and Scryfall top-level `cmc` policy are recorded in `.steadyarc/engineering-notes.md`.
- **Deferred issues added or changed:** `SA-MTGA-DEF-003` retains all ownership-dependent planner behavior blocked by current Arena logging.
- **Unresolved issues:** No DP-03 design blocker remains. DP-04 requires explicit activation and rendered/human visual evidence beyond structural tests.
- **Recommended next action:** Apply and validate this return patch, review DP-03, then explicitly activate DP-04 for the responsive card browser and asynchronous image scheduling.
- **Ownership after return:** Human repository owner.


## Activity amendment â DP-04 interactive panel slice

- **Date:** 2026-08-06
- **Changed by:** Codex
- **Transition or material change:** Added the first concrete Swing card-browser surface over the validated layout and viewport models.
- **Scope:** Stable placeholders, responsive card painting, mouse/keyboard selection and focus, viewport-driven asynchronous image requests, EDT completion, and affected-region repaint.
- **Still out of scope:** Production frame wiring, concrete `CardImageCache` adapter, cancellation/deprioritization policy beyond generation invalidation, ownership overlays, filter widgets, and rendered human visual evidence.
- **Authority after change:** Codex remains active on bounded DP-04 slices.


## Activity amendment — DP-04 viewport lifecycle slice

- **Date:** 2026-08-06
- **Changed by:** Codex
- **Transition or material change:** Tightened the interactive browser's viewport request lifecycle and logical interaction state.
- **Scope:** Cancel pending image futures when cards leave the request window, ignore late off-window completions, and preserve selection/focus by stable card identity when result ordering changes.
- **Still out of scope:** Production `CardImageCache` adapter, frame wiring, rendered fixture/human visual evidence, filter widgets, and ownership overlays.
- **Authority after change:** Codex remains active on bounded DP-04 slices.

## Activity amendment — DP-04 cache adapter and fixture slice

- **Date:** 2026-08-06
- **Changed by:** Codex
- **Transition or material change:** Added a stable-identity adapter to the shared `CardImageCache` boundary and deterministic narrow/normal/wide placeholder fixture generation.
- **Scope:** Resolve browser identities to authoritative enriched `CardInfo`, delegate image loading asynchronously, and produce PNG fixtures for human responsive-layout review.
- **Still out of scope:** Application frame/navigation wiring, human visual approval, filter widgets, and ownership overlays.
- **Authority after change:** Codex remains active on bounded DP-04 slices.


## 2026-08-06 — DP-04 scroll coordinator continuation

- Added `CardBrowserPanel.ScrollAnchor` capture/resolution using stable identity plus viewport offset.
- Added `CardBrowserScrollPane` to forward viewport changes, preserve anchor across responsive resize, and replace filtered card results without jumping when the anchor survives.
- Added focused EDT tests for resize and filtered-result anchor retention.
- DP-04 remains active; next review should decide whether this completes the browser foundation after human fixture review or whether a reusable card-view component is still required before DP-05 activation.


## 2026-08-06 — DP-04 reusable CardView continuation

- Added a reusable lightweight `CardView` Swing component for stable placeholder/image presentation.
- `CardBrowserPanel` now renders card component views through `CellRendererPane` while retaining responsive layout, hit testing, selection/focus state, and targeted repaint ownership.
- Hover, selection, and focus remain transient overlays and never mutate shared cached images.
- Added focused rendering coverage for placeholder stability and cached-image immutability.
- DP-04 remains active pending human review of rendered fixtures and a decision on completion/return.


## 2026-08-06 — DP-04 human-review harness continuation

- Added a standalone preview surface and PowerShell launcher for the required human visual/interaction pass.
- Added a durable review checklist for narrow/normal/wide resize, scrolling, placeholders, delayed images, mouse/keyboard state, and flicker/EDT responsiveness.
- DP-04 remains active until the human review is reported; production navigation and DP-05 controls remain out of scope.


## 2026-08-06 — DP-04 human-review harness continuation

- Added a standalone preview surface and PowerShell launcher for the required human visual/interaction pass.
- Added a durable review checklist for narrow/normal/wide resize, scrolling, placeholders, delayed images, mouse/keyboard state, and flicker/EDT responsiveness.
- DP-04 remains active until the human review is reported; production navigation and DP-05 controls remain out of scope.

DP-04 human review found three presentation corrections: stale background pixels, default Swing scrollbars, and undersized cards. The current return patch addresses all three with theme-semantic clearing, explicit `AppScrollBarUI`, and 220–320 px readable card defaults. Re-run the preview after applying and confirm the selected theme, scrollbar appearance, and rules-text readability.

## 2026-08-06 — DP-04 multi-select and badge amendment

- Human review requested multi-select before DP-04 closure and distinct compact overlays for selection and under-consideration membership.
- This slice adds identity-based toggle selection, a separate externally supplied consideration-membership set, a bottom-center tap-symbol `selected` chip, and a circular top-right chaos-symbol badge.
- Consideration persistence, ordering, and add/remove workspace actions remain DP-06; DP-04 only owns browser display state and interaction.

## 2026-08-06 — DP-04 standard selection gesture amendment

- Human review requested standard Windows list-selection semantics and direct badge actions.
- This slice changes plain click/Space to replacement selection, Ctrl to toggle, Shift to contiguous range, and Ctrl+Shift to additive range.
- Card double-click adds only that card to consideration without changing selection; selected-chip double-click adds the full selected set; consideration-badge click removes one card.
- The selected chip is enlarged, neutral gray at 80% opacity, and anchored flush to the bottom card edge.
- DP-04 remains active pending one more human interaction pass.

## Activity amendment — DP-04 approval and DP-05 activation

- **Date:** 2026-08-06
- **Changed by:** Human repository owner
- **Transition or material change:** Human visual and interaction review approved DP-04 after responsive layout, stable identity, theme, scrollbar, readable sizing, standard selection, and consideration badge amendments.
- **Authority after change:** Codex is activated for bounded DP-05 implementation.
- **Current slice:** Widget-independent filter interaction model and reusable click-first controls for format, color semantics, base types, mana range, semantic tags, and reset.
- **Still out of scope:** DP-06 persistence, AI export, production navigation, and ownership-dependent collection filtering until authoritative quantities are available.

## 2026-08-06 — DP-05 asynchronous results continuation

- Added a reusable restartable filter coordinator with debounce support, generation-based stale-result suppression, cancellation, off-EDT computation, and EDT-only delivery.
- Added explicit theme-aware loading, empty, partial-cache, offline, and failure treatments.
- This slice does not yet wire the coordinator to production navigation or catalog refresh progress; it establishes the interaction-quality boundary and focused tests.
