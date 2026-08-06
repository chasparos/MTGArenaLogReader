# Steady Arc Handoff

## State

- **Status:** Active
- **Handoff ID:** SA-MTGA-DECK-PLANNER-004
- **From:** Human repository owner
- **To:** Codex
- **Created:** 2026-08-06
- **Return owner:** Human repository owner
- **Return condition:** DP-04 delivers the responsive Swing card-browser foundation, viewport-aware image request scheduling, focused tests, and passing validation.

## Engineering context

- **Current arc:** Deck Planner
- **Active roadmap item:** DP-04 active
- **Authoritative baseline:** DP-01 complete; DP-02 parser/model/persistence/observer implemented with 171 passing tests at source commit `a3e9d3b2646087b500a0d422d16f707a9c671be4`.
- **Material upstream limitation:** Current production Arena logs do not publish an authoritative complete owned-card map even after Collection and Deck Builder interactions. Ownership-dependent product behavior is deferred in `SA-MTGA-DEF-003`.
- **Relevant durable constraints:** Scryfall catalog metadata may drive filtering and semantic tags. It cannot establish collection ownership. DP-03 must operate correctly with collection quantity unknown.

## Delegation

- **Requested action:** Proceed to DP-04 after successful DP-03 validation.
- **Completion criteria:** Color/color-identity semantics, base types, mana ranges, semantic tags, structured filtering, selected-tag AND behavior, and pre-tag-layer cloud counts are deterministic and tested.
- **Current authority:** Implement and validate DP-04 only. DP-05 and later items remain inactive except for roadmap/deferred dependency annotations.
- **Constraints:** Do not add ownership inference or block catalog filtering on collection data. Keep the filter model independent of future Swing widgets.
- **Files or areas in scope:** Deck Planner responsive layout, component-browser foundation, viewport-aware image scheduling, focused tests, and Steady Arc continuity memory.
- **Files or areas explicitly out of scope:** Filter widgets, ownership-dependent overlays, consideration persistence, AI export, and release integration.

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
