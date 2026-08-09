# Improvement Suggestions (Claude)

Concrete, prioritized suggestions arising from `analysis.md` and `review.md`.
Each item states the problem, a specific direction, and an explicit
acceptance signal so that, if another agent proposes a different or
conflicting fix, the repository owner can compare both proposals against the
same stated problem rather than against differently-scoped descriptions of
"what's wrong." Suggestions are grouped by priority, not by package, since the
goal is to help arbitrate trade-offs, not just list findings.

Nothing here should be treated as authorization to implement — these are
proposals for a human decision, consistent with the repository's own
authority-boundary rules for delegated agent work.

## Priority 1 — Orchestrator decomposition

### 1.1 Give `GameEventProjector` an explicit coordination-only role

**Problem:** Extracting collaborators (`RoomProjectionSupport`,
`ZoneTransferProjector`, etc.) has repeatedly removed *policy* from the
projector but not shrunk its *coordination* responsibility: routing each Arena
signal to the right collaborator, building the resulting structured event
envelope, and correlating across collaborators. That coordination logic has
no name and no owner distinct from "the rest of the file."

**Direction:** Before extracting more collaborators, name the coordination
responsibility itself (e.g., a dispatch table or visitor keyed by Arena
message/record type, mapping each case to the collaborator(s) it invokes and
the event envelope it produces). This turns "GameEventProjector does
everything not yet extracted" into "GameEventProjector is the dispatch table,
plus the residual cases not yet worth a named collaborator." This is a
refactor of *structure*, not of *behavior*, and should be checked against the
existing `ArenaLogReplayHarness`/`multigame.log` regression path with zero
behavioral diffs.

**Acceptance signal:** `GameEventProjector`'s line count should decrease as a
direct result of this restructuring (not just move lines into another
same-sized file), and it should become possible to add a new Arena record
type's handling without touching unrelated dispatch cases.

### 1.2 Track orchestrator size as a first-class metric, not just prose

**Problem:** `current-state.md` names `GameEventProjector` as a "design
pressure" in prose, but there's no mechanism that would catch it growing
further, or catch a *new* file quietly becoming the next `GameEventProjector`
(a real risk given the Deck Planner arc is actively adding files).

**Direction:** Add a simple, low-ceremony check — even just a documented
convention checked during review, or a lightweight test/script that flags any
main-source file over a threshold (e.g., 600–700 lines) — so that orchestrator
growth is visible before it reaches four figures again. This does not need to
be a hard build failure; a warning surfaced during the existing test run or
review checklist is sufficient given the project's preference for minimal
tooling.

**Acceptance signal:** A newly-added or newly-grown file crossing the
threshold is visible without someone having to run `wc -l` by hand, as this
review did.

## Priority 2 — Shared seams across the four vertical slices

### 2.1 Name (don't necessarily unify) the repeated parse→model→persist→UI shape

**Problem:** `app.deck`, `app.draft`, `app.deckplanner`, and `app.coaching`
each reinvent the same four-stage shape with different persistence
mechanisms (filesystem repository, H2, provenance-bearing snapshot) and
different Swing frame patterns. `DeckPlannerFilterCoordinator`'s
generation-based cancel/stale-suppress pattern for asynchronous work is
noticeably more disciplined than what the other three slices document; that
knowledge currently only benefits Deck Planner.

**Direction:** Rather than a large unifying abstraction (which risks becoming
the wrong abstraction for one of the four domains, as noted in `review.md`),
start smaller: extract the *generation-based cancellation* pattern used by
`DeckPlannerFilterCoordinator` into a small reusable utility (e.g., a
`Generation`/`StaleGuard` helper) that `DraftTracker`, `DeckTracker`, and
`CoachingService` can adopt where they perform asynchronous work whose results
can be superseded. This is narrow enough to validate against each consumer's
existing tests individually.

**Acceptance signal:** At least one other async-capable slice adopts the
extracted cancellation helper with no behavior change, proven by its existing
tests continuing to pass.

### 2.2 Consider a lightweight subsystem-registration seam for `Application`

**Problem:** `Application` (275 lines) manually constructs every subsystem and
owns all lifecycle/shutdown details; each new feature arc (Deck Planner most
recently) adds more manual wiring.

