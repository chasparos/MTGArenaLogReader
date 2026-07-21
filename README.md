# Arena Log Viewer

Java/Swing prototype that tails MTG Arena's `Player.log`, filters interesting entries,
creates typed log messages, asynchronously enriches messages with Scryfall card data,
and displays a copyable parallel log.

## Pipeline

```
Player.log
  -> LogTailReader
  -> BlockingQueue<RawLogEntry>
  -> LogMessageReader
  -> BlockingQueue<LogMessageInterface>
  -> InformationCollector
       -> immediately: BlockingQueue<LogMessageInterface> for UI
       -> asynchronously: CompletableFuture<ModelObject>
  -> MainFrame / JTextArea
```

## Run

```bash
mvn compile exec:java
```

By default the application starts at the current end of `Player.log`. To use another file:

```bash
mvn compile exec:java -Dexec.args="C:\\path\\to\\Player.log"
```

## JSON framing

Arena emits a mixture of one-line diagnostics and pretty-printed multi-line JSON. `LogRecordFramer`
tracks object/array nesting while respecting quoted strings and only emits a JSON record after the
root object or array closes. Filtering happens after framing, so isolated `{` lines are no longer
forwarded as `RAW` messages.

## Persistent REST cache

Scryfall responses, including 404/negative results, are stored in an embedded H2 database at:

```text
~/.arena-log-viewer/card-cache.mv.db
```

Lookup order is in-memory cache, H2 cache, then throttled Scryfall request. New REST results are
written back to H2 immediately.

## Game view

The `Game` tab uses `app.replay.GameView`, a custom-painted `JPanel` backed by
`app.model.GameModel`. Messages are held until their `Future<ModelObject>` is
complete, then projected in original sequence order by `GameEventProjector`.
Each event records the active turn, player, phase and step when Arena exposes
that information. The view uses `paintComponent` rather than one Swing child
component per event.

## v4 game projection changes

- Log reading begins at byte offset 0 so an existing Player.log is replayed on startup.
- H2 uses an embedded file connection without `AUTO_SERVER`.
- Zone names are learned from each match's `zones` collection; numeric zone IDs are match-local.
- The game projector maintains a `GameState` and compares successive object states.
- Transient Limbo/Pending/Suppressed transitions are hidden.
- Common transitions are rendered semantically, including land plays, casts, resolutions, draws, graveyard moves, exile, and recursion.
- Phase changes remain event context and are no longer emitted as standalone rows.

## Multiple-game session support

Version 5 introduces `GameMessageRouter` and `GameSessionsPanel`.

- The log is consumed once in chronological order.
- `matchId` is learned from `matchGameRoomStateChangedEvent`.
- `gameNumber` is learned from `gameStateMessage.gameInfo.gameNumber`.
- Each `(matchId, gameNumber)` pair receives its own `GameModel`,
  `GameEventProjector`, `GameView`, scroll pane, and tab.
- Historical startup replay and live appended records use the same routing path.
- The selected game can be copied as compact plain text with the
  **Copy selected game** button.

The application continues reading `Player.log` from byte zero and then follows
newly appended content, so previously logged games appear first and an active
match continues updating in realtime.

## Annotation-aware replay

The replay projector now consumes Arena annotations for authoritative zone transfers,
object-ID replacement, target selection, and ability classification. `UserActionTaken`
action type 2 identifies activated abilities; `TriggeringObject` identifies triggered
abilities. Unknown ability kinds are rendered conservatively rather than as spell casts.

## Revision 7 additions

- Captures the local player's visible kept opening hand and includes it in clipboard export.
- Retains Scryfall card faces, rules text, characteristics, legalities, and image URIs.
- Hover a replay event to see the first involved card's image and rules text. Images are cached under `~/.arena-log-viewer/images`.
- Ability events retain `(sourceGrpId, abilityGrpId)` metadata.
- Right-click an ability event to assign a persistent human name. Names are stored with Java Preferences and reused in future games.
- Before asking for a manual name, conservative heuristics use a unique triggered/activated Oracle-text paragraph when one can be identified unambiguously.

Opening-hand detection is intentionally conservative: it snapshots the largest visible hand before play begins. Arena does not reveal the opponent's hidden hand.


## Revision 8: full Scryfall domain metadata

- `CardInfo` now mirrors a broad replay-useful subset of the Scryfall Card object: identifiers,
  images, faces, rules text, characteristics, color data, keywords, produced mana, legalities,
  Arena-supported games, set/printing metadata, related parts/tokens, official links, and ranks.
- `GameObjectState` retains its resolved `CardInfo`, so card metadata travels with objects and
  events instead of being flattened to a name.
- Multifaced cards use face-level Oracle text and images when top-level fields are absent.
- Hover details include rules text, P/T, loyalty/defense, set, collector number, and rarity.
- The H2 cache is schema-versioned. Entries written by older revisions (which contained only a
  name and partial rules text) are automatically invalidated and fetched again from Scryfall.
- Positive entries refresh after 90 days; negative results refresh after 7 days.

