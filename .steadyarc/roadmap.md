# Steady Arc Roadmap

## Current arc

- **Arc identifier:** Application Shell & UI Consolidation Preparation
- **Arc type:** integration / refactor / UX architecture
- **Area in scope:** `src/main/java/app/application/`, the production application shell under `src/main/java/app/ui/`, replay-shell integration under `src/main/java/app/replay/`, module-frame adapters for Deck Planner, Deck Tracker, Draft Assistant, and Coaching, replay-focused developer harnesses under `src/main/java/devtools/`, focused tests, and UI architecture documentation.
- **Completion criteria:** AS-01 through AS-06 reach `complete` or an explicitly named `implemented; <X> deferred` state with green validation and human click evidence. The production application has one true top-level `MainFrame` that owns module selection and displays the selected module; pure replay/dev fixture actions no longer live in the production shell; Deck Planner is reachable as a production module; and the repository contains a documented boundary for the later full UI-consolidation pass.

### Mission

Turn the current replay-centric top-level window into an application shell. Production navigation should select one application module and display that module in the main content area, while replay fixtures, pasted-log experiments, and similar developer-only controls move into a dedicated replay UI test harness. This arc intentionally prepares but does not perform a complete visual/UI consolidation of every module.

### Accepted constraints

- `app.ui.MainFrame` is the target production shell. The existing `app.replay.MainFrame` is not the long-term application frame; replay becomes one module hosted by the application shell.
- A module is application functionality, not merely another top-level `JFrame`. The shell owns application-level navigation, title/chrome, shared settings entry points, selected-module lifecycle, and the central content host.
- Deck Planner must be wired into the production application during this arc rather than remaining preview-only.
- Existing module internals should be adapted incrementally. This arc should not opportunistically redesign Deck Tracker, Draft Assistant, Coaching, or Replay visuals while introducing the shell.
- Pure developer/test actions such as replaying bundled fixture logs and pasted-log experimentation belong in a dedicated replay UI harness under `devtools`, not in production navigation.
- `DeckPlannerWorkspacePreview` remains available as a focused Deck Planner test harness even after the planner is wired into the application.
- The later full UI-consolidation pass should be driven by the architecture inventory produced here: shared chrome, module lifecycle, common toolbar/status concepts, window ownership, common visual primitives, and migration of standalone module frames.
- The next intended Engineering Arc after this one is the deferred MTG Arena process-memory collection extraction research recorded as `SA-MTGA-DEF-005`. Do not start memory scanning inside this arc.

### Ordered items

#### AS-01 — Inventory current UI ownership and define the shell migration contract

**State:** complete

**Completion evidence date:** 2026-08-09

Document the current top-level/window ownership, identify production-vs-devtool responsibilities, define the target shell/module boundaries, and record a staged migration that avoids a big-bang rewrite.

Completion evidence (2026-08-09):

- `docs/architecture/ui-consolidation-preparation.md` inventories the current replay `MainFrame`, `Application` composition root, Deck Tracker, Draft Assistant, Coaching, Deck Planner, and developer-only replay controls.
- The document defines `app.ui.MainFrame` as the production shell and separates module hosting from module-specific presentation.
- It records the later full UI-consolidation concerns without expanding this arc into that redesign.
- Temporary `CardImageTrace` diagnostics used to isolate the Deck Planner image cancellation race are removed after the issue was understood.
- Deck Planner DP-08 is accepted on clean commit `58d4ba1dc2bbbbb560b0f7ac91dcf4dcfa9307b0` with 294 tests passing and explicit human click acceptance.

#### AS-02 — Introduce the true application MainFrame and module host

**State:** planned

Create `app.ui.MainFrame` with application-level navigation and a single central module content host. Define the smallest module contract necessary for selection, visible component ownership, activation/deactivation, and shell title/status integration. Keep module-specific services in `Application`; the frame must not become a second composition root.

Acceptance evidence:

- Focused tests cover module selection, replacement, activation/deactivation ordering, and shell-owned navigation state.
- Closing the shell preserves the existing `Application.close()` lifecycle.
- No replay fixture/dev-only action is introduced into the new shell.

#### AS-03 — Wire Deck Planner into production navigation

**State:** planned

Construct the existing Deck Planner services from the production `Application` composition root and expose `DeckPlannerWorkspace` as a selectable application module using the same persistent catalog/card/image/Candidate Set stores proven in the preview harness.

Acceptance evidence:

- Production navigation can open Deck Planner without launching `DeckPlannerWorkspacePreview`.
- Planner startup remains cache-first/non-blocking and shares the established persistent repositories.
- Existing preview tests remain useful and production integration gets focused lifecycle coverage.

#### AS-04 — Extract replay-only developer controls into a replay UI harness

**State:** planned

Move fixture replay and pasted-log experimentation out of production navigation into a dedicated `devtools` replay UI harness. Keep the production Replay module focused on observed/live replay state and legitimate user-facing replay actions.

Acceptance evidence:

- Bundled draft/replay fixture actions are available from the dev harness, not the production shell.
- Pasted raw-log experimentation is available from the dev harness unless separately justified as a production feature.
- Production Replay behavior and automated replay tests remain intact.

#### AS-05 — Adapt remaining production modules to shell navigation

**State:** planned

Provide incremental shell adapters for Deck Tracker, Draft Assistant, Coaching, Settings, and Replay without forcing their internal visual consolidation. Where a module still needs a secondary/detail window, make that ownership explicit rather than pretending it is already an embeddable panel.

Acceptance evidence:

- Every production module has a documented shell entry point and clear owner for secondary windows.
- Module switching does not duplicate trackers/services or leak background activity.
- Existing standalone-frame behavior remains regression-covered until intentionally retired.

#### AS-06 — Integration acceptance and future UI-consolidation handoff

**State:** planned

Run full validation and human click review of module navigation/lifecycle, then update the architecture document with evidence-backed follow-up work for a later full UI-consolidation arc.

Acceptance evidence:

- Full supported Maven validation is green.
- Human click review covers shell navigation, Deck Planner production use, Replay, Draft, Deck Tracker, Coaching/settings access, shutdown/relaunch, and dev-harness separation.
- The architecture document identifies which standalone frames/primitives should be consolidated later and which should intentionally remain secondary windows.
- Arc acceptance explicitly hands the next Engineering Arc to `SA-MTGA-DEF-005` process-memory collection extraction research unless the human reprioritizes.

### Active item

`AS-01 — Inventory current UI ownership and define the shell migration contract` is complete in the transition patch. No production shell implementation has started yet. The safe next action is AS-02 after repository-side validation of this planning/cleanup patch.

### Current planning decisions

- The Deck Planner arc is complete and human-accepted at clean commit `58d4ba1dc2bbbbb560b0f7ac91dcf4dcfa9307b0` with 294 tests passing.
- The image-display regression was a viewport request-cancellation bookkeeping defect, not corrupt image data or favorite-printing selection; temporary targeted diagnostics are removed after confirmation.
- Prefer an application shell plus module adapters over immediately converting every module into one shared visual framework.
- Preserve `Application` as the service/composition owner; UI classes consume configured services.
- The intended next arc after Application Shell acceptance is `SA-MTGA-DEF-005` MTG Arena process-memory collection extraction research.
- Full visual/UI consolidation across modules is a later mission informed by `docs/architecture/ui-consolidation-preparation.md`, not a hidden requirement of this arc.

## Concurrent arcs

None.
