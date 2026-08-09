# Steady Arc Roadmap

## Current arc

- **Arc identifier:** Deck Planner Phase 2 — Analysis and Filtering
- **Arc type:** product integration / filtering / graph-analysis design
- **Area in scope:** Deck Planner ownership presentation and filters, repair and completion of existing filter controls (especially color), followed by collaborative definition of a mechanic/interaction/soft-card-type relation graph and its searchable index.
- **Completion criteria:** To be defined during DPP2-01 after the ownership/filter foundation is reviewed.

### Ordered items

#### DPP2-00 — Ownership data and filter foundation

**State:** planned

Wire the completed `CollectionOwnership` batch API into Deck Planner without exposing scanner implementation details. Display copies owned beside cards, add the wizard-style owned-copy control to filtering, repair incomplete or misbehaving current filters, and give the color filter a focused behavior/design pass. Begin with fresh click-review and user-evidence collection before making concrete UI tuning plans.

#### DPP2-01 — Analysis, relation graph, and indexing definition

**State:** planned — definition requires human discussion

Collaboratively define the intended card-analysis model before implementation: mechanics and interactions, soft card types such as removal and ramp, relation/synergy edges, indexing, and the search/filter experiences the graph should enable. Do not prematurely freeze the schema or implementation plan; this item begins with explanation, examples, and iterative requirements capture.

## Completed arc — Memory-Scan Collection Extraction

- **Arc identifier:** Memory-Scan Collection Extraction
- **Arc type:** Windows-specific research / isolated prototype / persistence
- **Area in scope:** a new isolated collection-memory module, its Windows/JNA process-access implementation, a module-owned H2 ownership table, a dedicated scan test-harness UI, focused deterministic tests, and the two-operation application port.
- **Completion criteria:** MSC-01 through MSC-06 reach `complete` or an explicitly named `implemented; <X> deferred` state. A human can attempt a scan from the harness, follow progress in a message log, inspect raw/structured output, and verify atomic ownership publication. The rest of the application depends only on `getCopiesOwned(id)` and `attemptRealCollectionUpdate()` and has no dependency on JNA, process handles, memory layout, scan diagnostics, or scanner persistence types.

### Mission

Research and prototype a manual full-collection synchronization path by reading the running Windows MTG Arena client process. Treat the capability as unusually platform- and client-version-sensitive. Keep it more isolated than log ingestion: it owns its Windows access, memory-layout heuristics, diagnostics, test harness, and persistence schema behind a narrow application port.

### Accepted constraints

- Windows and MTG Arena client-version dependence are intrinsic to this module and must not leak into other application packages.
- Begin with a dedicated test-harness UI, not production integration. It has an **Attempt scan** button, an append-only progress-message log, and a separate output area.
- Progress vocabulary begins with observable milestones such as `Scan started` and `Arena client process acquired`; messages are diagnostic evidence, not the application API.
- The module owns a separate H2 collection-ownership table containing only Arena ID to known owned-copy count. Do not reuse, extend, join, or depend on the log-observation collection tables.
- The only application-facing API is `getCopiesOwned(id)` and `attemptRealCollectionUpdate()`. Scanner configuration, process discovery, regions, byte patterns, candidate scoring, diagnostics, output models, and repository types remain internal.
- A successful scan is published atomically. A failed, cancelled, partial, or low-confidence scan must not replace the last known ownership table.
- Before implementing memory-layout heuristics, inspect the approved reference implementation and current MTG Arena process behavior. Reference code is research evidence, not a runtime dependency.
- Do not infer that a missing ID means zero until one complete scan has been accepted and atomically published. Before that, ownership remains unknown.

### Ordered items

#### MSC-01 — Isolated boundary and scan-harness skeleton

**State:** complete

Create the module/package boundary, the two-operation application port, internal scan-result/progress contracts, separate H2 repository schema, and a standalone Windows-oriented harness with Attempt scan, message log, and output area. Use a deterministic fake scanner first; do not access another process yet.

