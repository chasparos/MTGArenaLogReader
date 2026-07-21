# MTG Arena Log Viewer

A Java/Swing application that reconstructs completed and live **MTG Arena** games from `Player.log` into a human-readable replay.

This is not a GRE packet viewer. Arena messages are noisy observations; the viewer maintains canonical game state and derives semantic gameplay events suitable for people, clipboard export, and ChatGPT-assisted strategic analysis.

## Core philosophy

```text
Arena observations
        ↓
Canonical game state
        ↓
Semantic, immutable GameEvents
        ↓
Replay UI and exports
```

The UI must not interpret raw GRE messages directly.

Design rules:

1. State first; never UI first.
2. Prefer Arena annotations over inference.
3. Never hardcode Arena zone IDs.
4. Never assume Arena object IDs are stable.
5. Preserve `CardInfo` throughout the pipeline.
6. Prefer immutable events.
7. Avoid duplicate events.
8. Keep unknown information explicit; never invent it.
9. Scryfall enriches Arena identity but does not replace Arena truth.
10. Historical replay and live updates use the same pipeline.

## Current architecture

```text
Player.log
    ↓
LogTailReader
    ↓
LogRecordFramer
    ↓
LogMessageReader / Arena record decoding
    ↓
InformationCollector
    ├── asynchronous Scryfall enrichment
    └── ordered UI message stream
            ↓
GameMessageRouter
    ↓
(matchId, gameNumber)
    ↓
GameEventProjector
    ├── canonical GameState
    ├── Arena annotation correlation
    ├── object-ID alias tracking
    ├── card identity fallback
    └── semantic event reconstruction
            ↓
Immutable GameEvent
    ↓
GameModel
    ├── Gameview rich replay
    ├── clipboard export
    ├── raw routed-log export
    └── BoardStateMonitor snapshots
```

The long-term architecture may introduce an explicit `GameFact` layer between state and human interpretation:

```text
ArenaSignals → GameState → GameFacts → GameEvents → UI
```

A `GameFact` would represent an objective truth such as “object X moved from Battlefield to Graveyard,” while a `GameEvent` could express the human interpretation, such as “Bushwhack destroys Spider Token.”

See [`architecture.excalidraw`](architecture.excalidraw) for the editable diagram.

## Important domain objects

### `GameState`

Canonical per-game state containing:

- players and seats
- dynamically learned zones
- game objects and logical object aliases
- owner and controller
- life and poison
- turn, phase, step, and priority observations
- hand contents and opening hand
- counters
- tapped state
- attachments
- combat declaration state
- match status, winner, result reason, and game number

Arena object identity changes are tracked through `AnnotationType_ObjectIdChanged`. Zone movement primarily comes from `AnnotationType_ZoneTransfer`; state diffing is a fallback.

### `GameObjectState`

Carries both Arena-observed characteristics and resolved `CardInfo`. The object therefore remains useful even when Scryfall cannot identify an Arena-only cosmetic or variant ID.

### `CardInfo`

Mirrors the replay-relevant parts of Scryfall:

- Arena and Scryfall identifiers
- names and card faces
- mana cost and Oracle text
- type line, colors, and keywords
- power, toughness, loyalty, and defense
- image URLs
- related cards and tokens
- legalities
- set, collector number, rarity, and artist

### `GameEvent`

The immutable presentation and export boundary. Events retain context such as turn, active player, phase, step, involved cards, ability references, combat assignments, snapshots, and confidence-relevant source data.

## Processing pipeline

### Log framing

`Player.log` contains ordinary diagnostic lines and pretty-printed multi-line JSON. `LogRecordFramer` tracks braces and arrays while respecting quoted strings, then emits a complete JSON record only after the root closes.

The application reads an existing log from byte zero and continues following appended records in real time.

### Enrichment and caching

Card lookup order:

```text
Memory
  ↓
H2 persistent cache
  ↓
Scryfall
```

The cache is stored beneath:

```text
~/.arena-log-viewer/
```

Important locations:

