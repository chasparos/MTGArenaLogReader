# UI Consolidation Preparation

## Purpose

This document captures the application-shell boundary before the next UI refactor. It is intentionally an inventory and migration contract, not a visual redesign specification.

The immediate goal is to stop treating the Replay window as the application itself. The target is one production `app.ui.MainFrame` that selects and displays application modules. Existing module internals can remain independent while they are wired through that shell. A later Engineering Arc can then consolidate shared visual language and module structure from evidence rather than from a big-bang rewrite.

## Current ownership

### Application composition root

`app.application.Application` currently constructs and owns the core ingestion/enrichment services and the user-facing subsystem objects. It creates the live log pipeline and UI queue, Deck Tracker and `DeckTrackerFrame`, Draft Tracker and `DraftAssistantFrame`, Coaching services and `CoachingFrame`, settings infrastructure, the replay `MainFrame`, and shared Scryfall/card/image/persistence services.

This is the correct place to continue composing long-lived services. The future shell must not recreate those services.

### Replay MainFrame is currently overloaded

`app.replay.MainFrame` is the current top-level application window, but its responsibilities are mixed:

- production replay presentation through `GameSessionsPanel`;
- application navigation to Deck Tracker and Draft Assistant;
- coaching launch via replay selection;
- settings and log rescan;
- match-log inspection;
- developer/test operations such as replaying the bundled draft fixture;
- pasted raw-log experimentation.

Replay presentation, application navigation, and developer fixture tooling should become separate responsibilities.

### Current module/window shapes

| Area | Current UI shape | Current owner | Shell direction |
| --- | --- | --- | --- |
| Replay | `GameSessionsPanel` embedded in `app.replay.MainFrame` | Replay frame | Become an application module/panel hosted by `app.ui.MainFrame`. |
| Deck Planner | `DeckPlannerWorkspace` used by `DeckPlannerWorkspacePreview` | Preview harness | Wire the proven workspace into production as a selectable module; retain the preview harness. |
| Deck Tracker | `DeckTrackerFrame` | Standalone frame | Initially adapt as a shell entry with explicit detail-window ownership; later decide whether to embed. |
| Draft Assistant | `DraftAssistantFrame` | Standalone frame | Initially adapt to shell navigation without redesigning draft internals. |
| Coaching | `CoachingFrame` opened for a selected match | Secondary frame | Keep contextual/secondary ownership explicit; do not force embedding merely to satisfy the shell abstraction. |
| Settings | `SettingsDialog` | Dialog owned by replay frame | Move ownership to the application shell. |
| Replay fixture tooling | `replayDraftFixtureAction` exposed by production replay frame | Production frame | Move to a dedicated replay UI dev harness. |
| Pasted raw-log scan | `PastedLogDialog` exposed by production replay frame | Production frame | Treat as developer/test tooling unless separately promoted as a product feature. |

## Target shell boundary

The production shell should live at `app.ui.MainFrame`.

The shell owns:

- the one top-level production application window;
- module navigation/selection;
- the central selected-module content area;
- shell title and application-level status/chrome;
- settings entry point;
- application close/window lifecycle;
- visual ownership of shell-level navigation.

The shell does **not** own log parsing, trackers, card/catalog repositories, Scryfall clients, module-specific persistence, replay projection, or Draft/Deck/Coaching business logic. Those continue to be composed by `Application` and injected into module adapters/panels.

## Minimal module contract

The first shell implementation should use the smallest useful module abstraction. Conceptually it needs a stable module ID, display name, one shell-owned component for the selected content area, activation/deactivation callbacks, and optional shell status/title contribution.

Do not encode arbitrary module-specific buttons into the generic module interface. If the abstraction starts accumulating Draft-, Replay-, or Planner-specific methods, the boundary is wrong.

A module may explicitly own secondary windows. Coaching is a good example: it is contextual to a selected replay/match and may remain a detail window even when Replay itself is embedded.

## Replay developer harness

Create a dedicated repository-owned replay UI harness under `devtools` for developer-only replay interactions.

The harness should be the home for replaying bundled fixture logs, pasted raw-log scanning experiments, replay presentation click testing, and any future deterministic replay fixture chooser.

The production Replay module should receive real/observed application state and expose legitimate user-facing replay controls only.

## Deck Planner production integration

Deck Planner is the first strong proof of the module-shell approach because its workspace is already a real Swing component experience and has a mature preview harness.

Production integration should:

1. compose planner repositories/services in `Application`;
2. reuse the persistent `~/.arena-log-viewer` catalog/card/image/Candidate Set paths already exercised by the preview;
3. construct `DeckPlannerWorkspace` once per application lifecycle;
4. expose it through the shell module host;
5. preserve `DeckPlannerWorkspacePreview` for focused UI acceptance and fixture work.

Do not fork a production-only Deck Planner implementation from the preview-proven workspace.

## Staged migration

### Stage 1 — shell primitive

Introduce `app.ui.MainFrame` and a minimal module contract. Host a small initial module set without changing module internals.

### Stage 2 — Deck Planner

Wire Deck Planner into production navigation and validate its lifecycle in the real application composition root.

### Stage 3 — Replay separation

Extract developer-only replay actions into a dedicated dev harness and make Replay a clean production module.

### Stage 4 — remaining module adapters

Add Deck Tracker, Draft Assistant, Coaching/settings entry points with explicit secondary-window ownership where embedding is not yet justified.

### Stage 5 — acceptance and consolidation inventory

Click-test switching, persistence, service reuse, shutdown/relaunch, and module ownership. Update this document with actual friction discovered during integration.

## Future full UI-consolidation pass

The later consolidation pass should evaluate which standalone frames should become embeddable module panels, which detail/context windows should remain secondary windows, which toolbar/status/navigation concepts are truly shared, which Swing primitives should become project-owned reusable components, how theme/spacing/typography/icon/selection conventions have drifted, whether loading/error/lifecycle states can share one vocabulary, and which accessibility/keyboard-navigation contracts should apply everywhere.

Do not answer those questions by forcing every module into the same component hierarchy during the shell arc.

## Deferred next mission: Arena collection extraction

After this Application Shell arc is accepted, the intended next Engineering Arc is the deferred `SA-MTGA-DEF-005` research mission: investigate an optional Windows/JNA process-memory reader for authoritative MTG Arena collection quantities.

That work remains separate from the shell refactor. The shell may eventually provide a user-facing “Synchronize Collection” entry point, but this arc must not implement or simulate collection memory scanning.
