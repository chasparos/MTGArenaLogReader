# MTGArenaLogReader

MTGArenaLogReader is a Java desktop application for MTG Arena players. It tails
Arena's `Player.log` file, reconstructs your matches and games in real time, and
enriches the observed cards with Scryfall metadata. It is not a bot, macro tool,
or automation aid — it only reads the log file Arena already writes and presents
that information back to you.

## What it does

- **Live game replay** — reconstructs each game turn-by-turn (plays, casts,
  triggers, combat, zone changes, life/poison totals) from Arena's own log
  messages, with hover previews of card art and rules text.
- **Match and session tracking** — groups games into matches and sessions as
  Arena reports them, and supports rescanning a log file from the start.
- **Deck tracker** — correlates your selected Arena deck to the current match and
  shows remaining library/graveyard/exile contents and draw odds live.
- **Draft assistant** — surfaces draft-related information and export options
  while drafting.
- **Deck planner** — a cache-backed workspace for browsing and analyzing your
  card pool and decks.
- **Manual coaching tools** — lets a reviewer annotate a replay for post-game
  study.
- **Export** — copies a selected game as compact plain text for sharing.

Card data (images, rules text, legalities, etc.) is fetched from the
[Scryfall](https://scryfall.com/) API and cached locally so repeat lookups don't
require network access.

## Requirements

- **Java 21** (JDK) to build and run the application.
- **Apache Maven** (or the bundled `mvnw` / `mvnw.cmd` wrapper — no separate Maven
  install required).
- **Windows** is the primary supported platform. Some features (such as
  Windows-DPAPI-backed secret storage) are Windows-specific; the core log-reading
  and replay functionality is otherwise platform-independent Java/Swing.
- **MTG Arena** installed and generating a `Player.log` file to read.
- Outbound internet access to reach the Scryfall API for card metadata (cached
  locally after first fetch).

### Tech stack

- Java 21, Swing for the desktop UI
- Maven for build/dependency management
- [Gson](https://github.com/google/gson) for JSON parsing
- [Unirest](https://kong.github.io/unirest-java/) for HTTP calls to Scryfall
- [H2](https://www.h2database.com/) embedded database for local card/deck/ownership caches
- [JNA](https://github.com/java-native-access/jna) for native Windows integration
- [JSVG](https://github.com/weisJ/jsvg) for SVG rendering
- SLF4J for logging
- JUnit 5 for tests

## Getting started

Build and run with the Maven wrapper:

```bash
./mvnw compile exec:java
```

On Windows:

```powershell
.\mvnw.cmd compile exec:java
```

By default the application starts at the current end of `Player.log`. Use the
rescan action in the UI to replay the current file from byte zero, or point at a
specific log file:

```bash
./mvnw compile exec:java -Dexec.args="C:\\path\\to\\Player.log"
```

Run the test suite with:

```bash
./mvnw test
```

## More technical documentation

This README intentionally stays focused on what the application is and what you
need to run it. For architecture, subsystem details, and project history, see:

- [`docs/`](docs) — architecture overview, subsystem guides, and coaching
  protocol documentation. Start with
  [`docs/architecture/current-state.md`](docs/architecture/current-state.md).
- [`.steadyarc/`](.steadyarc) — the project's living memory: roadmap, ownership
  handoffs, and durable engineering/design notes for anyone continuing
  development on the repository.
