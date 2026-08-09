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
| Deck Tracker | `DeckTrackerFrame` | Singleton companion frame reached through `SecondaryWindowModule` | Shell navigation opens the retained timeline window; later decide whether to embed. |
| Draft Assistant | `DraftAssistantFrame` | Singleton companion frame reached through `SecondaryWindowModule` | Shell navigation opens the existing draft window without redesigning draft internals. |
| Coaching | `CoachingFrame` opened for a selected match | Replay-contextual secondary frame | Remains a selected-match action owned by Replay; it is not a context-free shell module. |
| Settings | `SettingsDialog` | Dialog owned by `app.ui.MainFrame` | Remains an application-shell action rather than a content module. |
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

`devtools.ReplayUiHarness` is the dedicated repository-owned surface for developer-only replay interactions.

The harness owns bundled match/draft fixture loading, pasted raw-log scanning, arbitrary log-file selection, replay presentation click testing, and any future deterministic replay fixture chooser. It uses production framing, parsing, routing, and Replay presentation with deterministic empty enrichment, so it does not require Scryfall access.

The production Replay module receives real/observed application state and exposes legitimate user-facing replay controls only. The obsolete replay `MainFrame` and dormant fixture/paste composition have been removed from production.

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

## Integration findings and later consolidation inventory

The shell integration established these evidence-backed boundaries:

- Replay and Deck Planner are genuine embedded modules and should remain the reference shapes for future embeddable content.
- Deck Tracker and Draft Assistant remain singleton companion windows. Their shell adapters make ownership honest without duplicating services or pretending their current frame layouts are already embeddable.
- Coaching remains contextual to a selected reconstructed match. A later pass may add a conversation browser entry, but opening or creating coaching for current play continues to belong to Replay context.
- Settings is application chrome, not module content. It remains owned by `app.ui.MainFrame`.
- Developer fixture and pasted-log work is isolated in `devtools.ReplayUiHarness` and must not drift back into production navigation.

The next visual consolidation arc should evaluate, in order:

1. shared top-level spacing, typography, module-tab treatment, status language, and keyboard navigation;
2. whether Deck Tracker and Draft Assistant can expose embeddable root panels while retaining optional detached-window presentation;
3. common loading, offline, empty, and failure surfaces across Replay, Deck Planner, Draft, and tracking;
4. unified ownership and disposal for secondary dialogs/windows, including theme refresh and application shutdown;
5. accessibility contracts for focus order, selected-module announcement, shortcuts, and high-contrast themes.

The colored horizontal selector is intentionally a first shell treatment, not a frozen design system. Human review required increasing the initial tint contrast, evidence that later theme work must test perceptibility rather than relying only on structurally distinct colors.

## Deferred next mission: Arena collection extraction

After this Application Shell arc is accepted, the intended next Engineering Arc is the deferred `SA-MTGA-DEF-005` research mission: investigate an optional Windows/JNA process-memory reader for authoritative MTG Arena collection quantities.

That work remains separate from the shell refactor. The shell may eventually provide a user-facing “Synchronize Collection” entry point, but this arc must not implement or simulate collection memory scanning.
