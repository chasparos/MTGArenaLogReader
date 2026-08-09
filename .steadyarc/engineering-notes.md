# Steady Arc Engineering Notes

## Durable repository facts

- MTGArenaLogReader is a Maven-based Java 21 desktop application using Swing, Gson, H2, Unirest, and JUnit 5.
- The runtime entry point is `app.application.Application`.
- Arena-observed log data is authoritative for gameplay and account state. Scryfall is optional enrichment and may supply card rules, legality, identifiers, and images, but may not overwrite Arena observations.
- Existing architecture documentation in `README.md` and `docs/architecture/` remains authoritative for replay/game semantics.
- The Memory-Scan Collection Extraction module exposes batch `CollectionOwnership.getCopiesOwned(ids)` plus the neutral `CollectionUpdate` session conversation. JNA, Windows handles, process/region models, scan heuristics, diagnostics, output evidence, provider persistence, and anchor terminology remain internal.
- The main application owns synchronization UI language and visuals. Protocol card options carry stable card/set/collector identity; the UI may resolve set icons and other presentation without making the provider depend on application catalogs or image services.
- Provider-owned verified-card state is separate from ownership publication. Quantity-four cards are preferred for retries because ordinary Arena ownership is monotonic, but every stored card/quantity remains a hypothesis that must match current memory; no stored value bypasses consensus.
- Cancellation is a publication barrier: the worker is interrupted and cancellation is checked after scanning and immediately before transaction entry. Only one terminal session event is emitted.
- Memory-derived ownership uses a separate atomic H2 ID→copies table. It must not reuse or join the log-observation collection schema, and incomplete/failed scans must not replace the last complete table.
- MSC-02 Win32 evidence: `VirtualQueryEx` requires a process handle with query rights and returns homogeneous virtual-memory regions; the implementation opens `MTGA.exe` with query/read rights and closes every acquired handle in `finally`. Region inventory deliberately performs no collection extraction and cannot publish ownership.
- The approved `NthPhantom10/MTGA-collection-exporter` reference reports that collection discoverability may depend on visiting/scrolling Arena's Collection or Decks UI and that access permissions can fail. Preserve these as harness diagnostics/evidence questions, not timeless extraction assumptions.
- Reference inspection for MSC-03 found little-endian unsigned `(arenaId, quantity)` pairs, anchor-pattern search, bounded windows around hits, candidate extraction at 8/12/16-byte strides, and scoring by known-ID ratio, exact anchors, anchor IDs, and block size. Our implementation must be independently structured, fixture-driven, bounded, and stricter about ambiguity; the reference is MIT-licensed research evidence.
- MSC-03's pure extractor now enforces fail-closed size, known-ID, exact-anchor, duplicate-conflict, and distinct-map ambiguity gates. Candidate evidence must remain internal and harness-facing; this component has no authority to read process memory or publish the ownership table.
- Native memory reads are exact and bounded to one previously inventoried committed-readable region, with an 8 MiB hard ceiling. A partial `ReadProcessMemory` result is failure, never a truncated candidate fixture.
- Scanner confidence inputs are explicit evidence: a scanner-owned JSON catalog records its client/catalog identity and Arena-ID domain; the human confirms at least two owned ID/copy anchors. Configuration validation occurs before process acquisition and cannot consult application repositories.
- Current Arena `0.1.13636.1303683` uses Unity Mono, not IL2CPP. Managed symbols support investigating `ClientPlayerInventory` / `_cardInventory` / `GetPlayerCards` and possible `Dictionary<int,int>`-like storage, while the observed/reference 8/12/16-byte pair layouts remain a separate projection/backing-storage hypothesis.
- The authoritative scanner-known ID domain comes from Arena's own read-only `Raw_CardDatabase_*.mtga` SQLite `Cards.GrpId` values. The producer emits no names or application data, and atomically writes a versioned JSON document for the harness.
- Real anchor discovery traverses only committed writable `MEM_PRIVATE` regions, searches every configured 8-byte little-endian anchor in one pass, and overlaps 1 MiB chunks by seven bytes. Hit evidence has no publication authority; candidate-window extraction remains a separate next step.
- Anchor hits are clustered only within the same inventoried region, with an 8 MiB maximum candidate window. Candidate selection can report accepted evidence but the scanner deliberately returns an incomplete result until MSC-04 publication gates are designed and approved.
- Multiple individually accepted windows require authoritative-domain consensus. Real evidence found unequal raw 3,337- and 3,322-entry maps; the scanner compares their known-ID projections and preserves raw differences as diagnostics rather than treating unknown boundary pairs as ownership.
- Raw plausible pairs outside the current Arena-local `Cards.GrpId` domain are boundary/noise evidence, not ownership. Global consensus is evaluated on known-ID projections; raw and known-domain differences remain visible independently so normalization cannot conceal a real quantity conflict.
- MSC-04 publication requires at least two independent accepted windows with identical non-empty known-domain maps. The scanner alone decides completeness; the service atomically replaces only complete results and leaves the previous table untouched for rejected, ambiguous, cancelled, exceptional, or invalid results.
- Known-domain structural consensus is necessary but not sufficient for ownership publication. Quantity semantics were subsequently established by an evidence-only before/after pack-opening experiment: the baseline contained 3,322 positive entries; the new generation contained 3,325; exactly three pack cards were marked First; six quantities changed; and fifth-copy cards did not exceed four. Publication additionally requires exact user confirmations, quantities 1..4, and independently supported uniquely monotonic generation consensus.
- The accepted production baseline is Arena client `2026.61.30.13636` / Unity player `0.1.13636.1303683`, catalog `Raw_CardDatabase_7bc4fb29468604399aa7f1c7afb07405.mtga` with 26,126 known IDs. Future drift procedure and recovery guidance live in `docs/guides/memory-collection-sync-maintenance.md`.
- The Memory-Scan Collection Extraction arc closed with MSC-01 through MSC-06 complete, 338 automated tests green, and human approval of the production synchronization flow.

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

