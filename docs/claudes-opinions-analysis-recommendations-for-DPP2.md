# Claude's Opinions, Analysis, and Recommendations for DPP2

## Purpose of this document

This document consolidates and expands on the two preparatory DPP2 documents:

- `docs/Deck Planner Phase 2 - ideas and bullets.md` — the vision (synergy discovery,
  card grouping, a query language over tag/relation groups).
- `docs/DPP2 - research.md` — three inline research/idea dumps about tokenizing
  Oracle text and building an in-memory synergy graph.

Both source documents are candid, exploratory, and contain internal
inconsistencies (the research document says so explicitly). This document reads
them together, cross-checks them against the code that already exists in
`app.deckplanner.filter` (the DP-05/DP-08 tag-cloud implementation), and gives a
single opinionated recommendation for how to shape DPP2-01 without freezing the
schema prematurely, per the roadmap's own instruction that DPP2-01 "begins with
explanation, examples, and iterative requirements capture."

This is an opinion/analysis document, not an implementation plan. Nothing here
authorizes code changes on its own; DPP2-01 remains discussion-led per
`.steadyarc/roadmap.md` and `.steadyarc/handoff.md`.

## 1. What the two source documents actually agree on

Read past the differing vocabulary (documents 1–3 in the research file use
"knowledge graph," "predicates," and "nodes/edges"; the ideas file uses
"groups" and "interaction relationships"), and there is one converging shape:

1. Cards should be classified into a bounded, hand-curated vocabulary of
   mechanical concepts (triggers, effects, costs, subtype-changers, zones),
   not an open-ended bag of free-text tags.
2. Classification should come from **deterministic parsing of Oracle text**
   (regex/pattern matching against Magic's constrained rules vocabulary —
   "Ruleese"), not an LLM or heavyweight NLP pipeline.
3. Synergy is discovered by **traversing shared/related tags between cards**,
   not by hardcoding card-to-card pairs.
4. The output should support two consumers: (a) an interactive "find cards
   that go with this card" recommendation feature, and (b) a richer
   filter/search experience than today's flat tag cloud.