Acceptance evidence:

- Package/dependency tests show application consumers see only the two-operation port.
- Harness click review confirms the control, progress log, output area, disabled/running state, and retry behavior are useful before scanner mechanics are planned in detail.
- Repository tests prove unknown-before-first-publish, known-zero after a complete publish, positive counts, and atomic replacement.

Implementation evidence (2026-08-09):

- `app.collection.CollectionOwnership` declares exactly the approved `getCopiesOwned(id)` and `attemptRealCollectionUpdate()` operations; reflection coverage prevents accidental API growth.
- `app.collection.memory` owns the scanner seam, asynchronous service, and independent `memory_collection_owned` / publication-state H2 schema without importing log-observation collection types.
- `devtools.MemoryCollectionScanHarness` provides Attempt scan, an append-only timestamped progress log, separate output, running-state button disablement, retry, and explicit simulated labeling.
- The fake scanner publishes deterministic progress/output and an atomic complete table; validation passes 305 tests with zero failures, errors, or skips.
- Human click review approved the harness interaction and authorized MSC-02.

#### MSC-02 — Windows process acquisition and readable-region inventory

**State:** complete

Implement internal JNA bindings for process enumeration/acquisition, readable-region discovery, bounded reads, handle cleanup, cancellation, and diagnostics. Inventory the current client without attempting collection extraction.

Acceptance evidence:

- Harness reports process acquisition and region inventory progress without freezing the EDT.
- Handles are closed on success, failure, cancellation, and harness shutdown.
- Unsupported OS, Arena-not-running, access-denied, and client-exit cases are explicit results and never mutate ownership.

Implementation evidence (2026-08-09):

- Internal JNA bindings acquire `MTGA.exe` with query/read rights, enumerate virtual regions with `VirtualQueryEx`, classify committed readable/non-guard pages, and close the process handle in a `finally` boundary.
- The harness now runs real Windows region inventory and reports process acquisition, region totals/readable bytes, explicit `collectionExtraction=NOT_ATTEMPTED`, failure details, and handle closure without publishing ownership.
- Deterministic tests cover readable filtering, Arena-not-running, inventory failure, and handle cleanup; full validation passes 308 tests with zero failures/errors/skips.
- Human real-client review acquired PID 185456, inventoried 7,534 regions / 6,482 committed / 4,334 readable / 2,958,073,856 readable bytes, confirmed handle closure and no publication, and approved the result.

#### MSC-03 — Candidate collection extraction and confidence evidence

**State:** complete

Research current-client memory representation and implement bounded candidate discovery/scoring. Preserve evidence explaining why a candidate was accepted or rejected. Do not hard-code one unversioned layout as timeless truth.

Acceptance evidence:

- Synthetic memory fixtures cover valid blocks, decoys, truncation, implausible quantities, duplicates, and layout drift.
- Harness output shows candidate counts, confidence/evidence, and extracted ID→copies data separately from progress messages.
- Low-confidence or ambiguous candidates fail closed and do not publish.

Research decisions after MSC-02 evidence:

- The approved MIT reference searches little-endian `(arenaId, quantity)` anchor pairs, reads a bounded window around hits, and evaluates 8/12/16-byte strides. This is research input, not code to copy blindly.
- Our first MSC-03 slice is engine-neutral and synthetic: byte-fixture extraction, candidate evidence, scoring, ambiguity rejection, and bounded-read planning. It will not scan all 2.95 GB or publish ownership.
- Real reads will be chunked and anchor-directed. Full-region materialization and unbounded whole-process scans are prohibited.
- Known-ID validation must be supplied from a module-owned source; the scanner will not import Deck Planner, Scryfall, card-cache, or log-collection repositories across the boundary.

Implementation evidence (2026-08-09):

