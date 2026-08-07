# Steady Arc Roadmap

## Current arc

- **Arc identifier:** Steady Arc 1.0 Project Memory Compliance
- **Arc type:** maintenance
- **Area in scope:** `.steadyarc/` project-owned continuity Markdown, `AGENTS.md`, and static compliance evidence only. Managed release artifacts, product source, and product tests are out of scope.
- **Completion criteria:** canonical project-memory roles exist; roadmap and handoff use the Steady Arc 1.0 shapes without losing prior evidence; the pre-existing Deck Planner arc is retained without reprioritizing its product objective; managed-tool update status is explicitly classified rather than reconstructed without a verified release archive.

### Ordered items

#### SA-COMP-01 — Migrate continuity memory to Steady Arc 1.0

**State:** complete

**Completion evidence date:** 2026-08-07

Migrate the project-owned continuity artifacts to the current information architecture and lifecycle contracts while preserving the transferred repository's engineering state. This adds the missing design and collaboration-preference memory roles, converts the roadmap to canonical arc/item states, retires the legacy reused handoff record into history, and refreshes repository entry-point routing. Managed tools remain unchanged because the verified Steady Arc release archive required for a version transition was not supplied.

### Active item

None. `SA-COMP-01` is complete in this patch; the arc is at its human evaluation point. After application and repository-side validation, the safe product continuation is a new Deck Planner handoff ID that resumes DP-05.

## Retained context — Deck Planner

- **Arc identifier:** Deck Planner
- **Arc type:** feature implementation
- **Area in scope:** `src/main/java/app/deckplanner/`, Deck Planner-focused tests and preview fixtures, and the minimum shared application/enrichment surfaces explicitly required by a Deck Planner roadmap item.
- **Completion criteria:** DP-01 through DP-08 reach `complete` or an explicitly named `implemented; <X> deferred` state with the required validation, performance, integration, and human visual evidence. Ownership-dependent behavior remains deferred until authoritative Arena collection evidence is available.
- **Resume state:** DP-05 is the next product item, but product implementation is paused until the Steady Arc 1.0 compliance patch is applied and the human issues a fresh handoff ID.

### Mission

Build a responsive Swing workspace for discovering cards legal in a chosen MTG Arena format, narrowing them through structured and semantic filters, collecting candidates under consideration, incorporating collection ownership when Arena logs provide authoritative evidence, and exporting those candidates to an LLM with authoritative card rules.

### Accepted constraints

- The catalog contains cards satisfying both `game:arena` and the selected Scryfall format-legality predicate. Catalog identity must be stable across alternate printings and Arena IDs.
- Scryfall metadata supplements catalog and rules information; it never overrides Arena-observed ownership or gameplay truth.
- Catalog and image prefetch are restartable, rate-limited, observable, and never block the Swing event-dispatch thread (EDT).
- Card presentation uses Swing component views. The planner owns responsive card layout, viewport-aware materialization, hit testing, focus, and selection. Selection/hover/focus/ownership states are painted as lightweight overlays by the containing panel.
- Use Swing's repaint coalescing. Do not introduce a game-loop renderer, direct canvas, or continuously repainted backing surface.
- Collection quantity is a tri-state integer contract: `-1` unknown, `0` authoritatively known absent, and positive values the authoritatively observed number of copies.
- Normal filters include color/color identity, base card type, and mana-value range. Semantic tags are categorized and apply as an additional filter layer.
- The filtered result produces a tag cloud with counts derived from that result before the tag layer is applied, so users can see useful refinements instead of only already-selected tags.
- Cards can be added to a persistent "Under consideration" workspace.
- AI export uses a versioned, token-conscious text protocol, includes authoritative rules for every considered card, and ends with exactly: `What deck would you build with these cards?`

### Ordered items

#### DP-01 — Catalog and enrichment foundation

**State:** complete

**Completion evidence date:** 2026-08-06

Define a format catalog service that pages Scryfall with a query equivalent to `game:arena legal:<format>`, normalizes duplicate printings to a documented planning identity, and passes cards sequentially through a reusable enrichment/cache boundary.

Acceptance evidence:

- Query construction and pagination tests cover multiple pages, `has_more`, cancellation, retryable failures, and Scryfall request spacing.
- A format snapshot records format, fetch time, schema/catalog version, completion state, and per-card enrichment outcome; interrupted runs resume without corrupting the last complete snapshot.
- Every accepted catalog entry has Arena availability evidence, format legality, stable Scryfall identity, Arena ID where Scryfall supplies it, rules metadata, filter fields, and image references.
- Existing message-driven `InformationCollector` behavior remains intact; bulk ingestion reuses extracted enrichment primitives rather than manufacturing fake log messages.

Completion evidence (2026-08-06):

