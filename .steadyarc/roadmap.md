# Steady Arc Roadmap

## Current arc

- **Arc identifier:** Deck Planner
- **Arc type:** feature implementation
- **Area in scope:** `src/main/java/app/deckplanner/`, Deck Planner-focused tests and preview fixtures, and the minimum shared application/enrichment surfaces explicitly required by a Deck Planner roadmap item.
- **Completion criteria:** DP-01 through DP-08 reach `complete` or an explicitly named `implemented; <X> deferred` state with the required validation, performance, integration, and human visual evidence. Ownership-dependent behavior remains deferred until authoritative Arena collection evidence is available.

### Mission

Build a responsive Swing workspace for discovering cards legal in a chosen MTG Arena format, narrowing them through structured and semantic filters, collecting candidates candidates, incorporating collection ownership when Arena logs provide authoritative evidence, and exporting those candidates to an LLM with authoritative card rules.

### Accepted constraints

- The catalog contains cards satisfying both `game:arena` and the selected Scryfall format-legality predicate. Catalog identity must be stable across alternate printings and Arena IDs.
- Scryfall metadata supplements catalog and rules information; it never overrides Arena-observed ownership or gameplay truth.
- Catalog and image prefetch are restartable, rate-limited, observable, and never block the Swing event-dispatch thread (EDT).
- Card presentation uses Swing component views. The planner owns responsive card layout, viewport-aware materialization, hit testing, focus, and selection. Selection/hover/focus/ownership states are painted as lightweight overlays by the containing panel.
- Use Swing's repaint coalescing. Do not introduce a game-loop renderer, direct canvas, or continuously repainted backing surface.
- Collection quantity is a tri-state integer contract: `-1` unknown, `0` authoritatively known absent, and positive values the authoritatively observed number of copies.
- Normal filters include color/color identity, base card type, and mana-value range. Semantic tags are categorized and apply as an additional filter layer.
- The filtered result produces a tag cloud with counts derived from that result before the tag layer is applied, so users can see useful refinements instead of only already-selected tags.
- Cards can be added to a persistent "Candidates" workspace.
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

**State:** complete

**Completion evidence date:** 2026-08-06

Human acceptance approval was recorded on 2026-08-06 after review of the filter-control and interaction-quality work.

Add appealing click-first controls for format, colors, base types, mana range, collection status, and categorized tags, with clear active states and fast reset/navigation.

Acceptance evidence:

- Filter state is a model independent of widgets and has deterministic unit tests.
- Rapid toggling, resizing, and scrolling do not queue stale result/image work or freeze the EDT.
- Empty, loading, partially cached, offline, and failed-catalog states have explicit UI treatments.
- Keyboard traversal and visible focus are supported; color is not the sole indication of state.

#### DP-06 — Candidate workspace

**State:** active

Persist an ordered set of candidate cards and expose an acceptance-testable deck-planning workspace without losing the active browser filters. The first automated implementation reached a green 220-test baseline, but human review on 2026-08-07 found that the acceptance surface still relied on synthetic cards and did not yet exercise the intended candidate interaction model. DP-06 therefore remains active for the following bounded rework pass.

Acceptance evidence:

- Membership survives restart and catalog refresh through stable identity mapping.
- Duplicate-printing behavior is defined and tested.
- Browser and candidate views remain synchronized, including overlays. Collection ownership/count presentation is omitted while `SA-MTGA-DEF-003` remains open; unknown ownership must not be replaced by invented values.
- Empty and stale/unresolvable candidate states remain recoverable.
- Existing Arena-exported deck lists can be imported into candidates without resetting active browser filters; quantities and duplicate printings collapse to one logical candidate, unresolved names are reported, and imported deck contents never imply collection ownership.
- A repository-owned human click-test harness exposes the complete DP-06 workspace before acceptance.
- Human acceptance is a required completion gate: after automated validation is green, the human runs the preview checklist and explicitly accepts DP-06 (or records observed defects). Automated tests alone do not close DP-06.

Human-feedback rework plan (2026-08-07), in implementation order:

1. **Real Standard preview data through the production catalog path.** Repurpose `DeckPlannerWorkspacePreview` to prime a bounded subset of current `game:arena legal:standard` cards through the existing Scryfall `CardCatalogSource` → `FormatCatalogService` → `FormatCatalogRepository` → `CatalogFilterIndex` path rather than manufacturing `Planner Card N` fixtures. Reuse the shared image-cache path for those real cards. Keep a clear offline/error presentation; do not silently substitute synthetic cards when the real preview fetch fails.

   Validation status (2026-08-07): complete for this rework step. The preview uses a bounded `CardCatalogSource` wrapper around `ScryfallClient`, persists/publishes through `FormatCatalogService` and `FormatCatalogRepository`, reuses a fresh completed Standard snapshot for 12 hours, falls back to an older completed snapshot only with explicit offline status when refresh fails, and shows an explicit no-synthetic-data error when neither network nor cache can supply real cards. Browser images resolve the same catalog identities through `CardImageCacheSource` and the shared `CardImageCache`. Human-side validation passed at commit `bcf61885455c5858a94876bf0f3b9e25b225b0bd`: 221 tests, zero failures/errors/skips.