- `CandidateBlockExtractor` is a pure byte-fixture component with no process, persistence, UI, or cross-module dependencies. It evaluates little-endian ID/copy pairs at 8/12/16-byte strides and retains evidence for rejected interpretations.
- Selection is fail-closed: minimum size, known-ID ratio, exact-anchor, conflicting-duplicate, and distinct-candidate ambiguity checks must all pass. Equivalent maps found through multiple alignments are collapsed before ambiguity comparison so they cannot conceal a different near-tied interpretation.
- Synthetic tests cover all three reference strides, plausible numeric decoys, truncation, implausible quantities, conflicting duplicates, and equally credible competing candidates. Full validation passes 313 tests with zero failures/errors/skips.
- This slice performs no real process-memory reads and cannot publish ownership. MSC-03 remains open for bounded anchor-directed reads and harness evidence against the current client.
- The Windows seam now supports exact-length `ReadProcessMemory` calls behind `BoundedMemoryWindowReader`. Every read must be within one inventoried committed-readable region, use a positive length no larger than 8 MiB, and complete in full; invalid windows fail before native access and partial native reads fail explicitly. Validation passes 315 tests.
- No anchor constants or known-ID catalog were borrowed from Deck Planner, Scryfall, caches, or log storage. Defining a module-owned validation input remains required before the bounded reader is connected to candidate discovery.
- Scanner evidence configuration is now explicit and harness-owned: a selected JSON document supplies a non-blank client/catalog version plus unique Arena IDs, while the human supplies at least two confirmed `arenaId=copies` anchors. IDs, quantities, duplicates, membership, and minimum anchor count fail closed before process acquisition. Validation passes 317 tests.
- The harness displays the accepted version, known-ID count, and anchor count separately from inventory output. It still does not search memory or publish ownership. Producing the versioned known-ID document from an isolated source is the next implementation slice.
- The scanner can now produce that document directly from Arena's newest `MTGA_Data/Downloads/Raw/Raw_CardDatabase_*.mtga` SQLite file by reading only `Cards.GrpId`. Output is sorted, deduplicated, range-checked, versioned by source identity, and atomically replaced; it has no dependency on application H2 data or network enrichment.
- The harness offers **Build from Arena install** off the EDT. The currently installed database contains 26,126 distinct in-range IDs (6,873 through 107,976), providing a concrete expected click-evidence scale. Full validation passes 318 tests.
- Human evidence accepted the 26,126-ID catalog and five confirmed anchors, then reacquired MTGA PID 185456 with 4,636 readable regions / 3,339,694,080 readable bytes. Preflight and inventory remained non-publishing.
- The harness now performs a first real anchor-discovery pass over committed writable private regions likely to contain the Mono heap. All anchors are searched together in overlapping 1 MiB chunks; progress, failed chunks, hit counts, and bounded hit addresses are reported. This pass still marks collection extraction not attempted and cannot publish.
- Catalog building and memory discovery run off the EDT. A modal glass pane with an indeterminate spinner blocks conflicting harness interaction for the duration while progress messages continue through EDT-safe publication.
- Human discovery evidence found 14 exact anchor hits, including two clusters containing all five anchors with identical internal spacing near `0x1ee01fba8c8` and `0x1f0fd8bf8c8`. This strongly supports duplicated collection-shaped structures rather than random pair collisions.
- Hits are now clustered within their inventoried region and each cluster receives at most one bounded 8 MiB read. Candidate output includes outcome, interpretation count, stride/alignment, entries, known ratio, exact anchors, conflicts, score, and rejection reasons. Accepted evidence remains non-complete and publication is explicitly disabled.
- Discovery diagnostics now distinguish unique bytes covered from transport bytes read, so seven-byte chunk overlap no longer appears as excess logical coverage.
- Human bounded-window evidence produced two strong stride-16 candidates: 3,337 entries / 99.6% known / five exact anchors and 3,322 entries / 100% known / five exact anchors, both conflict-free. Because the maps differ, they are treated as possible current/stale snapshots rather than interchangeable successes.
- A cross-window consensus gate reports rejected, known-domain consensus, or ambiguous globally and counts both raw and authoritative-domain differences. Unequal known-ID projections remain ambiguous and non-publishing.
- Repeat evidence reproduced the same stable 3,322-entry 100%-known candidate; the 3,337-entry candidate differed by exactly 15 raw pairs and included out-of-domain IDs. Consensus now compares only the authoritative Arena-local known-ID projection, while separately reporting raw, known-domain, and unknown-entry differences. Unknown pairs are never eligible for ownership.
- Human final MSC-03 evidence reported `KNOWN_DOMAIN_CONSENSUS`: both accepted windows projected to the identical 3,322-entry known-ID map, with zero known-domain differences; the wider window's 15 extra pairs were entirely unknown-domain boundary noise. The human approved the result.

