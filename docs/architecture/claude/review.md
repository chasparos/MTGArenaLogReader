# Architectural Review (Claude)

This is an evaluative review, not a description. It complements `analysis.md`
(what the system is) with judgments: what is sound, what is risky, and where
stated intent and actual code diverge. It is written to be directly comparable
against another agent's review of the same repository, so the repository
owner can arbitrate disagreements. Each finding is stated as a discrete claim
with a confidence level and, where possible, a concrete file/line reference so
disagreement between agents can be checked against the same evidence rather
than against each agent's prose.

## Summary judgment

The core reconstruction architecture (ingestion → routing → match/game
lifetime → semantic projection → presentation) is well-conceived and, on
inspection, is largely honored by the code, not just documented aspirationally.
The most significant architectural risk is concentration of responsibility in
a small number of large orchestrator classes, which the project's own
documentation already acknowledges. The Steady Arc process layer is unusually
disciplined for a hobby-scale project and is a genuine asset for multi-agent
work, but it is also a second architecture (a governance architecture) that
this review treats as being just as inspectable as the code.

## Strengths (high confidence)

1. **The lifetime model is real, not aspirational.** `MatchSession` owns
   `MatchState`/`MatchProjector` and constructs a fresh `GameSession` per game
   number; `GameSession` owns a fresh `GameEventProjector` and canonical
   `GameState`. This matches `match-support.md`'s prescriptive ownership table.
   The separation is what prevents battlefield-state leakage across games in a
   Bo3 match, which is a real and easy-to-get-wrong correctness property for
   this domain.

2. **Sequence-order preservation under asynchronous enrichment is a genuinely
   good design.** Delivering a message immediately while its `modelFuture`
   resolves later, then re-serializing completions through
   `OrderedMessageBuffer`, avoids the common trap of either (a) blocking the
   pipeline on every network call or (b) losing ordering guarantees when
   enrichment completes out of order. This is exactly the kind of concurrency
   decision that is easy to get subtly wrong, and the chosen approach is
   defensible.

3. **Arena-observation-is-authoritative is consistently stated and appears to
   be consistently followed.** Both `current-state.md` and `match-support.md`
   state that Scryfall/enrichment must never manufacture gameplay facts, and
   the Deck Planner arc repeats the same principle for collection ownership
   (`-1`/`0`/positive tri-state, never inferred from deck contents). Seeing the
   same non-negotiable principle re-derived independently in a different arc
   (Deck Planner ownership, `SA-MTGA-DEF-003`) is a good sign that it is a
   genuine team invariant and not just documentation for one subsystem.

4. **Test fixture discipline.** Treating `multigame.log` as immutable and
   requiring new synthetic fixtures for new edge cases is a sound way to avoid
   fixture rot, and `ArenaLogReplayHarness` running the production path without
   Swing/network is a reasonable seam for fast, deterministic tests of a
   fundamentally asynchronous, I/O-driven pipeline.

5. **Explicit evidence tiers (contract / integration / rendered-fixture /
   human-visual).** The Deck Planner arc's insistence on rendered-fixture and
   human-review evidence before declaring visual/interaction work complete,
   rather than accepting structural test passes as proof of look-and-feel, is a
   maturity marker that is easy to skip under agent-driven development and
   valuable that this project does not skip it.

## Risks and concerns

### 1. Orchestrator concentration (medium-high confidence, self-acknowledged by the project)

`GameEventProjector` is 1075 lines and remains the largest file in the
repository despite eight extracted collaborators (`TargetDecisionTracker`,
`PlayerSnapshotProjector`, `RoomProjectionSupport`, `ObjectNameResolver`,
`GameObjectProjector`, `ObjectLifecycleEvents`, `ZoneTransferProjector`,
`GameResultProjector`, plus `DamageProjector`/`CombatProjector`). Extracting
collaborators without shrinking the orchestrator itself is a common failure
mode: each extraction removes detailed policy but the orchestrator keeps
accreting the *coordination* of an ever-growing number of collaborators, cross
references between them, and the event-envelope construction for all of them.
The risk is not that the decomposition is wrong in kind, but that the
orchestrator's own coordination logic has no owner distinct from "all of it."
`current-state.md` already names this as the top design pressure; this review
independently confirms it by direct inspection and agrees with the project's
own assessment. Where this review differs from a purely restating summary: the
risk compounds because `GameEventProjector` is also the piece of the codebase
most frequently touched by rule-specific bug fixes (Room half-naming, target
correlation, damage/poison/concession classification), meaning the highest
change-frequency code is also the largest, hardest-to-review file.

### 2. Four parallel vertical slices with duplicated structural shape (medium confidence)

