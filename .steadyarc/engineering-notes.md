# Steady Arc Engineering Notes

## Durable repository facts

- MTGArenaLogReader is a Maven-based Java 21 desktop application using Swing, Gson, H2, Unirest, and JUnit 5.
- The runtime entry point is `app.application.Application`.
- Arena-observed log data is authoritative for gameplay and account state. Scryfall is optional enrichment and may supply card rules, legality, identifiers, and images, but may not overwrite Arena observations.
- Existing architecture documentation in `README.md` and `docs/architecture/` remains authoritative for replay/game semantics.

## Existing Deck Planner foundations

- `app.enrichment.ScryfallClient` already supports Arena-ID, exact-name, Scryfall-ID, set catalog, and paginated `game:arena` set searches. Format catalog ingestion should generalize its paging/query/throttling boundary.
- `app.enrichment.CardEnrichmentService` is the shared cache-freshness, throttling, Arena-ID, and related-token boundary used by `InformationCollector` and bulk catalog ingestion. Catalog work must call it directly rather than enqueue synthetic log messages.
- `app.enrichment.CardCache` persists positive and negative Arena-ID lookups as full `CardInfo` JSON in H2. Catalog snapshot semantics and identity deduplication are new concerns and should not be hidden inside the message cache without an explicit schema contract.
- `app.enrichment.CardImageCache` already provides asynchronous memory/disk image loading. Deck Planner work should extend it with viewport priority/cancellation, bounded decoded-image retention, target-size variants, and EDT-safe completion rather than create an unrelated downloader.
- `app.model.card.CardInfo` and face/image models already carry the core Scryfall fields used by replay and draft export.
- `app.ui.AsyncVirtualListPanel` demonstrates viewport indexing, generation cancellation, EDT confinement, and overlay painting. It renders rows to custom back buffers, so it is a reference for scheduling—not the Deck Planner's Swing-component card rendering model.
- `app.draft.export.DraftAiExporter` and `app.export.MatchAiExporter` demonstrate authoritative-data warnings, deterministic text protocols, complete face/rules fields, compact aliases, and golden-testable output.
- The original log fixtures contain `InventoryInfo` and deck responses but no owned-card snapshot. DP-02 now models complete collection observations separately; deck membership remains non-authoritative for ownership.
- Owned-card snapshots historically use `PlayerInventory.GetPlayerCardsV3`; current tracker implementations also recognize a bare non-empty JSON map of positive Arena ID keys to positive integer counts. The exact current wire shape must be confirmed against this user's log before DP-02 is closed.
- Collection persistence stores only positive entries from a recognized complete snapshot. Absence is `0` only within that snapshot's identity domain; with no snapshot—or when a consumer's freshness window expires—the quantity is `-1`. Unsupported deltas never update the complete snapshot.
- `app.deckplanner.catalog.FormatCatalogService` processes Scryfall pages and cards sequentially with cancellation; `FormatCatalogRepository` stages resumable H2 runs and atomically changes the current snapshot only after completion. Per-card enrichment outcomes are retained with the snapshot.

## Deck Planner architectural decisions

### Catalog and truth boundaries

- A target-format catalog entry must satisfy both Arena availability and Scryfall legality for the selected format. The Scryfall query should express both predicates and the stored snapshot must retain the actual normalized format.
- Bulk runs are resumable and publish a new complete snapshot atomically. An interrupted or partially failed refresh must not replace the last complete usable catalog.
- Network requests are rate-limited and cancellable. Sequential enrichment means one authoritative card-processing sequence, not EDT execution and not uncontrolled parallel HTTP fan-out.
- Printing identity, oracle/card identity, and Arena ID are distinct. Deduplication policy must be explicit because alternate Arena printings can share rules while ownership and images remain printing-specific.

### Collection quantities

- Collection quantities use a signed integer value with provenance: `-1` means unknown, `0` means known absent from a complete authoritative observation, and `n > 0` means the observed owned count.
- Missing keys in an incomplete response remain unknown. Code may produce zero only after identifying and parsing a complete collection snapshot for the same identity domain.
- Scryfall, deck lists, and inferred craftability cannot change collection quantities.

### UI and responsiveness

- The Deck Planner uses Swing component card views managed by a responsive planner panel/layout model. It does not use a canvas or game-loop renderer.
- The planner panel owns card bounds, hit testing, selection, hover, keyboard focus, and scroll anchoring. Transient states are painted as overlays by the panel so cached images remain immutable.
- Swing repaint coalescing is the frame scheduler. Repaint calls should be region-scoped when an image/state affects one card and batched naturally by `RepaintManager`.
- Image network I/O, disk I/O, decoding, scaling, filtering, tag counting, and catalog refresh stay off the EDT. Component state changes and repaint requests return to the EDT.
- Viewport awareness includes a small prefetch margin and generation/priority cancellation. Scrolling must not cause the entire filtered catalog to decode into memory.