### Candidates and AI export

- "Candidates" is durable user intent and uses stable identity mapping across catalog refreshes.
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

## 2026-08-06 — DP-04 multi-select and candidate badges

- Browser selection is an ordered identity set. Mouse click and keyboard Space toggle one card without clearing prior selections; selection survives reorder/filter replacement for identities that remain present.
- The browser accepts a separate identity set for candidate membership. It is presentation state only in DP-04; persistence and workspace mutation remain DP-06.
- Selection is rendered as a bottom-center theme-aware chip with `/svg/tap.svg` and the text `selected`, replacing the full-card selection outline.
- Candidate membership is rendered as a circular theme-aware top-right badge using the general MTG `/svg/chaos.svg` symbol.

## 2026-08-06 — DP-04 standard desktop selection and candidate gestures

- Plain click/Space replaces selection, Ctrl toggles one card, Shift replaces selection with the contiguous anchor range, and Ctrl+Shift adds that range.
- Selection and range anchors remain identity-aware across result replacement; browser selection is still transient DP-04 state.
- Double-clicking a card adds it to the candidate set without changing selection. Double-clicking a selected chip adds every selected card. Clicking a visible candidate badge removes that card.
- Badge hit targets are derived from the same geometry used for painting so interaction and rendering cannot drift apart.
- Selected catalog cards use the same rounded card-edge geometry as hover/focus, with a golden-yellow dark-mode outline; the circular candidate badge remains anchored to the top-right edge.

## DP-05 asynchronous filter coordination

- Expensive filtering and tag-count work is restartable and runs off the EDT.
- Each interaction change increments a generation, cancels scheduled/running work where possible, and suppresses stale completions even when cancellation cannot interrupt the underlying computation.
- Result listeners are always delivered on the EDT.
- Availability is independent of filtering outcome: READY, PARTIAL_CACHE, and OFFLINE can accompany loading/content/empty/failure states.
- Explicit empty, loading, offline, partial-cache, and failed-result treatments are reusable and theme-aware.

## DP-05 workspace composition

`DeckPlannerWorkspace` is the integration boundary for the filter model, asynchronous coordinator, state treatment, and card browser. It accepts an already-built immutable `CatalogFilterIndex` and caller-owned executors; it does not fetch catalogs or own application navigation. Result cards use catalog planning identity and preferred-printing name. Tag-chip counts are faceted against the active selected-tag layer, so every tag activation immediately recounts the remaining refinements against the narrowed result set.

## 2026-08-06 — DP-05 workspace review harness

The DP-05 interaction pass uses `PreviewDeckPlannerWorkspace.ps1`. The harness owns only synthetic catalog data and simulated image latency; it exercises the production `DeckPlannerWorkspace`, filter model, coordinator, result states, tag counts, browser, and theme controls without introducing production navigation or persistence.

## 2026-08-06 — DP-05 filter-control human-review corrections

