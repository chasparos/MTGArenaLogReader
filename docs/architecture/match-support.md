# Match Support Architecture

## Status

This document records the architectural boundaries and evolution plan for the
**match-support** arc.

The core lifetime described here is now implemented: `MatchSession` owns
`MatchState` and `MatchProjector`, and creates isolated `GameSession` instances with
fresh per-game reconstruction state. Structured game starts, game results, match
scores, and inferred best-of-three match results are present.

The deck-configuration and complete sideboarding portions remain an evolution area;
the rules below continue to constrain that work. For the complete current runtime
architecture, see [`current-state.md`](current-state.md).

## Goal

Best-of-Three reconstruction must preserve information for exactly as long as that information remains valid.

The governing rule is:

> Reconstruction state must have the same lifetime as the information it represents.

The application routes records by `(matchId, gameNumber)`, creates independent
per-game reconstruction pipelines, and retains explicit match-lifetime knowledge
without turning match state into a second copy of game state.

## Lifetimes

The application uses three conceptual lifetimes:

```text
Application
    ↓
Match
    ↓
Game
```

A longer-lived scope may provide knowledge to a shorter-lived scope. A shorter-lived scope must not leak transient state back into a longer-lived scope unless that state represents knowledge that remains valid.

## Ownership table

| Information | Lifetime | Rationale |
| --- | --- | --- |
| Settings and user preferences | Application | Independent of any match or game. |
| Card database and global card cache | Application | Shared enrichment data, not reconstructed match state. |
| Match identifier and match metadata | Match | Identifies and describes the series of games. |
| Known player identities | Match | The participants remain the same across games. |
| Player role mapping | Match | Local player and opponent identities remain useful across games. |
| Arena seat mapping | Match knowledge, game verification | A prior mapping may seed reconstruction, but each game may re-observe or replace seat assignments. Explicit current-game observations win. |
| Completed game results and match score | Match | Accumulates across games. |
| Match winner and completion status | Match | Describes the completed series, not an individual game. |
| Registered deck identity | Match | Deck registration belongs to the match. |
| Active deck configuration | Match, versioned by game | Sideboarding may change the configuration used by the next game. |
| Sideboarding observations | Match | They describe changes between game configurations. |
| Reusable card identity knowledge | Match | A card identity learned in one game can remain useful in later games. |
| Reusable player or account metadata | Match | It describes a participant, not a transient game object. |
| Zone identifiers and zone contents | Game | Arena creates game-local zones and their contents reset between games. |
| Battlefield, hand, graveyard, exile, library, and stack state | Game | These are canonical game state and reset for a new game. |
| Turn, phase, step, priority, and combat | Game | These are transient game progression state. |
| Opening hand and mulligan state | Game | They belong to one game only. |
| Object instance IDs and logical object aliases | Game | Arena object identity is not assumed stable across games. |
| Counters, tapped state, attachments, and combat assignments | Game | They describe current game objects and must not survive a game boundary. |
| Deduplication and pending-correlation state | Game | It is meaningful only within the observation stream of one game. |

## Match state is knowledge, not continuity of the board

A new game starts with a fresh canonical `GameState`.

The previous game's battlefield, hand, stack, zones, object instances, attachments, counters, combat state, and pending correlations must not seed the next game.

What may seed the next game is durable knowledge learned while reconstructing the match, for example:

- the identities of the players;
- which participant is the local player;
- prior game results;
- known card identities that are not tied to an object instance;
- the deck configuration expected for the new game;
- other explicitly verified match metadata.

This distinction prevents accidental battlefield leakage while still improving later-game reconstruction.

```text
Previous game object state          does not survive
Knowledge learned from observation  may survive when still valid
```

## Observation precedence

Match-scoped knowledge is a seed, never authority over a newer Arena observation.

When a new game explicitly reports information that conflicts with the seed:

1. accept the current Arena observation;
2. update the current game reconstruction;
3. update match-scoped knowledge only when the newly observed fact has match lifetime;
4. retain uncertainty when the conflict cannot be resolved.

In particular, seat mappings must be re-verifiable for every game. The application may start with the prior participant mapping, but it must not force a stale seat assignment over explicit current-game data.

## Match and game responsibilities

The implemented ownership direction is:

```text
Match session
    ├── match-scoped reconstruction state
    └── game sessions
            ├── GameModel
            ├── GameEventProjector
            └── canonical GameState
```

