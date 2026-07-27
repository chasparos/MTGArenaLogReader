# Current Architecture

## Purpose and scope

MTGArenaLogReader is a Windows-oriented Java 24 desktop application that reads MTG
Arena's `Player.log` and turns Arena observations into:

- a semantic, per-game replay;
- match-level score and result state;
- live deck-tracking snapshots;
- draft assistance and draft exports;
- compact match exports and a manual coaching workflow.

This document describes the code as it exists today. Feature-specific contracts live
in the other documents under `docs/`.

## Runtime overview

`app.application.Application` is the composition root. It constructs the queues,
workers, caches, trackers, repositories, and Swing windows, and owns their shutdown.

```text
Player.log
    |
    v
LogTailReader
  - tails or rescans the file
  - frames multiline JSON
  - filters uninteresting records
    |
    v
BlockingQueue<RawLogEntry>
    |
    v
LogMessageReader / LogMessageParser
  - creates ordered typed log messages
  - extracts referenced Arena card IDs
    |
    v
BlockingQueue<LogMessageInterface>
    |
    v
InformationCollector
  - immediately forwards the message
  - completes its model future asynchronously
  - supplements Arena observations with cached Scryfall metadata
    |
    v
MainFrame queue pump (Swing event-dispatch thread)
    |
    +--> GameSessionsPanel --> GameMessageRouter --> MatchSession --> GameSession
    |                                               |                |
    |                                               |                +--> GameEventProjector
    |                                               |                +--> GameModel / GameView
    |                                               +--> MatchProjector / MatchState
    |
    +--> DeckTracker --> DeckTrackerFrame
    |
    +--> DraftTracker --> DraftUiModel --> DraftAssistantFrame
```

Historical records and newly appended records take the same path. Normal startup
begins at the current end of `Player.log`; the UI's rescan action clears the live
queues and asks `LogTailReader` to replay from byte zero.

## Architectural boundaries

| Boundary | Main packages | Responsibility |
| --- | --- | --- |
| Composition and lifecycle | `app.application` | Construct dependencies, start workers, open windows, and close resources. |
| Ingestion | `app.log`, `app.model.log` | Tail, frame, filter, sequence, and decode Arena log records. |
| Optional enrichment | `app.enrichment`, `app.model.card` | Add Scryfall card metadata without replacing Arena-observed facts. |
| Session routing | `app.routing`, `app.model.session` | Route ordered messages by match and game and own their lifetimes. |
| Semantic reconstruction | `app.projection`, `app.model.game`, `app.model.event`, `app.model.match`, `app.snapshot` | Maintain canonical state and derive structured game and match events. |
| Replay presentation | `app.replay`, `app.ui` | Render models and events in Swing; provide copy and navigation actions. |
| Deck tracking | `app.deck` | Parse deck observations, persist deck knowledge, and calculate game snapshots. |
| Draft assistance | `app.draft` | Parse draft observations, maintain the draft UI model, rank cards, and export draft context. |
| Export and archive | `app.export`, `app.archive` | Serialize reconstructed games/matches and archive evicted matches. |
| Coaching | `app.coaching` | Persist coaching conversations and build/slice prompts from canonical match exports. |
| Settings and secrets | `app.settings` | Themes, settings UI, and Windows DPAPI-backed API-key storage. |

The top-level `app.model` package contains the enrichment bundle shared by the
pipeline. More specific state belongs in its `card`, `event`, `game`, `log`, `match`,
and `session` subpackages.

## Reconstruction ownership and lifetimes

State is deliberately divided by lifetime:

```text
Application
    +-- global card/image caches, settings, repositories, thread pools
    |
    +-- MatchSession
          +-- MatchState, MatchProjector, participant and result knowledge
          |
          +-- GameSession (one per game number)
                +-- GameEventProjector
                +-- canonical GameState and game-local collaborators
                +-- GameModel exposed to replay/export
```

`GameMessageRouter` learns the current `matchId` and `gameNumber` from ordered Arena
records. `GameSessionsPanel` creates one `MatchSession` per match ID. A match creates
one `GameSession` per game number; every game receives a fresh `GameEventProjector`
and fresh game state while sharing only explicit match-lifetime knowledge.

