# GPT Terra Architecture Review

## Scope

This review assesses the implementation described in
[analysis.md](analysis.md). It distinguishes observed implementation facts from
recommendations; proposed changes are collected in
[improvement-suggestions.md](improvement-suggestions.md).

## Overall assessment

The architecture has a sound core: it treats Arena logs as the source of truth,
preserves chronological semantics despite asynchronous enrichment, and keeps
match and game lifetimes explicit. Its main weakness is not a missing
architectural direction but concentration of orchestration, presentation, and
composition responsibilities in several mature classes.

## What is working well

| Area | Assessment | Why it matters |
| --- | --- | --- |
| Truth boundary | Strong | Arena-derived facts are kept separate from optional Scryfall enrichment, avoiding invented gameplay or ownership data. |
| Event order | Strong | Bounded queues, sequence numbers, and `OrderedMessageBuffer` protect chronological replay from out-of-order network completions. |
| State ownership | Strong | `Application`/match/game lifetimes are explicit; a game gets fresh canonical state while match knowledge is retained separately. |
| Reconstruction boundary | Strong | `GameEventProjector` owns semantic state and emits structured events, preventing UI/export code from becoming competing parsers. |
| Failure containment | Strong | Card lookup failure does not halt log delivery; catalog staging prevents incomplete refreshes from replacing a complete snapshot. |
| Testability | Good | JUnit coverage and `ArenaLogReplayHarness` exercise the production pipeline without Swing or network access. |
| Deck Planner | Good | Its catalog, collection, filtering, and UI contracts are intentionally separated; background filtering and stale-generation suppression are explicit. |

## Material design tensions

These are valid trade-offs rather than automatically defects. They are the
likely sources of differing agent recommendations.

| Topic | Current choice | Benefit | Cost / counter-position |
| --- | --- | --- | --- |
| Explicit composition | `Application` constructs concrete dependencies directly | Easy startup tracing; no framework magic | Large wiring surface and awkward isolated composition tests. A DI container is not justified solely by this size. |
| Async enrichment | Deliver base message first; wait only when replay projects ordered completion | Live intake remains responsive; Arena truth survives network failure | A slow earlier future can delay later replay projection. Bypassing the wait would damage chronology. |
| Custom replay painting | `GameView` uses custom-painted virtualized rendering | Avoids a heavyweight component per event and fits rich timeline visuals | Layout, paint, hit testing, and interactions congregate in one UI boundary. Replacing it with generic Swing components could regress performance. |
| Stateful router | `GameMessageRouter` learns match/game context from stream order | Same behavior for historical replay and live tailing | Requires strictly ordered input and defensive handling of incomplete records. |
| Embedded persistence | H2/filesystem/Preferences per concern | No server or user setup | Persistence technologies and migration/versioning responsibilities are distributed. |
| Defensive parsing | Unknown/malformed input often yields no route/event instead of failing the pipeline | Robust against log drift | Silent degradation can make unsupported Arena schema changes hard to diagnose. |

## Risks to manage

1. **Semantic hotspot** — `GameEventProjector` still coordinates extensive
   precedence, mutation, correlation, and event-emission policy. Collaborator
   extraction has reduced scope, but changes can still have broad replay impact.
2. **Presentation hotspots** — `GameView`, `GameSessionsPanel`, and the
   deck/draft/coaching frames mix rendering and interaction coordination. This
   makes visual behavior harder to verify without focused fixtures or human
   review.
3. **Composition hotspot** — direct construction in `Application` makes
   lifecycle ownership visible but grows every time a subsystem is introduced.
4. **Observability gap** — graceful handling of unknown JSON is appropriate,
   yet unstructured or quiet drops reduce diagnosis of Arena protocol drift.
5. **Persistence evolution** — H2 schemas and filesystem formats are managed
   by individual repositories. Inconsistent version/migration discipline can
   create upgrade risk.
6. **Resource contention** — the small shared REST executor and synchronized
   persistent caches provide predictable limits but may couple slow enrichment,
   image, pasted-log, and catalog work under load.
7. **Planner integration boundary** — the Deck Planner is deliberately
   reusable and not yet production navigation. Folding it into the application
   prematurely would risk violating its tested separation and collection-truth
   constraints.

## Guardrails that should remain non-negotiable

- Do not use Scryfall, deck membership, or craftability to infer collection
  ownership.
- Do not let views or exports re-interpret raw Arena JSON as a second semantic
  engine.
- Do not reorder game observations to make enrichment appear faster.
- Do not share a previous `GameState` with a later game in the same match.
- Keep network, disk, decoding, filtering, and tag counting off the EDT.
- Preserve explicit unknown values where Arena does not provide authoritative
  information.

## Evidence confidence

This review is high-confidence for package boundaries, composition, queue
capacities, executor ownership, persistence locations, and documented
invariants because those are represented in current source and authoritative
architecture documents. It is moderate-confidence for performance and
operational contention because no runtime profile was collected for this
review. The recommendations intentionally call for measurements before
changing concurrency limits or introducing new abstractions.