#### MSC-04 — Atomic real-collection publication

**State:** complete — ownership semantics causally validated and publication restored

Connect an accepted complete scan to the module-owned H2 table. Preserve the previous complete table until the replacement transaction commits.

Acceptance evidence:

- Transaction/failure tests prove no partial visibility and recovery of the previous complete table.
- `getCopiesOwned(id)` distinguishes unknown-before-publication from zero-in-a-complete-table and positive ownership.
- Scanner-specific evidence is not stored in or joined to other application schemas.

Implementation evidence (2026-08-09):

- `KnownDomainConsensus` is a pure publication gate requiring at least two independently accepted windows with identical non-empty Arena-local known-ID projections. One accepted window is rejected; known quantity differences are ambiguous; unknown raw pairs are excluded.
- The Windows scanner returns `complete=true` and the consensus map only through that gate. Rejected and ambiguous scans return no copies and therefore retain the previous H2 publication.
- `MemoryCollectionService` sends complete results through the existing module-owned transactional `replaceComplete` operation. Unknown-before-first-publish, atomic replacement, invalid replacement retention, and service publication behavior remain covered.
- Full relay validation passes 324 tests with zero failures/errors/skips. Human real-publication evidence is still required before MSC-04 closes.
- Real-client ownership semantics are now causally validated. A preserved 3,322-entry baseline followed by one store-pack opening produced a 3,325-entry generation, exactly matching the three cards marked First; six total ID quantities changed and no value exceeded four. Manual UI review counted 466 visible playsets versus 510 memory playsets, a plausible printing/filter difference. Generation-aware consensus accepts only a uniquely monotonic candidate supported by at least two independent windows; conflicting generations remain fail-closed.
- Superseded safety state: publication was temporarily disabled while semantics were unverified. The controlled pack-opening comparison subsequently validated the field and publication is now restored only behind the strict generation-aware gate described above.

#### MSC-05 — Narrow application integration

**State:** complete — human-approved in production

Compose the module behind its two-operation port and allow Deck Planner ownership queries/manual synchronization without importing Windows or scanner implementation types.

Acceptance evidence:

- Non-Windows and unavailable-scanner operation leaves the rest of the application usable.
- Dependency inspection confirms no application package reaches through the port.
- Existing log-observation collection code remains independent and is neither silently merged nor treated as the memory scanner's table.

Implementation evidence (2026-08-09, protocol foundation):