2. **Shared replay-style candidate chips.** Extract or expose the card-chip presentation used by the replay view as a reusable component/painter and use that same renderer for resolved candidate entries. Candidate rows show card identity/presentation and stale state, but no owned-count text while collection ownership is deferred.

   Validation status (2026-08-07): complete for this rework step at commit `bc47a7bb6e8cf4cec85647b932ae798984e7c368`. `ReplayCardChip` is shared by replay and Deck Planner, stale rows remain explicit, ownership text is omitted, and the human-side suite passed 223 tests with zero failures/errors/skips.
3. **Manual ordering by drag and drop plus explicit MTG sort.** Replace Up/Down as the primary ordering gesture with drag-and-drop reorder that persists through `CandidateModel`. Keep manual order authoritative after a drag. Add a `Use normal MTG sorting` action that applies a shared Magic ordering primitive (type group → mana value → color → name), reusing/refactoring the existing Draft Assistant / Deck Tracker ordering logic rather than introducing another comparator.

   Validation status (2026-08-07): complete for this rework step at commit `f20a751b0f9820f1fc030fa3e669b4777baa9eee`. Candidate insertion-point moves persist through the ordered model, Draft and Deck Planner share `MagicCardOrdering`, and the human-side suite passed 226 tests with zero failures/errors/skips.
4. **Common name-to-card resolution and richer deck import.** Introduce one name-to-card repository used by deck import and future planner entry points. It first resolves against the current catalog/cache, handles exact Arena export printing hints where present, and may fall back to an exact-name Scryfall lookup. The Import Deck UI offers known Arena decks already observed by the deck subsystem as selectable sources in addition to pasted Arena-export text. Fallback metadata never implies collection ownership.

   Current implementation slice also includes the human-requested candidate-surface refactor: replace the `JList`/list-model presentation with a custom component panel inside the project-local `AppScrollBarUI` scroll surface while preserving the same authoritative ordered model. This deliberately creates room for later visual groupings (for example card-advantage / recursion / win-condition categories, mana-cost adornments, and other planner-specific grouping metadata) without another storage migration.

   Validation status (2026-08-07): complete for this rework step at commit `9a4878fe27335709ee9e963912a7d4c7b011612f`. The custom component candidate surface, project-local scrollbar, local-first `CardNameRepository`, exact-name Scryfall fallback, and read-only observed-Arena-deck adapter are committed on a clean tree; the human-side suite passed 228 tests with zero failures/errors/skips.
5. **Candidate filter layer.** Add a filter-panel control that restricts the catalog result to identities currently in the candidate set without destroying the normal structured/tag filter state. Selecting a resolved candidate automatically enables this temporary layer; disabling it restores the same normal filter state that was active before. Candidate membership changes update the layer immediately.

   Current implementation slice promotes the candidate container into a small shared `CardCollectionSurface` primitive that owns project-local scrolling, component-row selection, and insertion-point drag/drop while leaving card-specific rendering/grouping metadata to its caller. DP-06 uses that primitive first; no other workspace is migrated in this patch.
6. **Human click acceptance.** Update `DeckPlannerWorkspacePreview` and its visible checklist to exercise real Standard cards, replay-style chips, drag/drop persistence, normal MTG sorting, known-deck and pasted-text import, Scryfall-name fallback behavior, the candidate-only filter layer, stale-card recovery, resizing/scrolling, and restart persistence. DP-06 closes only after the full Maven suite is green and the human explicitly accepts this real-card preview.
   Current implementation slice turns the checklist into an interactive ten-check human acceptance surface covering every completed DP-06 rework behavior. Reaching 10/10 records only that the click checks were performed; the harness deliberately cannot close DP-06 or manufacture acceptance. The human must still report explicit acceptance or defects after repository-side validation is green.


Human-feedback iteration (2026-08-08):

- **Acceptance review is design review, not manual regression duplication.** The click harness should ask the human to assess UX/design categories and whether the implemented design still feels right with representative real data. Automated contracts such as Scryfall legality/source integrity remain automated evidence rather than checkbox prompts.
- **Persistent full Standard cache.** The preview should prefetch the full Arena-available Standard catalog through the same persistent application cache roots (`format-catalog`, `card-cache`, and images) rather than a bounded `target/`-local metadata cache. More real cards are intentionally part of the human design surface.
- **Local-first import and polite Scryfall fallback.** Deck import must search the persistent played-card cache before exact-name Scryfall fallback. Scryfall access is globally throttled, and HTTP 429 handling honors `Retry-After` with bounded backoff rather than issuing a burst of independent exact-name requests.
- **Category workspace rather than list-shaped surface.** Default candidate presentation groups cards into Creatures, Noncreatures, and Nonbasic Lands, wraps cards without horizontal overflow, uses larger scalable replay chips, and shows a visible insertion marker during drag/drop. Stale cards remain a recoverable special group.
- **High-quality scalable chip rendering.** Replay card chips remain shared between replay and planner, but their rendering must scale without raster-text artifacts; text is drawn through glyph vectors with high-quality antialiasing/fractional metrics.
- **This pass does not expand filter taxonomy.** Tribe/subtype tag resolution is explicitly deferred as `SA-MTGA-DEF-004`; it will require a dedicated long-list interaction/taxonomy design.