5. All three research documents converge on the same practical lexicon of
   Oracle-text anchors: trigger words (when/whenever/at), replacement/static
   words (instead/as long as/doesn't), the activated-ability colon
   convention, targeting language, and zone-change vocabulary (enters the
   battlefield, dies, exile, etc.). This lexicon is the most immediately
   reusable, low-risk artifact from the research and deserves to be treated
   as a checked-in reference regardless of which architecture is chosen.

## 2. Where the source documents disagree or overreach

- **Graph database vs. in-memory maps.** Research document 2's "Alternative:
  Graph Databases" section (Neo4j/JanusGraph/Cypher) is a scope risk. This is a
  desktop Swing app with an embedded H2 database and no existing graph-store
  dependency. Introducing an embedded graph database is unjustified complexity
  for what is, in practice, a multi-map lookup problem at MTG's card-count
  scale (tens of thousands of cards, dozens of tag types). I recommend
  explicitly rejecting the graph-database alternative for DPP2 and recording
  that decision so it does not resurface as a default next time.
- **"Weighted scoring" is asserted, not derived.** Document 1's
  `calculateLinkStrength` example hardcodes weight constants (1.0, 0.8, 0.4,
  0.3, 0.2) with no stated rationale. Document 2's traversal is unweighted
  (pure predicate matching, e.g., `COSTS_RESOURCE` matches `TRIGGERS_ON`).
  These are two different design philosophies (scored ranking vs. boolean
  match), and the ideas document's own examples ("stronger link," "stronger
  still") assume scoring exists, but never says what the score should be used
  for downstream (sort order? a cutoff? tiering?). This needs a human decision
  before implementation, not an invented default.
- **Predicate schema (document 2) vs. flat tag categories (ideas doc,
  document 1).** Document 2 proposes typed edges between concepts
  (`TRIGGERS_ON`, `COSTS_RESOURCE`, `MUTATES_PROPERTY`, `TARGETS_ENTITY`) —
  effectively a mini ontology. The ideas document and document 1 propose
  simpler symmetric tag membership ("card is in group X"). A typed-edge graph
  is more expressive (it can represent "outlet supplies input that payoff
  wants") but is materially more implementation and maintenance cost than a
  tag-membership index. This is the single largest architectural fork in the
  material and is addressed as the core recommendation below.
- **Scope creep into "cheat cards into play," "ramp," "removal," full boolean
  query language with `AND/OR/NOT` and parentheses.** These are reasonable
  long-term goals but are UI/query-surface features layered on top of
  whatever tagging model is chosen — they are not prerequisites for DPP2-01
  and should not gate it.
- **Ygra/type-changer example is illustrative but niche.** It is a good test
  case for "modifier" cards but a poor generalizing example to build the
  whole schema around; type-changing effects are rare enough that they should
  be a stretch goal, not a load-bearing requirement for the v1 schema.

## 3. What already exists in the codebase (and why it matters)

DPP2-01 is not starting from zero. `app.deckplanner.filter` already has a
working, shipped, versioned deterministic tag system from the DP-05/DP-08
work:

- `SemanticTag(TagCategory category, String key, String label)` — exactly the
  "bounded vocabulary of typed tags" the research documents ask for.
- `TagCategory` — currently `KEYWORD, ACTION, ZONE, CONCEPT`. This is a
  smaller, already-battle-tested version of document 2's node taxonomy
  (`Zone`, `Action`, `Type/Subtype`, `Variable`).
  - `KEYWORD` tags come straight from Scryfall's own parsed keyword list — free,
    reliable, no regex needed.
  - `ACTION`/`ZONE`/`CONCEPT` tags are extracted by hand-written regex against
    combined Oracle text in `CardTagRules`, matching the "Symbolic AI /
    Regex" approach both research documents recommend over LLMs.
- `CardTagRules.VERSION` — the tag schema is already versioned, which answers
  a question the research documents don't raise: how do you invalidate/rebuild
  cached tags when the extraction rules change. Any DPP2-01 schema extension
  should keep bumping this version rather than inventing new versioning.
- `CatalogFilterIndex` / `IndexedCatalogCard` — an existing index structure
  that already supports faceted tag counting against a narrowed result set
  (see engineering-notes: "Tag-chip counts are faceted against the active
  selected-tag layer"). This is materially similar to what document 1's
  `tagToCardIds` multi-map does, just already integrated with the UI.
- `DeckPlannerFilterPanel` / `DeckPlannerFilterModel` — an existing tag-cloud
  UI that the ideas document explicitly says should evolve ("should take
  advantage of the analysis graph... rather than be a pure/simple 'contains
  string' matcher").

**This means DPP2-01's real question is not "should we build a tag system,"
it is "how do we extend the existing `SemanticTag`/`TagCategory` system with
(a) relationship/synergy edges between tags or cards, and (b) a basic query
language," while deciding whether that extension needs a typed-edge graph or
can stay a multi-map.** Framing it that way avoids re-deriving infrastructure
that already works and is tested.

## 4. My recommendation

### 4.1 Keep the foundation as a tag-membership index, not a typed-edge graph — for v1

Start DPP2-01 by extending the existing `SemanticTag`/`CardTagRules`
model rather than adopting document 2's typed-predicate graph
(`TRIGGERS_ON`/`COSTS_RESOURCE`/etc.) as the initial data model. Concretely:

- Add new `TagCategory` values (or a parallel `SynergyRole` concept) to
  distinguish a tag's *role* in an interaction, at minimum: `TRIGGER` (an
  event a card cares about, e.g., "creature ETB," "creature dies", "artifact
  enters"), `PAYOFF` (a card whose effect scales with or reacts to a trigger),
  `ENABLER`/`SOURCE` (a card that produces the triggering event, e.g., a
  sacrifice outlet, a mill effect, a token generator), and `MODIFIER` (a
  card/effect that changes what other cards' text refers to, e.g., Ygra).
- A synergy lookup for "what goes with card X" becomes: take X's tags, for
  each `TRIGGER` tag find cards tagged `PAYOFF` for the same concept (and vice
  versa for `ENABLER`↔`TRIGGER`), rank by shared-tag count. This is a direct,
  low-risk generalization of the multi-map (`tagToCardIds`) already
  sketched in document 1 and already half-implemented as
  `CatalogFilterIndex`.
- This defers, but does not forbid, a real typed-edge graph. If in practice
  the tag-role model proves too coarse (e.g., it cannot express "outlet cost
  must be a creature, not any permanent"), that is the trigger to introduce
  document 2's typed predicates as a v2 schema bump — but only once a concrete
  case demonstrates the tag-membership model is insufficient. Don't build the
  more expensive model speculatively.
- Do not adopt an external graph database. The existing in-memory,
  H2-adjacent architecture is sufficient at MTG's data scale, and adding a
  graph engine would introduce a new persistence dependency and operational
  surface with no demonstrated need.

### 4.2 Decide scoring's purpose before adding any weights

Before writing a single `calculateLinkStrength`-style function, get a human
decision on what a synergy score is *for*: Is it a sort order for a
recommendation list? A threshold for "strong" vs. "weak" badge display? Or
purely presentational (grouping, not ranking)? My opinion: start with the
simplest useful thing — rank candidate synergy cards by **number of matching
tag relationships**, with no hand-tuned per-category weights — and only
introduce weighting if plain match-count produces visibly bad orderings in
real review sessions. This mirrors the project's established DP-05 pattern of
starting from click-review evidence rather than speculative tuning
(`.steadyarc/roadmap.md`: "Begin with fresh click-review and user-evidence
collection before making concrete UI tuning plans").

### 4.3 Treat the Oracle-text lexicon (research document 3) as a durable, reusable artifact

Document 3's word list (when/whenever/at, instead/as long as/doesn't, the
activated-ability colon, target/up to/choose one, enters the
battlefield/dies/exile) is good, low-risk, immediately actionable reference
material — independent of which graph/index architecture is chosen. I
recommend it be preserved (e.g., folded into engineering notes or a small
reference doc near `CardTagRules`) as the checklist for expanding
`CardTagRules`'s regex set, rather than left buried in a "quick and dirty
research" document. It is the most concretely reusable part of the source
material and shouldn't be lost.

### 4.4 Extend the tag-cloud UI incrementally, don't design a query language yet

The ideas document's request for a full boolean query language
(`AND`/`OR`/`NOT` with parenthesized sub-expressions) compiled from the
filter UI is a reasonable *eventual* target, but it is a UI/UX design problem
on top of whatever data model is chosen, and today's filter model
(`CardFilterState`) already encodes an implicit AND-of-selected-tags,
OR-within-base-types structure that works. My recommendation: don't design
the query language in DPP2-01. Let the "find synergies for card X" feature
and any resulting tag-role additions ship and get used first; a general query
language is worth building once there's a concrete backlog of filter
combinations users actually can't express — not speculatively.

### 4.5 Fix the tag-quality problem raised in the ideas document directly

The ideas document raises a real, already-present quality issue: single-card
flavor tags (its example: "Excalibur" as a keyword-like tag pointing to one
card) pollute the tag cloud. This is worth fixing regardless of the
synergy-graph work, and can be done inside the existing `CardTagRules`/
`CatalogFilterIndex` without waiting on DPP2-01's bigger design: prefer
generic categorical tags (Equipment, Counters) over card-unique proper-noun
substrings, and/or add a minimum-card-count threshold before a
regex/keyword-derived tag is surfaced in the UI tag cloud. I'd treat this as
a candidate quick, low-risk DPP2 item independent of the synergy-graph
decision, since it improves the existing shipped feature rather than
depending on new schema.

### 4.6 Suggested DPP2-01 discussion agenda (in priority order)

1. Confirm or reject the tag-membership-index-first approach in §4.1 (vs.
   document 2's typed predicate graph) with the human before any code is
   written.
2. Enumerate the first concrete trigger/payoff/enabler tag families to
   support (proposed starting set, matching the ideas document's own
   examples: creature-ETB / creature-dies / artifact-enters-battlefield /
   sacrifice-cost / mill / graveyard-recursion / token-creation), and decide
   how many are enough for a useful v1 versus deferred.
3. Decide the scoring question in §4.2.
4. Decide where the "find synergies for this card" entry point lives in the
   existing `DeckPlannerWorkspace`/`DeckPlannerFilterPanel` UI (a new panel? a
   right-click/context action from the card browser? a dedicated view?).
5. Defer the boolean query language and full typed-edge graph as explicitly
   out of scope for DPP2-01, to be revisited only if the simpler model proves
   insufficient.

## 5. Summary opinion

The two source documents are directionally right and largely already
validated by what DP-05/DP-08 built: a deterministic, versioned,
regex-over-Oracle-text tag system feeding a faceted filter index is exactly
the "cheap symbolic AI instead of an LLM" approach both research documents
independently landed on. The main risk in the source material is scope
inflation — a full typed-predicate knowledge graph, hand-tuned synergy
weights, an embedded graph database, and a general boolean query language are
all proposed simultaneously, with no signal for which is actually needed
first. My recommendation is to extend the existing tag infrastructure with a
small set of relationship-role tags (trigger/payoff/enabler/modifier), rank
synergy candidates by simple shared-tag count rather than invented weights,
fix the existing single-card-tag pollution issue as a quick independent win,
and treat the typed-edge graph, weighted scoring, and query language as
deferred v2+ ideas to revisit only once real usage shows the simpler model is
insufficient.