- `ScryfallClient` exposes the constrained `game:arena legal:<format>` paged source and distinguishes retryable HTTP 429/5xx failures from terminal failures.
- `FormatCatalogService` processes pages/cards sequentially, supports cancellation, exponential retry, progress events, and payload-level Arena/legality validation.
- `FormatCatalogRepository` retains resumable staging runs and atomically publishes timestamped, schema-versioned snapshots with per-printing outcomes.
- `CatalogCardIdentity` groups alternate printings by Oracle identity while retaining every Scryfall/Arena printing; preferred printing selection is deterministic.
- `CardEnrichmentService` is shared by live `InformationCollector` and bulk catalog ingestion.
- Support-relay `maven-test` passed: 164 tests, zero failures/errors/skips.

#### DP-02 — Arena collection observation

**State:** implemented; live ownership integration deferred

**Completion evidence date:** 2026-08-06

Discover and parse the authoritative Arena log response(s) that expose owned card quantities, then persist a provenance-bearing collection snapshot keyed by Arena card identity.

Acceptance evidence:

- Focused fixtures distinguish a complete snapshot from deltas, deck contents, cosmetics inventory, and unrelated inventory messages.
- `-1`, `0`, and positive quantities round-trip through model and persistence tests without conflation.
- Zero is emitted only when a complete authoritative snapshot proves absence; incomplete or never-observed data remains `-1`.
- Snapshot provenance includes source record/sequence and observation time, and stale-session behavior is specified.

Implementation evidence (2026-08-06):

- `ArenaCollectionLogParser` recognizes explicit `PlayerInventory.GetPlayerCardsV3` responses and structurally equivalent non-empty bare maps whose keys are positive Arena IDs and values are positive integer copy counts.
- Empty objects, generic `InventoryInfo`, decks, mixed named/numeric maps, zero/negative values, and fractional values are rejected and cannot replace the current snapshot.
- `ArenaCollectionRepository` atomically publishes complete positive-entry snapshots. Before a complete snapshot, quantities are `-1`; after one, omitted IDs are `0`; present IDs retain their positive counts.
- Provenance records observation time, raw-record sequence, and explicit-vs-bare source shape. A caller-supplied freshness window converts stale observations back to `-1` without deleting audit history.
- Raw observation is wired before gameplay parsing; unsupported delta-like records do not mutate collection state.
- Support-relay `maven-test` passed: 171 tests, zero failures/errors/skips.

Deferred acceptance evidence:

- Current production `Player.log` captures from 2026-08-06 were inspected after opening Collection, entering Deck Builder, adding owned and unowned cards, and saving a deck. Arena logged navigation and the complete deck upsert, but published no `PlayerInventory.GetPlayerCardsV3` response or structurally equivalent complete owned-card map.
- Live ownership-dependent integration is deferred under `SA-MTGA-DEF-003` until Arena again publishes an authoritative complete collection record. The parser, provenance model, repository, and observer remain available and tested; no ownership is inferred from deck contents.

#### DP-03 — Filter index and categorized tag cloud

**State:** complete

**Completion evidence date:** 2026-08-06

Build immutable/filterable indexes over the completed catalog. Define normalized categories for colors, base types, mana value, oracle keywords, zones, mechanics/actions, and compound concepts such as `all creatures`.

Acceptance evidence:

- Color behavior explicitly covers colorless, multicolor, exact-vs-inclusive color matching, and color identity.
- Base-type extraction handles multi-face cards and type lines containing supertypes/subtypes.
- Mana ranges handle lands, split/adventure/modal cards, and fractional/exceptional values consistently.
- Tag rules are deterministic and versioned; tests cover `mill`, `sacrifice`, `target`, zone terms, printed keywords, and `all creatures` without naïve substring false positives.
- Structured filters combine predictably, selected tags add an AND layer, same-category multi-selection semantics are documented, and tag-cloud counts are recomputed against the active selected-tag result.

Completion evidence (2026-08-06):

- `CatalogFilterIndex` builds an immutable projection over a completed format snapshot and applies color/color-identity, exact/inclusive color matching, explicit colorless behavior, base types, and mana ranges without UI dependencies.
- Base types are extracted from top-level and face type lines before subtypes, covering modal and other multi-face cards.
- Mana filtering uses Scryfall's top-level layout-aware `cmc`, preserves fractional values, and normalizes omitted/invalid values to zero; focused tests cover split, adventure, modal, land, fractional, and invalid-range cases.
- `CardTagRules.VERSION` fixes deterministic schema version 1. Word-aware rules cover printed keywords, mill, sacrifice, target, graveyard/exile/library/hand/battlefield, and `all creatures` while avoiding substring false positives.
- Selected tags use global AND semantics, including selections within the same category. Tag-cloud counts are calculated after structured filtering and the active selected-tag AND layer so each activation recounts the remaining refinements.
- Local validation passed: 174 tests, zero failures/errors/skips at source commit `41a86e54c6c1d77b6003096f7b79ef3d9134b8e8`; the final acceptance-tightening patch adds focused mana-policy and same-category tests for the next validation run.