```text
~/.arena-log-viewer/card-cache.mv.db
~/.arena-log-viewer/images/
~/.arena-log-viewer/card-aliases.properties
```

Positive and negative cache entries are versioned and refreshed. Arena-only variant IDs can be mapped to an exact card name or another Arena ID. For example:

```properties
100673=Doubling Season
```

### Multiple games

`GameMessageRouter` keys sessions by:

```text
(matchId, gameNumber)
```

Each game owns its own `GameModel`, `GameEventProjector`, replay view, scroll pane, and tab. Historical and live messages travel through the same routing path.

## Current capabilities

### Parsing and routing

- historical replay from the beginning of `Player.log`
- real-time tailing
- robust multi-line JSON framing
- ordered asynchronous card enrichment
- multiple matches and games in one log
- normal and queued game-state message handling
- per-game raw-record retention and export

### Card identity

- broad Scryfall domain model
- memory and H2 caching
- stale-cache invalidation
- card-face handling
- image caching and hover previews
- Arena variant-art aliases
- user-maintained alias properties
- late name repair when a previously unknown `grpId` becomes known
- Arena metadata fallback for unresolved cards
- token recognition using direct lookup, creating-card relationships, `all_parts`, fingerprints, and descriptive synthetic fallbacks

### Game reconstruction

- opening hand capture before turn one
- mulligan replacement of earlier hand snapshots
- land plays
- spell casts and cancelled-cast rollback
- permanent resolution
- draws and known hand movement
- graveyard, exile, return, and recursion events
- creature deaths
- activated abilities from `UserActionTaken`
- triggered abilities from `TriggeringObject`
- target selection
- attachment creation, persistence, deletion, and nested board rendering
- tapped and untapped state, including Arena's omitted-false behavior
- life changes
- poison changes
- generic permanent counters in canonical state
- winner and best-effort result reason
- attacker declarations
- blocker declarations
- attack target tracking
- current attacker power/toughness
- duplicate combat snapshot suppression
- start-of-turn life, poison, hand-size, and battlefield snapshots

### Replay UI

- operating-system look and feel
- custom-painted, scrollable replay
- per-game tabs
- turn banners and rounded event panels
- color-aware rounded card chips
- real SVG mana symbols through lightweight JSVG
- mana-cost layout inside card chips
- rounded power/toughness chips
- evergreen keyword/ability decorations
- card-chip-only hover targets
- cached card image and rules preview
- persistent manual names for unknown abilities
- recursive attachment rows
- content-aware preferred height
- compact game export
- raw routed-log export

### Deck tracker

- H2-backed Arena deck cache
- best-effort selected-deck correlation
- separate live tracker window
- library, graveyard, and exile counts
- known copies remaining
- approximate next-draw percentages
- color-identity row styling

## SVG assets

Mana and evergreen keyword icons are stored under:

```text
src/main/resources/mana-svg/
src/main/resources/keyword-svg/
```

`SvgAssetRenderer` paints them directly through JSVG. `ManaSvgSync` can refresh source SVGs from Andrew Gioia's Mana project. The repository also contains attribution and licensing information in:

```text
src/main/resources/MANA-ASSET-LICENSE.txt
```

The basic card-chip, mana-symbol, power/toughness-chip, and keyword-decoration functionality is present. It now needs visual and layout refinement rather than a replacement architecture.

## Known issues and limitations

### Reconstruction

- Combat damage is not reconstructed yet.
- Damage assignment, lethal assignment, deathtouch assignment, trample overflow, and “destroyed by combat” attribution are not modeled.
- Stack ordering, nested stack objects, copies, priority passes, and resolution order are only partially represented.
- Counter state is preserved, but semantic explanation of replacement effects such as Doubling Season is not reconstructed.
- Some ability ownership remains ambiguous when Arena provides only an ability group ID.
- Opponent hidden hands are intentionally never reconstructed.
- Deck reconstruction is best effort and depends on observable Arena data.
- Some tokens and cosmetic variants may still require aliases or manual naming.
- Result reasons remain heuristic when Arena does not expose an explicit cause.

### UI