- The filter column uses a wrapping layout and tracks viewport width so controls add rows instead of clipping horizontally.
- Filter chips have compact fixed geometry; selected state is painted independently of label text, and live tag counts are painted as stable count pills so rollover cannot erase them.
- Color/type/tag controls use bundled MTG SVG symbols and theme-semantic surfaces; color controls also use recognizable W/U/B/R/G accents.
- Mana value uses a dual-handle discrete 0 through 7+ control. The full 0–7+ span maps to no mana filter, while a 7+ upper bound maps to the model's open high range.
- Tag-cloud counts are faceted against the currently selected tag layer. Selecting a tag therefore immediately recounts every remaining refinement against the narrowed result set.

### 2026-08-06 — DP-05 filter-control refinement
- Returned the filter rail to a compact 350 px workspace width; wrapping controls create additional rows instead of clipping.
- Filter chips now size once from their own label/icon while reserving permanent selection-mark space and optional count space.
- Restored visible check-mark selection state, softened inline tag counts, and added tag-list text filtering for large future tag vocabularies.
- Color controls now include W/U/B/R/G, colorless, and a separate Phyrexian-mana refinement derived from card/face mana costs; Phyrexian remains a semantic mana-symbol filter, not a color.


### 2026-08-07 — DP-05 loading and scroll stability

- Once content has been published, filter refresh uses only the fixed status-strip progress indicator; the hidden centered state-panel indicator is deactivated so it cannot animate/repaint behind the browser.
- Planner vertical scroll panes reserve the narrow custom-scrollbar gutter and disable the thumb when scrolling is unnecessary. This avoids scrollbar-width/layout feedback loops without presenting an active scrollbar when content fits.
- Color-source and match-mode selectors use the same explicit selected-chip treatment as other filters so chosen semantics are visible without relying on native radio rendering.

### 2026-08-07 — DP-05 color-semantics radio polish

Human review preferred the lighter unframed color-semantics row beneath the color selectors. The final treatment restores radio-button visuals with a theme-aware hollow/filled bullet so the active source and match mode remain explicit without competing visually with the color chips.


## 2026-08-07 — DP-06 candidate workspace state

- The authoritative candidate set is ordered and keyed by the Deck Planner logical catalog identity, not by printing-specific Arena or Scryfall IDs. Alternate printings grouped under the same logical identity therefore occupy one candidate position.
- Candidate persistence retains unresolved identities across catalog refreshes. A temporarily missing identity is rendered as a recoverable stale candidate instead of being deleted; when the identity returns in a later catalog snapshot it resolves to that snapshot's preferred printing.
- Browser candidate badges are a projection of the persistent workspace onto the currently filtered browser result. Filtering a card out of the browser must not remove it from the candidate workspace.
- Candidate presentation does not show collection quantity while `SA-MTGA-DEF-003` remains open. The underlying collection contract remains `-1` unknown / `0` known absent / positive-owned, and candidate persistence writes remain injectable through an executor so disk I/O can stay off the Swing EDT.
### DP-06 existing-deck import

- Arena-exported deck text is an input convenience for the candidate set, not an ownership source or authoritative playable-deck model.
- Import resolves card names against every printing in the current format catalog, then stores the catalog group's stable logical identity. Quantities, repeated lines, and alternate printings therefore collapse to one candidate while preserving first appearance order.
- Main deck, sideboard, commander, and companion card lines are accepted. Unresolved names remain explicit import feedback rather than becoming fabricated/stale identities.
- Import is additive and does not reset active browser filters. Browser badges remain a visible-result projection of the durable candidate set.

### 2026-08-07 — Shared card collection surface and candidate filter layer

- `app.ui.CardCollectionSurface` is the reusable component-row primitive for small ordered card collections that need project-local scrolling, selection, and insertion-point drag/drop. It deliberately does not own card-specific rendering, planner persistence, or semantic grouping policy; callers supply ordinary Swing row components and may later add group headers/metadata without a `JList` cell-renderer constraint.
- Deck Planner's candidate-only catalog layer is orthogonal to normal structured/tag filters. Enabling or disabling it never rewrites those filters; it intersects the normal result with the current authoritative candidate identities.
- Selecting a resolved candidate may activate the candidate-only layer as a temporary browsing aid. Stale candidates do not activate it.
- Candidate membership changes are propagated into the active layer immediately; the browser remains a filtered projection and never becomes the authority for candidate membership.