Human-feedback iteration 2 plan (2026-08-08), in implementation order:

1. **Candidate vocabulary and visual/layout foundation.** Complete the active DP-06 vocabulary migration to **Candidates** across current code and documentation while preserving historical handoff evidence. Fix wrapped category layout so every card and header count remains visible after resize/update. Candidate selection uses a precise replay-chip outline rather than a rectangular row highlight. Increase catalog card side length by 25%, use the shared MTG ordering for catalog presentation, and restore the dark-mode golden selection outline.
2. **Editable candidate categories and named Candidate Sets.** Persist category order and membership; add/remove/reorder categories, move cards from a removed category into an implicit `Uncategorized` category that disappears while empty, and expose an inline add-category control below populated groups. Add save/load for named Candidate Sets without conflating a saved set with Arena deck ownership.

   Current implementation slice: category order and explicit card-to-category assignments are persisted independently from candidate membership; removing a category preserves its current cards by assigning them to implicit `Uncategorized`; empty `Uncategorized` is not rendered. Category headers expose compact move-up/move-down/remove controls and the candidate surface exposes an inline circular `+` category control. Named Candidate Sets persist and restore ordered candidate membership together with the category snapshot; they remain planner artifacts and do not imply Arena ownership.
3. **Multi-selection and cross-surface drag/drop.** Extend the candidate surface to Windows-style multi-selection, drag multiple selected candidates together, accept catalog-card drops into candidate categories, and use a scaled replay-chip drag image with insertion/category feedback.
4. **Alternate-art/name resolution and legality visibility.** Make logical-card resolution expose alternate Arena/Scryfall printings, collector-visible favorite art selection, and an explicit Illegal indicator when a resolved card exists but is outside the selected legal format. This must preserve logical candidate identity and must not infer ownership.

Filtering/tag-taxonomy rework remains outside these slices; subtype/tribal handling stays deferred as `SA-MTGA-DEF-004`.



#### DP-07 — Authoritative AI deck-building protocol

**State:** planned

Create `MTGA_DECK_BUILD_REQUEST_V1`, following the established authoritative/provenance discipline of game reconstruction exports while using a deck-planning-specific schema.

Acceptance evidence:

- Payload includes target format, candidate membership, collection quantity with unknown distinguished from zero, stable identities, name, mana cost/value, type line, complete oracle text, colors/color identity, power/toughness/loyalty/defense when applicable, keywords, produced mana, and every relevant face of multi-face cards.
- Repeated values use a compact alias/table mechanism where it saves tokens without making rules ambiguous.
- Export is deterministic, escapes delimiters/newlines, states that supplied card data is authoritative, forbids substitution/invented rules, and separates observed facts from strategic inference.
- Golden tests cover ordinary, token-producing, split/adventure, transform/modal, and metadata-incomplete cards plus the `-1/0/positive` collection states.
- The final request text is exactly `What deck would you build with these cards?`

#### DP-08 — Integration, performance, and release evidence

**State:** planned

Wire navigation/startup lifecycle, background service shutdown, persistence migration, and end-to-end validation.

Acceptance evidence:

- Full Maven tests pass on the supported JDK.
- Integration fixture demonstrates format selection → resumable catalog population → filtering/tag refinement → candidates → deterministic AI export.
- Performance evidence records EDT responsiveness, time to first usable catalog view, scroll behavior with a full target-format catalog, image-cache hit behavior, and bounded memory/cache growth.
- Human visual validation covers normal interaction and the required loading/offline/error states.

### Active item

`DP-06 — Candidate workspace` remains active. Candidate vocabulary/layout/presentation is validated at the clean 244-test baseline `a509dcca9f3aa810cd91ff31e2155f90fc94870a`. The active implementation slice is editable persisted categories plus named Candidate Sets. After repository-side validation, continue to the planned multi-selection/cross-surface drag/drop slice; do not start DP-07 until DP-06 is explicitly accepted.

### Current planning decisions

- DP-01, DP-03, DP-04, and DP-05 are complete. DP-02 contracts are implemented; live ownership integration is deferred because the current client does not publish authoritative collection quantities in `Player.log`. DP-06 remains active after a clean 233-test acceptance-harness baseline at `db57fa3610ced5ba09e9fbb28e5c7cc8054fdf2f`; real-card human review on 2026-08-08 opened another bounded UX/cache/import iteration rather than accepting the item. Ownership-dependent candidate displays are omitted while `SA-MTGA-DEF-003` remains open.
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