The concrete implementation uses `MatchSession`, `GameSession`, `MatchState`,
`GameModel`, `MatchProjector`, and `GameEventProjector`.

The Swing presentation layer must not become the canonical owner of match reconstruction. UI components consume models and structured events; they do not decide reconstruction lifetime.

Likewise, the deck tracker must not become a second canonical game-state implementation. Replay reconstruction and deck tracking may consume common match-scoped deck knowledge, but their game-local state remains separate.

## Match seeding boundary

A new game should receive a deliberately limited match seed or match context. It must not receive the previous `GameState` or a previous projector wholesale.

The seed may eventually contain:

- known participant identities;
- local-player and opponent roles;
- re-verifiable seat knowledge;
- prior game results and current score;
- reusable card identity knowledge;
- active deck configuration and its confidence.

The seed must exclude:

- zone contents and IDs;
- object instances and aliases;
- pending casts or correlations;
- opening hand state;
- turn and combat state;
- event deduplication state.

The implementation should prefer a narrow, explicit boundary over sharing mutable collaborators whose internal state mixes match and game lifetimes.

## Sideboarding

Sideboarding changes match-scoped deck knowledge between games. It does not extend the lifetime of game zones or observed objects.

The model must distinguish:

1. **Registered deck** — the deck and sideboard registered for the match.
2. **Game configuration** — the main deck and sideboard believed to be submitted for a particular game.
3. **Observed game state** — cards and zones observed while that game is running.

Exact sideboard changes may only be presented as exact when Arena provides enough evidence to reconstruct a before-and-after configuration.

Suggested evidence levels are:

- **Explicit** — Arena reports the submitted configuration or exact exchange.
- **Reconstructed** — complete before-and-after lists permit an exact diff.
- **Inferred** — observations suggest a change but do not establish the full exchange.
- **Unknown** — insufficient evidence.

A card appearing in a later game is not, by itself, proof of an exact sideboard exchange. The application must preserve that uncertainty.

The deck tracker must eventually calculate remaining copies and draw probabilities from the active configuration for the current game, not blindly from the originally selected deck.

## Match events

Structured match events currently expose:

- match start;
- game start with current score;
- game completion;
- match completion;
- match winner.

Sideboarding start/completion remains planned.

Game winner and match winner are different facts and must remain separate.

Winner determination should prefer explicit Arena observations. Reconstructed score may be used only when the match format and required wins are sufficiently known. Otherwise the winner remains unknown.

The UI and exporters should consume structured match information rather than reparsing display text.

## Arc plan

### Patch 0 — Architecture note

- Define lifetimes and ownership constraints.
- Record seeding and sideboarding rules.
- Make no runtime changes.

### Patch 1 — Match lifetime

- Introduce match-scoped reconstruction ownership.
- Seed later games with durable match knowledge.
- Preserve fresh per-game canonical state.
- Add regression coverage for Games 2 and 3.
- Prove that no battlefield or transient object state leaks between games.

### Patch 2 — Match events and results

- Record completed game results and match score.
- Add structured match-state events.
- Present current score and final match winner.
- Cover 2–0, 2–1, explicit, inferred, and unknown outcomes as supported by fixtures.

### Patch 3 — Match deck configuration

- Introduce match-scoped deck configuration history.
- Keep the selected deck immutable for the match.
- Allow each game to own an independent active configuration.
- Update deck tracker calculations to use the current game's configuration.

### Patch 4 — Sideboarding reconstruction

- Treat complete between-game deck snapshots as explicit configuration evidence.
- Compute exact main-deck additions and removals from adjacent game configurations.
- Emit structured deck-tracker sideboarding events.
- Preserve `UNKNOWN` rather than inferring exchanges from cards merely observed in play.

## Non-goals for this arc

The match-support arc does not introduce:

- an `ArenaSignal` or `GameFact` layer;
- a parallel reconstruction pipeline;
- battlefield continuity between games;
- speculative exact sideboarding;
- UI parsing of raw GRE messages;
- a broad rewrite of `GameEventProjector`;
- unrelated refactoring.

Those changes require separate justification and must not be smuggled into match support.

## Acceptance constraints

Every numbered patch must:

- be generated against the current uploaded repository;
- preserve existing architectural boundaries;
- keep historical replay and live processing on the same routing path;
- preserve all existing regression tests;
- add focused tests for its own behavior;
- remain small enough to review and verify independently.