`GameEventProjector` is the game reconstruction orchestrator. Focused collaborators
currently own opening-hand tracking, object identity, attachments, counters, damage,
combat declarations, token resolution, pending-cast correlation, and zone-transition
classification/projection. `TargetDecisionTracker` owns the short-lived correlation
between Arena target-selection requests and responses; the projector remains
responsible for turning the resolved observation into a semantic event.
`PlayerSnapshotProjector` owns player life/poison observations and constructs
start-of-turn player and battlefield snapshots from canonical game state.
`RoomProjectionSupport` owns Room parent/facet correlation, half naming, unlocked
state, and repair of cast events emitted before complete card metadata arrived.
`ObjectNameResolver` owns enriched and observed fallback names, transient-object
history, ability-owner lookup, target labels, and late placeholder repair.
`GameObjectProjector` applies Arena's full game-object snapshots to canonical object
state, including characteristics, counters, combat state, tapped reset semantics,
token enrichment, and preservation of the last non-transient semantic zone.
`ObjectLifecycleEvents` classifies newly visible objects and subsequent semantic
zone movements and supplies their user-facing descriptions; the main projector
continues to own cross-event correlations and structured event envelopes.
`ZoneTransferProjector` consumes authoritative zone-transfer annotations, mutates
the canonical semantic zone, correlates Stack/Limbo rollbacks with pending casts,
suppresses Room facets, and coordinates Room-half cast events.
`GameResultProjector` reconstructs terminal winner/loser data and conservatively
classifies damage, poison, empty-library, concession, and draw outcomes from the
completion observation plus canonical state.
`MatchProjector` consumes already structured game events; it does not reparse Arena
JSON.

Arena observations are authoritative. Scryfall data is descriptive enrichment and
must not manufacture gameplay facts. Views and exporters consume structured state;
they must not become parallel reconstruction engines.

## Concurrency and ordering

- Three bounded queues provide back-pressure between tailing, parsing, and delivery.
- The pipeline executor runs the tail reader, message reader, and information
  collector.
- A separate REST executor performs Scryfall work and related-card prefetching.
- A message is delivered before enrichment finishes. Its `modelFuture` completes
  later.
- `OrderedMessageBuffer` waits for model futures and releases completed messages in
  original sequence order; `GameView` projects only those ordered completions.
- Swing mutations are scheduled on the event-dispatch thread.
- Mutable session models expose synchronized mutation and snapshot methods where they
  cross component boundaries.

This sequence-order guarantee is central: asynchronous card lookup must not reorder
game observations.

## Persistence and external systems

Application data is stored below `~/.arena-log-viewer/`:

| Data | Implementation | Location |
| --- | --- | --- |
| Scryfall card metadata and deck cache | embedded H2 | `card-cache.mv.db` and related H2 files |
| Card images | filesystem cache | `images/` |
| Match archives | filesystem exports | `archive/` |
| Coaching conversations/snapshots | embedded persistence repository | `coaching/` |
| Draft rankings | filesystem repository | `draft-rankings/` |
| Ability names and theme preference | Java Preferences | platform preference store |

Scryfall is the only runtime network integration in the current composition.
Manual coaching is explicitly copy/paste based and does not call an AI API. API keys
are stored with Windows DPAPI but are not currently used by the manual coaching path.

## Tests

The Maven/JUnit 5 suite has focused tests around framing, routing, projections,
exports, deck/draft parsing and analysis, settings, and persistence. The
`ArenaLogReplayHarness` exercises the production framing, parsing, routing, and
projection path without Swing or network access.

`src/test/resources/logs/multigame.log` is the principal end-to-end fixture and is
treated as immutable. Smaller synthetic fixtures/tests should cover new edge cases.

Run all tests with:

```bash
./mvnw test
```

On Windows:

```powershell
.\mvnw.cmd test
```

## Current design pressure

The package layout expresses useful subsystem boundaries, but several large classes
still combine orchestration with detailed policy or rendering:

- `GameEventProjector` is the semantic hotspot and remains substantially larger than
  its extracted collaborators.
- `GameView` combines layout, painting, interaction, replay-fragment rendering, card
  hit testing, coaching selection, and ability-name editing. Transient preview-window
  and asynchronous preview-image handling now live in `CardPreviewController`;
  `ReplayTurnSelection` owns single, toggle, and range-selection semantics, while
  `ReplayInteractionController` owns coaching menus, standard questions, and the
  ability-naming interaction. `TurnSnapshotRenderer` owns the complete layout and
  paint pass for start-of-turn player, known-zone, and battlefield snapshots while
  reusing the view's general card-chip painter. `ReplayEventRenderer` owns wrapping,
  context-column layout, panel painting coordination, and event-hitbox registration
  for ordinary chronological events. `ReplayFragmentRenderer` owns fragment sizing
  and the detailed painting policy for text, mana, keywords, cards, Room locks,
  power/toughness, rarity, counters, tapped state, and activated or evergreen
  ability badges; the view contributes theme, hover, and hitbox state through a
  narrow host interface.
- `Application` manually constructs every subsystem and owns all lifecycle details.
- `GameSessionsPanel` both presents sessions and owns routing/session/archive policy.
- deck, draft, and coaching frames contain significant view-state coordination.

These are refactoring candidates, not evidence that the current boundaries should be
discarded. Changes should preserve ordered replay, canonical-state ownership, and the
match/game lifetime split.