- `CollectionOwnership` now accepts a collection of Arena IDs and returns a complete ID-to-copies map, preserving `-1` for every requested ID before first publication and zero for absent IDs after publication.
- `CollectionUpdate` defines an application-neutral session conversation: the UI observes status, card-selection requirements, and completion; it responds with continue or verified card quantities and may cancel. Public names contain no memory/scanner/candidate/anchor terminology.
- Card choices carry Arena ID, name, set code/name, and collector number. The application UI remains responsible for set icons and other visual enrichment.
- `MemoryCollectionService` implements both ports while retaining Windows progress/evidence only on its internal harness callbacks. Automated validation remains 324 tests passing.
- The protocol foundation is not yet the production wizard: verified-card responses must next configure the provider-owned evidence state, and session cancellation must interrupt active work before the shell integration is ready for click review.
- Verified card quantities are now stored in a separate provider-owned H2 table and atomically replaced only with at least two valid cards. Quantity-four cards are ordered first for future automatic retries because normal Arena ownership cannot decrease, while remaining subject to runtime verification and client/card exceptions.
- Active session cancellation interrupts the worker, emits one terminal cancellation result, and checks cancellation again before H2 publication. A cancelled scan cannot publish even if native work returns concurrently.
- The first application-owned wizard slice now exists as `CollectionSyncPanel` with a provider-neutral click harness. It guides Arena startup / Decks → Collection, renders protocol card options with set-code badges, set name, collector number, and quantity controls, enforces the protocol minimum, shows application-language progress, and supports cancellation.
- The approved wizard is now composed as a production `Collection` shell module. Its preparation runs off the EDT, builds card-name/printing suggestions from Arena's local card database, and connects the neutral `CollectionUpdate` conversation to the memory provider at the application composition root. The UI imports no memory/Windows/extraction packages. Human production click review confirmed the complete flow works and MSC-05 is approved.

#### MSC-06 — Current-client acceptance and resilience handoff

**State:** complete

Run full validation and human real-client review, record the tested Arena client identity/version evidence, known limitations, diagnostic guidance, and the update procedure for future client drift.

Acceptance evidence:

- Human review exercises scan start, process acquisition, output inspection, successful publication when available, failure/retry, and shutdown.
- Full Maven validation is green.
- Documentation makes client-version sensitivity and maintenance ownership explicit.

Completion evidence (2026-08-09):

- Human real-client work exercised process acquisition, diagnostic output, repeated scanning, evidence-only comparison, successful atomic publication, retry, production wizard use, and normal application closure. The production flow was approved as working “like a charm.”
- Tested evidence identifies Arena client `2026.61.30.13636` / Unity player `0.1.13636.1303683` and local catalog `Raw_CardDatabase_7bc4fb29468604399aa7f1c7afb07405.mtga`, containing 26,126 authoritative Arena IDs at the time of validation.
- Failure is fail-closed: Arena-not-running, access denial, partial native reads, cancellation, insufficient confirmations, ambiguous candidates, and client/layout drift retain the previous complete publication and leave other application modules usable.
- Maintenance and drift diagnostics are recorded in `docs/guides/memory-collection-sync-maintenance.md`.
- Final automated validation passes 338 tests with zero failures/errors/skips; final diff check is clean apart from existing line-ending warnings.
- MSC-01 through MSC-06 are complete. The Memory-Scan Collection Extraction arc is closed.

### Final state

MSC-01 through MSC-06 are complete and human-approved. Ownership publication remains isolated, atomic, fail-closed, and available to application consumers only through the neutral batch lookup and update-session APIs.

## Completed arc — Application Shell & UI Consolidation Preparation

- **Arc identifier:** Application Shell & UI Consolidation Preparation
- **Arc type:** integration / refactor / UX architecture
- **Area in scope:** `src/main/java/app/application/`, the production application shell under `src/main/java/app/ui/`, replay-shell integration under `src/main/java/app/replay/`, module-frame adapters for Deck Planner, Deck Tracker, Draft Assistant, and Coaching, replay-focused developer harnesses under `src/main/java/devtools/`, focused tests, and UI architecture documentation.
- **Completion criteria:** AS-01 through AS-06 reach `complete` or an explicitly named `implemented; <X> deferred` state with green validation and human click evidence. The production application has one true top-level `MainFrame` that owns module selection and displays the selected module; pure replay/dev fixture actions no longer live in the production shell; Deck Planner is reachable as a production module; and the repository contains a documented boundary for the later full UI-consolidation pass.

### Mission

Turn the current replay-centric top-level window into an application shell. Production navigation should select one application module and display that module in the main content area, while replay fixtures, pasted-log experiments, and similar developer-only controls move into a dedicated replay UI test harness. This arc intentionally prepares but does not perform a complete visual/UI consolidation of every module.