- Rich chip layout still needs tuning for long card names, narrow windows, mixed text/chips, and wrapped rows.
- Board snapshots are still mostly text and do not yet reuse the complete rich card-chip layout.
- Keyword decorations are detected from rendered text rather than a fully structured keyword event payload.
- Hover previews are tied to card chips, but keyboard navigation and accessibility are not implemented.
- There are no replay controls for play, pause, or timeline scrubbing.
- Event filtering is not implemented.
- Combat groups could be presented more clearly than a single wrapped sentence.
- P/T chips show reconstructed current values, but there is no visual distinction between printed and modified stats.
- SVG asset refresh currently requires running the sync tool during development.

### Engineering

- `GameEventProjector` carries several responsibilities: canonical state mutation, correlation, identity repair, and event generation.
- A dedicated `ArenaSignal` and `GameFact` layer would reduce projector complexity.
- Event confidence (`EXPLICIT`, `CORRELATED`, `INFERRED`) is a design goal but is not yet uniformly represented on every event.
- Automated regression coverage should be built around sanitized `Player.log` fixtures.
- The application currently targets Java 24, which may be unnecessarily restrictive for distribution.
- Packaging as a native installer or self-contained runtime image is not yet configured.

## Roadmap

### Highest priority

1. Combat damage and combat-result reconstruction.
2. Explicit stack model and resolution correlation.
3. Split `GameEventProjector` into signal decoding, state application, facts, and interpretation.
4. Refine rich event and card-chip layout.
5. Add regression tests for known logs and edge cases.

### Next

- structured counter-change events and replacement-effect explanations
- combat grouping and clearer attacker/blocker presentation
- replay timeline, filters, play/pause, and scrub controls
- rich board-snapshot chips
- improved token and cosmetic-ID discovery
- confidence attached to every inferred event
- compact, detailed, and diagnostic export modes
- card appendix for analysis export

### Later

- life and poison graphs
- cards-drawn and card-advantage statistics
- mana curve and removal summaries
- win-condition analysis
- deeper deck reconstruction and library probabilities
- known-opponent deck model
- HTML replay export

## Build and run

Requirements:

- Java 24
- Maven

Run against the default Arena log location:

```bash
mvn compile exec:java
```

Run against another file:

```bash
mvn compile exec:java -Dexec.args="C:\path\to\Player.log"
```

The default Windows path is:

```text
%USERPROFILE%\AppData\LocalLow\Wizards Of The Coast\MTGA\Player.log
```

## Main source map

```text
app.Application                         application lifecycle and pipelines
app.utils.LogTailReader                 historical read and live tail
app.utils.LogRecordFramer               complete JSON record framing
app.utils.LogMessageReader              typed record decoding
app.rest.InformationCollector           ordered asynchronous enrichment
app.utils.GameMessageRouter             per-game routing
app.utils.GameEventProjector            state tracking and event correlation
app.model.GameState                     canonical game state
app.model.GameEvent                     immutable replay event
app.model.CardInfo                      rich Scryfall card domain object
app.monitor.BoardStateMonitor           turn snapshot projection
app.ui.GameSessionsPanel                game tabs and export controls
app.ui.Gameview                         rich replay painter and interaction
app.ui.SvgAssetRenderer                 SVG resource rendering
app.tools.ManaSvgSync                   development-time SVG source sync
app.decklist.*                          deck cache and live deck tracker
```

## Context for future development and Custom GPT sessions

Treat this README and the current source tree as the source of truth.

When modifying the application:

- do not render directly from GRE records;
- preserve the canonical state boundary;
- do not hardcode match-local zone IDs;
- account for Arena object-ID replacement;
- prefer annotations over state diffs;
- preserve `CardInfo` and observed Arena metadata;
- keep events immutable;
- avoid duplicate semantic events;
- keep unknown information conservative;
- make UI features consume structured events rather than reparsing displayed strings whenever practical.

The next architectural refactor should aim for:

```text
ArenaRecord
    ↓
ArenaSignal
    ↓
GameState mutation
    ↓
GameFact
    ↓
GameEvent
    ↓
UI / export
```