**Direction:** Introduce a minimal internal `Subsystem` (or similarly named)
interface with `start()`/`close()` that `Application` iterates over, without
introducing a general DI framework (which would be disproportionate for a
single-process desktop app and contrary to the project's preference for
explicit, inspectable wiring). This keeps construction explicit (still
hand-written in `Application`) while making shutdown ordering and
lifecycle uniform and enumerable rather than ad hoc per subsystem.

**Acceptance signal:** Adding a hypothetical new subsystem requires
implementing one small interface and adding one line to a subsystem list,
rather than hand-editing multiple lifecycle code paths in `Application`.

## Priority 3 — Strengthen boundaries that are currently policy-only

### 3.1 Make the "Scryfall never asserts ownership" rule structurally harder to violate

**Problem:** The rule that catalog/Scryfall data must never assert Arena
collection ownership is currently enforced by documentation and the fact that
live ownership integration is deferred (`SA-MTGA-DEF-003`). When ownership
integration resumes, a future agent under time pressure could plausibly wire
a Scryfall-derived quantity into the UI's ownership overlay by mistake.

**Direction:** When ownership integration resumes, consider making
`CollectionQuantity`'s tri-state (`-1`/`0`/positive) the *only* type accepted
by whatever UI overlay renders ownership, and ensure no code path can
construct that type from catalog/Scryfall data (e.g., by keeping its
constructors package-private to `app.deckplanner.collection` or otherwise
restricting construction to the observed-collection path). This turns a
documented invariant into a compiler-checked one.

**Acceptance signal:** Attempting to construct a `CollectionQuantity` from
catalog code outside `app.deckplanner.collection` fails to compile, not just
fails review.

### 3.2 Add a regression test that asserts no game-lifetime state survives a game boundary

**Problem:** The match/game lifetime separation is the most important
invariant in the reconstruction pipeline, and it's currently protected by
design discipline plus the existing `Games 2 and 3` regression coverage
referenced in `match-support.md`'s arc plan (Patch 1). This review did not
find a single, clearly-named test whose explicit purpose is "assert zero
battlefield/object/zone state survives a new `GameSession`," making it harder
for a future contributor to know where to add coverage when they touch this
boundary.

**Direction:** If such a test does not already exist under a discoverable
name, add one focused test (using the existing multi-game fixture or a small
synthetic one) that explicitly asserts a new `GameSession`'s `GameState`
contains no objects/zones from the prior game, with a name and location that
make it easy to find (e.g., alongside `MatchSession`/`GameSession` tests).
This is as much about discoverability for future agents as it is about
coverage.

**Acceptance signal:** A reviewer or agent searching test names for "game
boundary" or "leak" finds this test directly, without having to infer its
existence from the broader match-support test suite.

## Priority 4 — Process/documentation improvements

### 4.1 Periodically consolidate `engineering-notes.md`

**Problem:** `engineering-notes.md` is append-only and already long; several
entries are near-duplicate headers (two consecutive "DP-04 human review
harness" entries were observed during this analysis). Left unmanaged, it will
accrete the same way `GameEventProjector` did, becoming harder for a new
agent session to extract durable decisions from.

**Direction:** Periodically (e.g., at arc boundaries) fold superseded or
duplicate entries into a single durable statement per decision, keeping the
append-only *event log* value for recent entries but pruning old,
now-redundant blow-by-blow entries once their content is captured in
`current-state.md`, `match-support.md`, or a similar durable document.

**Acceptance signal:** No duplicate section headers, and each entry describes
a decision still relevant to the current codebase rather than a step already
fully superseded by a later entry.

### 4.2 Establish a fixed location/format for cross-agent architecture opinions

**Problem:** This document itself is a solution to the stated need ("model's
opinions differ, and I need to arbitrate"), but there is currently no
convention in the repository for where future agent-authored architecture
opinions should live, what they must cover, or how they cross-reference each
other, beyond the `docs/architecture/<agent-name>/` convention this task
introduces.

**Direction:** If this pattern proves useful, consider adding a short section
to `docs/architecture/current-state.md` or a new lightweight index document
describing the convention (one folder per agent, each with an `analysis.md`,
`review.md`, and `improvements.md` following this same shape) so that future
agents asked for the same kind of analysis produce comparable, similarly
structured output rather than free-form documents that are harder to diff
against each other.

**Acceptance signal:** A second agent's future architecture analysis, if
requested, can be placed in a sibling folder and read side-by-side with this
one without the repository owner needing to first reconcile incompatible
document structures.