#### DP-04 — Responsive card browser and asynchronous images

**State:** complete

**Completion evidence date:** 2026-08-06

Create the Deck Planner frame/workspace, responsive card layout, reusable `CardView`, and viewport-aware image scheduling.

Acceptance evidence:

- Only visible cards plus a small directional prefetch margin request decoded images; requests are cancelled or deprioritized when cards leave that window.
- Network, disk, decoding, scaling, catalog filtering, and tag counting run off the EDT; Swing mutation and repaint requests run on the EDT.
- Resize recomputes columns and card bounds without losing logical selection or scroll anchor.
- Card views preserve card aspect ratio, show stable placeholders, and repaint only affected regions when images arrive.
- The panel performs element layout and hit testing, supports mouse and keyboard focus/selection, and paints hover/selection/focus/ownership overlays without mutating cached card images.
- Rendered-fixture evidence covers narrow, normal, and wide viewports; a human visual pass confirms responsiveness and interaction feel.

#### DP-05 — Filter controls and interaction quality

**State:** active

Current slice: human-review corrections for wrapping compact MTG-styled controls, dual-handle mana range, and active-tag faceted counts.

Add appealing click-first controls for format, colors, base types, mana range, collection status, and categorized tags, with clear active states and fast reset/navigation.

Acceptance evidence:

- Filter state is a model independent of widgets and has deterministic unit tests.
- Rapid toggling, resizing, and scrolling do not queue stale result/image work or freeze the EDT.
- Empty, loading, partially cached, offline, and failed-catalog states have explicit UI treatments.
- Keyboard traversal and visible focus are supported; color is not the sole indication of state.

#### DP-06 — Under consideration workspace

**State:** planned

Persist an ordered set of candidate cards and expose add/remove/clear/reorder interactions without losing the active browser filters.

Acceptance evidence:

- Membership survives restart and catalog refresh through stable identity mapping.
- Duplicate-printing behavior is defined and tested.
- Browser and consideration views remain synchronized, including overlays and collection counts.
- Empty and stale/unresolvable candidate states remain recoverable.

#### DP-07 — Authoritative AI deck-building protocol

**State:** planned

Create `MTGA_DECK_BUILD_REQUEST_V1`, following the established authoritative/provenance discipline of game reconstruction exports while using a deck-planning-specific schema.

Acceptance evidence:

- Payload includes target format, consideration quantity/membership, collection quantity with unknown distinguished from zero, stable identities, name, mana cost/value, type line, complete oracle text, colors/color identity, power/toughness/loyalty/defense when applicable, keywords, produced mana, and every relevant face of multi-face cards.
- Repeated values use a compact alias/table mechanism where it saves tokens without making rules ambiguous.
- Export is deterministic, escapes delimiters/newlines, states that supplied card data is authoritative, forbids substitution/invented rules, and separates observed facts from strategic inference.
- Golden tests cover ordinary, token-producing, split/adventure, transform/modal, and metadata-incomplete cards plus the `-1/0/positive` collection states.
- The final request text is exactly `What deck would you build with these cards?`

#### DP-08 — Integration, performance, and release evidence

**State:** planned

Wire navigation/startup lifecycle, background service shutdown, persistence migration, and end-to-end validation.

Acceptance evidence:

- Full Maven tests pass on the supported JDK.
- Integration fixture demonstrates format selection → resumable catalog population → filtering/tag refinement → consideration → deterministic AI export.
- Performance evidence records EDT responsiveness, time to first usable catalog view, scroll behavior with a full target-format catalog, image-cache hit behavior, and bounded memory/cache growth.
- Human visual validation covers normal interaction and the required loading/offline/error states.

### Current planning decisions

- DP-01, DP-03, and DP-04 are complete. DP-02 contracts are implemented; live ownership integration is deferred because the current client does not publish authoritative collection quantities in `Player.log`. DP-05 is active and must keep collection-status controls disabled or explicitly unavailable while ownership remains unknown.
- Treat collection extraction as a separate truth pipeline from Scryfall enrichment.
- Reuse `CardInfo`, `CardCache`, `CardImageCache`, and existing exporter conventions where their contracts fit; refactor shared primitives before adding a parallel cache or protocol utility.
- `AsyncVirtualListPanel` is useful evidence for viewport indexing and EDT discipline, but its custom-painted buffered-row design is not the Card Planner rendering model.

### Out of scope for the first arc

- Automatically submitting or importing a deck into Arena.
- AI-generated card facts that are absent from authoritative metadata.
- Full deck legality/sideboard validation or automated mana-base optimization unless promoted as a later bounded item.
- Live collaborative/cloud synchronization.

## Concurrent arcs

None.