### Accepted constraints

- `app.ui.MainFrame` is the target production shell. The existing `app.replay.MainFrame` is not the long-term application frame; replay becomes one module hosted by the application shell.
- A module is application functionality, not merely another top-level `JFrame`. The shell owns application-level navigation, title/chrome, shared settings entry points, selected-module lifecycle, and the central content host.
- Deck Planner must be wired into the production application during this arc rather than remaining preview-only.
- Existing module internals should be adapted incrementally. This arc should not opportunistically redesign Deck Tracker, Draft Assistant, Coaching, or Replay visuals while introducing the shell.
- Pure developer/test actions such as replaying bundled fixture logs and pasted-log experimentation belong in a dedicated replay UI harness under `devtools`, not in production navigation.
- `DeckPlannerWorkspacePreview` remains available as a focused Deck Planner test harness even after the planner is wired into the application.
- The later full UI-consolidation pass should be driven by the architecture inventory produced here: shared chrome, module lifecycle, common toolbar/status concepts, window ownership, common visual primitives, and migration of standalone module frames.
- The next intended Engineering Arc after this one is the deferred MTG Arena process-memory collection extraction research recorded as `SA-MTGA-DEF-005`. Do not start memory scanning inside this arc.

### Ordered items

#### AS-01 — Inventory current UI ownership and define the shell migration contract

**State:** complete

**Completion evidence date:** 2026-08-09

Document the current top-level/window ownership, identify production-vs-devtool responsibilities, define the target shell/module boundaries, and record a staged migration that avoids a big-bang rewrite.

Completion evidence (2026-08-09):

- `docs/architecture/ui-consolidation-preparation.md` inventories the current replay `MainFrame`, `Application` composition root, Deck Tracker, Draft Assistant, Coaching, Deck Planner, and developer-only replay controls.
- The document defines `app.ui.MainFrame` as the production shell and separates module hosting from module-specific presentation.
- It records the later full UI-consolidation concerns without expanding this arc into that redesign.
- Temporary `CardImageTrace` diagnostics used to isolate the Deck Planner image cancellation race are removed after the issue was understood.
- Deck Planner DP-08 is accepted on clean commit `58d4ba1dc2bbbbb560b0f7ac91dcf4dcfa9307b0` with 294 tests passing and explicit human click acceptance.

#### AS-02 — Introduce the true application MainFrame and module host

**State:** complete

Create `app.ui.MainFrame` with application-level navigation and a single central module content host. Define the smallest module contract necessary for selection, visible component ownership, activation/deactivation, and shell title/status integration. Keep module-specific services in `Application`; the frame must not become a second composition root.

Acceptance evidence:

- Focused tests cover module selection, replacement, activation/deactivation ordering, and shell-owned navigation state.
- Closing the shell preserves the existing `Application.close()` lifecycle.
- No replay fixture/dev-only action is introduced into the new shell.

Completion evidence (2026-08-09):

- Production launches `app.ui.MainFrame` with one central `ModuleHost`, horizontal module navigation, shell title/status contributions, Settings ownership, and close propagation to `Application.close()`.
- Focused tests cover initial selection, replacement, activation/deactivation order, repeated selection, navigation state, and top-row placement.
- Subsequent AS-03 and AS-05 human click reviews exercised the shell navigation and approved the integrated module behavior.

#### AS-03 — Wire Deck Planner into production navigation

**State:** complete

Construct the existing Deck Planner services from the production `Application` composition root and expose `DeckPlannerWorkspace` as a selectable application module using the same persistent catalog/card/image/Candidate Set stores proven in the preview harness.

Acceptance evidence:

- Production navigation can open Deck Planner without launching `DeckPlannerWorkspacePreview`.
- Planner startup remains cache-first/non-blocking and shares the established persistent repositories.
- Existing preview tests remain useful and production integration gets focused lifecycle coverage.

Completion evidence (2026-08-09):

