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