The first run of revision 8 will therefore re-fetch old cached cards once. This specifically fixes
`previewUrl=null` and `scryfallId=null` caused by revision 7 reading stale JSON from the existing H2
cache. Deleting `~/.arena-log-viewer/card-cache.mv.db` is no longer necessary.


## Revision 9

- Best-effort token identification using the creating card's Scryfall `all_parts` token relationships and Arena characteristics (subtypes, colors, power/toughness).
- Descriptive token fallback instead of raw `ArenaCard#...` labels.
- Start-of-turn player snapshots with life totals, poison counters when Arena exposes them, and hand sizes from zone counts.
- Related token card images and rules metadata are fetched from Scryfall for hover previews.


## v10 — Combat reconstruction

Combat declarations are reconstructed from Arena's canonical object state rather than rendered directly from GRE messages.

The projector now records:

- `attackState` and `attackInfo.targetId`
- `blockState` and `blockInfo.attackerIds`
- structured `CombatAttackAssignment` and `CombatBlockAssignment` payloads on `GameEvent`
- human-readable attacker and blocker declaration events
- idempotent declaration signatures to suppress repeated GRE snapshots

Attackers are finalized when the game reaches declare blockers or a later combat step. Blockers are finalized when the game reaches combat damage or end combat. This first pass deliberately does not infer damage assignment, lethal damage, trample overflow, or which creature killed another.


## v10 combat reconstruction adjustments

- Combat declarations are derived only from the current battlefield representative of each logical object.
- Historical Arena object-ID aliases no longer accumulate as duplicate attackers or blockers.
- Attack and block declarations are labelled with their semantic declaration steps.
- Target annotations fall back to `abilityGrpId` card identity when the affector object is absent, avoiding misleading `Seat <objectId>` text.


## v10 combat reconstruction — land entry and UI refinements

- Permanent entry events now include Arena's explicit tapped/untapped state when supplied.
- Land-play events are ordered before that land's own enters-the-battlefield ability in the same Arena state message.
- Combat phase/step labels are shortened for the fixed-width UI context column.
- Target annotations treat `abilityGrpId` as an ability identifier and resolve its owning card instead of displaying it as an Arena card number.


## Revision 11 — Deck tracker

Revision 11 adds `app.decklist`, an H2-backed deck cache, best-effort correlation of the selected Arena deck to a newly started match, and a separate live Swing deck-tracker window. The window is shown only when a deck is known and the game is active. It displays the Arena-style quantity/name list, color-identity backgrounds, library/graveyard/exile totals, known copies remaining, and approximate next-draw percentages.


## v12 game-state additions

- Tracks player poison counters and emits poison-change events.
- Stores all permanent counter types in canonical `GameObjectState`; the first UI iteration renders only Arena's total power/toughness.
- Adds a passive `BoardStateMonitor` and multiline start-of-turn battlefield snapshots.
- Reconstructs winner and likely result reason (damage, poison, empty library, concession, draw, or unknown/effect).


## Tests

The test suite uses JUnit 5.

```bash
mvn test
```

`ArenaLogReplayHarness` replays finite fixtures through the production log framer,
message parser, game router, event projector, and per-game models without starting
Swing or making Scryfall requests. Regression fixtures belong in:

```text
src/test/resources/logs/
```

The planned `multigame.log` fixture should contain two best-of-three matches so routing,
game-number transitions, match boundaries, and state isolation can be asserted together.


### End-to-end regression fixture

`src/test/resources/logs/multigame.log` contains two complete best-of-three matches
(six games). `MultigameLogReplayTest` sends it through the production framer,
message parser, game router, event projector, and per-game models while replacing
network enrichment with deterministic empty bundles.

The fixture has stable pseudonyms for player, user, and session identifiers. Keep the
fixture immutable; add focused synthetic tests or a separate sanitized fixture for new
edge cases.


## Maintenance refactoring status

The source tree now uses architectural packages for ingestion, routing, projection,
enrichment, replay, export, snapshots, models, and deck tracking.

The first behavioral extraction from `GameEventProjector` is
`app.projection.OpeningHandTracker`. It owns opening-hand and mulligan correlation while
the projector remains the orchestration boundary. Regression coverage includes the
two-match `multigame.log` replay fixture and focused opening-hand tracker tests.


- `AttachmentTracker` owns persistent attachment annotation bookkeeping and exposes stable logical-object relationships to battlefield snapshots.
- `ObjectIdentityTracker` owns Arena instance-alias correlation, current-instance selection, and identity-aware object lookup.
- `CounterProjector` owns permanent-counter identity, naming, and count mutation; annotation interpretation and player-counter events remain in `GameEventProjector`.
- `TokenResolver` owns token related-card matching, ambiguity rejection, and deterministic fallback naming.
- `CombatProjector` owns stable attacker/blocker declaration projection and duplicate suppression.


### Maintenance refactoring status

Zone-transition rule precedence is isolated in `ZoneTransitionClassifier`, while `ZoneEventProjector` owns the existing user-facing transition wording. `GameEventProjector` remains responsible for state mutation, card resolution, and immutable event creation.