- Production `Application` composes one cache-first `DeckPlannerModule` and exposes it through the shell without launching the preview harness.
- The module reuses the persistent catalog, card, image, collection, observed-deck, Candidate Set, and printing-preference stores and closes planner-specific resources with the application lifecycle.
- Automated validation passed 299 tests with zero failures, errors, or skips after the horizontal module-selector review adjustment.
- Human click review confirmed the production module workflow works and explicitly approved AS-03.

#### AS-04 — Extract replay-only developer controls into a replay UI harness

**State:** complete

Move fixture replay and pasted-log experimentation out of production navigation into a dedicated `devtools` replay UI harness. Keep the production Replay module focused on observed/live replay state and legitimate user-facing replay actions.

Acceptance evidence:

- Bundled draft/replay fixture actions are available from the dev harness, not the production shell.
- Pasted raw-log experimentation is available from the dev harness unless separately justified as a production feature.
- Production Replay behavior and automated replay tests remain intact.

Completion evidence (2026-08-09):

- `devtools.ReplayUiHarness` owns bundled match/draft fixtures, pasted raw-log experiments, and arbitrary log-file replay through production framing and presentation boundaries.
- Production `Application` no longer constructs or exposes developer fixture/paste actions, and the obsolete replay `MainFrame` is removed.
- Human click review confirmed the harness works as intended and explicitly approved continuation.

#### AS-05 — Adapt remaining production modules to shell navigation

**State:** complete

Provide incremental shell adapters for Deck Tracker, Draft Assistant, Coaching, Settings, and Replay without forcing their internal visual consolidation. Where a module still needs a secondary/detail window, make that ownership explicit rather than pretending it is already an embeddable panel.

Acceptance evidence:

- Every production module has a documented shell entry point and clear owner for secondary windows.
- Module switching does not duplicate trackers/services or leak background activity.
- Existing standalone-frame behavior remains regression-covered until intentionally retired.

Completion evidence (2026-08-09):

- Deck Tracker and Draft Assistant are explicit shell entries backed by singleton `SecondaryWindowModule` adapters; repeated selection does not duplicate their frames or services.
- Coaching remains a selected-match Replay action and Settings remains shell-owned, documenting why neither is a context-free content module.
- Human click review confirmed the companion-window behavior works for the current integration stage and explicitly approved AS-05.

#### AS-06 — Integration acceptance and future UI-consolidation handoff

**State:** complete

Run full validation and human click review of module navigation/lifecycle, then update the architecture document with evidence-backed follow-up work for a later full UI-consolidation arc.

Acceptance evidence:

- Full supported Maven validation is green.
- Human click review covers shell navigation, Deck Planner production use, Replay, Draft, Deck Tracker, Coaching/settings access, shutdown/relaunch, and dev-harness separation.
- The architecture document identifies which standalone frames/primitives should be consolidated later and which should intentionally remain secondary windows.
- Arc acceptance explicitly hands the next Engineering Arc to `SA-MTGA-DEF-005` process-memory collection extraction research unless the human reprioritizes.

### Final state

AS-01 through AS-06 are complete and human-approved. The arc handed off to the Memory-Scan Collection Extraction arc defined above.

### Current planning decisions

- The Deck Planner arc is complete and human-accepted at clean commit `58d4ba1dc2bbbbb560b0f7ac91dcf4dcfa9307b0` with 294 tests passing.
- The image-display regression was a viewport request-cancellation bookkeeping defect, not corrupt image data or favorite-printing selection; temporary targeted diagnostics are removed after confirmation.
- Prefer an application shell plus module adapters over immediately converting every module into one shared visual framework.
- Preserve `Application` as the service/composition owner; UI classes consume configured services.
- The intended next arc after Application Shell acceptance is `SA-MTGA-DEF-005` MTG Arena process-memory collection extraction research.
- Full visual/UI consolidation across modules is a later mission informed by `docs/architecture/ui-consolidation-preparation.md`, not a hidden requirement of this arc.

## Concurrent arcs

None.