### Filters and tags

- Structured filters and semantic tags are separate layers. The tag cloud is derived after normal structured filters but before selected tag filters, making it a useful refinement surface.
- Tag extraction is deterministic and versioned. Tags belong to categories (for example keyword, zone, action/mechanic, and compound predicate); they are not an unstructured bag of raw substrings.
- Compound tags such as `all creatures` are semantic predicates over normalized rules/type information and require focused tests.

### Consideration and AI export

- "Under consideration" is durable user intent and uses stable identity mapping across catalog refreshes.
- The deck-planning AI export is a versioned protocol distinct from match reconstruction, but it follows the same authoritative-data and deterministic-escaping principles.
- The protocol must carry complete functional card rules, including all relevant faces, while using aliases/tables only when they reduce tokens without ambiguity.
- Unknown collection quantity must remain distinguishable from known zero in the payload.

## Steady Arc history

- Initial Steady Arc bootstrap work completed on 2026-07-31. Its returned handoff is preserved under `.steadyarc/handoff-history/SA-MTGA-BOOTSTRAP-001.md`.
- Managed Steady Arc tools, knowledge documents, and the generic `devtools` support relay were upgraded from PlaneGuardianAssets on 2026-08-06.

## 2026-08-06 — Current Arena collection logging limitation

- Current production client `2026.61.30.13636` did not publish `PlayerInventory.GetPlayerCardsV3` or a complete bare numeric ownership map in detailed `Player.log` captures after Collection browsing, Deck Builder entry, arbitrary owned/unowned card additions, and deck save.
- Deck save publishes a full `DeckUpsertDeckV3` definition, including unowned cards, so it is explicit negative evidence for using deck membership as ownership.
- Ownership-dependent Deck Planner behavior is deferred under `SA-MTGA-DEF-003`; catalog filtering and semantic indexing continue independently with collection quantity unknown.

### DP-03 filter semantics

- Structured filter groups combine with AND semantics. Base-type selections are OR within the base-type group because a multi-face card can satisfy any selected base type.
- Selected semantic tags are an additional global AND layer. Multiple selections from the same tag category are also AND; categories do not imply an OR shortcut.
- Deck Planner mana-value filtering uses Scryfall's top-level `cmc`, preserving fractional values. Scryfall's top-level value is treated as the layout-aware value for split, adventure, transform, and modal cards. Missing or invalid values normalize to zero, including lands whose payload omits `cmc`.


### DP-04 responsive browser foundation

- Responsive card placement is specified by a pure layout model so resize, hit testing, and scroll-anchor calculations can be tested without constructing a Swing frame.
- Image request selection is likewise a side-effect-free viewport contract. Visible cards are mandatory; only a small directional prefetch margin may be added. Network/disk/decode execution remains delegated to the asynchronous image cache.


## 2026-08-06 â DP-04 Swing browser panel

- `CardBrowserPanel` is EDT-confined for model mutation and Swing state changes.
- Image sources return `CompletableFuture<Optional<BufferedImage>>`; completion is marshalled back to the EDT and repaints only the affected card bounds.
- The panel delegates responsive geometry and image-window selection to `CardGridLayout` and `ViewportImageWindow`.
- Cached images are never mutated for hover, selection, or focus; overlays are painted by the containing panel.


### Browser request lifecycle

- `CardBrowserPanel` treats the current viewport window as the authoritative decoded-image demand set. Pending futures outside that set are cancelled, and completions that race after cancellation are ignored rather than cached or painted.
- Browser selection and keyboard focus are logical card-identity state, not row/index state. Reordering or narrowing a result set remaps retained identities to their new indices.

## DP-04 image-source and fixture evidence

- The card browser resolves stable planning identities to enriched `CardInfo` before crossing the shared `CardImageCache` boundary; the panel itself remains independent of catalog/cache implementation details.
- `DeckPlannerCardBrowserFixtures` writes deterministic 360x640, 760x640, and 1280x640 PNGs under `target/rendered-fixtures/deck-planner` for human responsive-layout review; generated evidence is not committed.


## DP-04 viewport coordination and scroll anchoring