`app.deck`, `app.draft`, `app.deckplanner`, and `app.coaching` each independently
implement: log/data parsing → domain model → a persistence/repository type →
a dedicated Swing frame with significant view-state coordination
(`current-state.md` names this directly for deck/draft/coaching frames).  This
is not necessarily wrong — these are genuinely different domains — but there
is no shared abstraction for "parse Arena/domain records → maintain a
persisted domain snapshot → drive a Swing frame," so improvements to one
slice's parsing robustness, persistence atomicity, or UI state-handling
pattern do not propagate to the others automatically. `DeckPlannerFilterCoordinator`'s
generation-based cancellation-and-staleness pattern (documented under "DP-05
asynchronous filter coordination" in `.steadyarc/engineering-notes.md`) is a
good pattern that the other three slices do not obviously share.

### 3. `Application` as a manual composition root (medium confidence)

`current-state.md` already flags that `Application` "manually constructs every
subsystem and owns all lifecycle details" (275 lines). This review agrees this
is a maintainability risk rather than a correctness risk today — at the
current subsystem count it is still readable — but the trend across the
project's own roadmap (Deck Planner added an entire new vertical slice) means
this constructor is likely to keep growing linearly with every new feature
arc, and there's no described plan (in the docs this analysis found) for
subsystem lifecycle registration that would cap that growth.

### 4. `GameView` as a rendering god-object, partially mitigated (medium confidence)

`current-state.md` documents that layout, painting, interaction, replay
rendering, hit testing, coaching selection, and ability-name editing were
originally combined in `GameView`, and that several concerns have since been
extracted (`CardPreviewController`, `ReplayTurnSelection`,
`ReplayInteractionController`, `TurnSnapshotRenderer`, `ReplayEventRenderer`,
`ReplayFragmentRenderer`). At 627 lines, `GameView` is smaller than
`GameEventProjector` and than `ReplayFragmentRenderer` (700 lines) — the
extraction has visibly worked better here than for the projector, likely
because rendering concerns decompose more naturally along "what kind of visual
element" lines than reconstruction concerns decompose along "what kind of
Arena signal." This is a case where two similar-looking problems (a large
class needing decomposition) responded differently to the same extraction
strategy, which is useful context before assuming the projector needs the same
treatment applied harder.

### 5. Two independent Deck Planner data paths with only a documented, not enforced, boundary (medium confidence)

The Deck Planner's catalog path (Scryfall-fetched, independent of the log
pipeline) and its Arena-collection-ownership path
(`ArenaCollectionLogParser` reading `Player.log`) are architecturally separate
and are documented as never allowed to let Scryfall data assert ownership.
This is a good rule, but it is currently enforced by documentation and code
review discipline (and by the fact that live ownership integration is
deferred under `SA-MTGA-DEF-003`) rather than by a type system boundary that
would make an ownership-inferred-from-Scryfall bug impossible rather than
merely against policy. Given that ownership integration is explicitly
deferred and will be revisited later, this is worth flagging now while the
boundary is still simple, rather than after a future agent wires ownership
inference under time pressure.

### 6. Steady Arc process layer: strength and single point of failure (medium confidence)

The `.steadyarc/` handoff/roadmap/engineering-notes system is unusually good
at giving a stateless agent enough context to resume delegated work safely,
and its evidence-tier discipline (contract vs. rendered-fixture vs.
human-visual) is exactly the kind of guardrail that prevents an agent from
overclaiming completion. The review notes two things: (a) `engineering-notes.md`
is already long and growing append-only; without periodic consolidation it
will itself become a large, hard-to-navigate artifact analogous to
`GameEventProjector` — a governance document with the same "everything
accretes here" failure mode as the code it governs; (b) the handoff file
currently encodes a single active named delegation (Codex, DP-05) — this
document (produced by a different agent, for a documentation-only task
outside that delegation) is a concrete instance of the boundary the
`AGENTS.md` "Authority boundary" section describes, and it worked as intended:
this task was recognized as outside the active handoff's implementation scope
and treated as a bounded documentation request rather than as license to
touch DP-05 code.

## Points where this review's confidence is limited

- This review is based on static reading of source, tests, and documentation,
  not on running the application, profiling GC/latency, or exercising the
  Swing UI interactively. Claims about runtime behavior (queue back-pressure
  behavior under real Arena log throughput, EDT responsiveness under load) are
  inferred from code structure, not measured.
- No performance benchmarks or profiling artifacts were found in the
  repository; this review cannot make evidence-based claims about whether the
  three-queue pipeline or the H2-backed caches meet any implicit performance
  goal, only that the *design* for back-pressure and asynchronous enrichment
  is coherent.
- The Deck Planner arc is mid-flight (DP-05 active per the current handoff at
  the time of this analysis). Judgments about it describe the architecture as
  currently checked in, not a final state.

## Where a second agent might reasonably disagree

To make arbitration easier, here are the calls in this review most likely to
be contested by a differently-trained model, and why:

- **Whether `GameEventProjector`'s size is a real problem or an acceptable cost
  of a single coherent state machine.** Some agents may argue that Arena's
  actual event grammar is irreducibly complex and that splitting the
  orchestrator further would only relocate complexity into inter-collaborator
  coordination that is *harder* to review than one file. This review leans
  toward "extract further" but acknowledges the counter-argument is not
  frivolous — the file has already survived multiple rounds of extraction and
  remains large, which is some evidence that its residual size is close to
  irreducible for its current responsibilities.
- **Whether the four vertical slices (deck/draft/planner/coaching) should be
  unified behind a shared abstraction, versus intentionally kept independent
  because their domains genuinely differ.** This review flags the duplication
  as worth *noticing*, not as a confident recommendation to unify — a shared
  abstraction introduced too early could easily become the wrong abstraction
  for one of the four domains.
- **Whether the Steady Arc process overhead is proportionate.** A differently
  oriented agent might view the handoff/roadmap/engineering-notes system as
  process overhead disproportionate to a single-developer desktop tool. This
  review's position is that, specifically *because* multiple AI agents
  operate on this repository with no persistent memory between sessions, the
  overhead is justified; that judgment depends on the stated multi-agent
  usage pattern and would change for a single-human-maintainer project.