- `CardBrowserScrollPane` owns viewport change notifications instead of requiring callers to manually forward every scroll event.
- Scroll position is represented by stable card identity plus a vertical pixel offset, then resolved against the current responsive layout. This preserves the logical top card across width changes and filtered-result replacement when that card remains present.
- Anchor restoration and viewport-driven image scheduling are EDT-confined; missing anchors degrade to the current viewport rather than guessing a replacement identity.


## 2026-08-06 — Deck Planner reusable card views

- The responsive browser uses a reusable `CardView` Swing component rendered through `CellRendererPane`; it does not create one heavyweight child hierarchy per catalog entry.
- `CardBrowserPanel` remains authoritative for responsive bounds, viewport materialization, hit testing, focus, selection, and repaint regions.
- `CardView` receives immutable display state immediately before paint. Cached `BufferedImage` instances are drawn as inputs only; interaction overlays are painted afterward and never written into the cache image.


## 2026-08-06 — DP-04 human review harness

- `DeckPlannerCardBrowserPreview` is a standalone devtools surface, not production navigation. It exercises responsive resize, viewport scheduling, delayed image arrival, scrolling, mouse selection, and keyboard focus/selection.
- `PreviewDeckPlannerCardBrowser.ps1` is the supported human-session entry point; review criteria are recorded in `docs/guides/deck-planner-card-browser-review.md`.


## 2026-08-06 — DP-04 human review harness

- `DeckPlannerCardBrowserPreview` is a standalone devtools surface, not production navigation. It exercises responsive resize, viewport scheduling, delayed image arrival, scrolling, mouse selection, and keyboard focus/selection.
- `PreviewDeckPlannerCardBrowser.ps1` is the supported human-session entry point; review criteria are recorded in `docs/guides/deck-planner-card-browser-review.md`.

### 2026-08-06 — DP-04 human review corrections
- Card browser renderer surfaces now clear their paint clip explicitly using the active semantic `Viewport.background`/`App.surface` color, preventing stale rows after resize and result replacement.
- `CardBrowserScrollPane` installs the shared `AppScrollBarUI` on both axes and applies semantic scroll-pane/viewport colors even in standalone review harnesses.
- The standard readable card layout is 220–320 px wide with the existing 63:88 aspect ratio; narrow windows use one centered card rather than shrinking below readable width.

## 2026-08-06 — DP-04 multi-select and consideration badges

- Browser selection is an ordered identity set. Mouse click and keyboard Space toggle one card without clearing prior selections; selection survives reorder/filter replacement for identities that remain present.
- The browser accepts a separate identity set for under-consideration membership. It is presentation state only in DP-04; persistence and workspace mutation remain DP-06.
- Selection is rendered as a bottom-center theme-aware chip with `/svg/tap.svg` and the text `selected`, replacing the full-card selection outline.
- Under-consideration membership is rendered as a circular theme-aware top-right badge using the general MTG `/svg/chaos.svg` symbol.

## 2026-08-06 — DP-04 standard desktop selection and consideration gestures

- Plain click/Space replaces selection, Ctrl toggles one card, Shift replaces selection with the contiguous anchor range, and Ctrl+Shift adds that range.
- Selection and range anchors remain identity-aware across result replacement; browser selection is still transient DP-04 state.
- Double-clicking a card adds it to the under-consideration set without changing selection. Double-clicking a selected chip adds every selected card. Clicking a visible consideration badge removes that card.
- Badge hit targets are derived from the same geometry used for painting so interaction and rendering cannot drift apart.
- The selected chip is a larger bottom-edge badge using neutral gray at 80% opacity; the circular consideration badge remains anchored to the top-right edge.

## DP-05 asynchronous filter coordination

- Expensive filtering and tag-count work is restartable and runs off the EDT.
- Each interaction change increments a generation, cancels scheduled/running work where possible, and suppresses stale completions even when cancellation cannot interrupt the underlying computation.
- Result listeners are always delivered on the EDT.
- Availability is independent of filtering outcome: READY, PARTIAL_CACHE, and OFFLINE can accompany loading/content/empty/failure states.
- Explicit empty, loading, offline, partial-cache, and failed-result treatments are reusable and theme-aware.

## DP-05 workspace composition

`DeckPlannerWorkspace` is the integration boundary for the filter model, asynchronous coordinator, state treatment, and card browser. It accepts an already-built immutable `CatalogFilterIndex` and caller-owned executors; it does not fetch catalogs or own application navigation. Result cards use catalog planning identity and preferred-printing name. Tag-chip counts come from the coordinator's pre-tag-layer cloud and therefore remain useful refinement counts while tags are selected.
