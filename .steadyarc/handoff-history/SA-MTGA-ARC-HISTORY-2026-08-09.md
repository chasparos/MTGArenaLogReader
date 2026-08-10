# Steady Arc Handoff History — Deck Planner / Application Shell / Memory-Scan (through 2026-08-09)

This record preserves the detailed ownership-transition amendment trail for the Deck Planner (DP-06 through DP-08), Application Shell & UI Consolidation Preparation (AS-01 through AS-06), and Memory-Scan Collection Extraction (MSC-01 through MSC-06) arcs, all of which are complete and human-accepted. It is retained as historical evidence, not current state; see `.steadyarc/handoff.md` for current ownership and `.steadyarc/roadmap.md` for the current active arc.

### Application Shell integration amendment — 2026-08-09

- AS-02 through AS-05 are implemented, green, and human-approved through production shell, Deck Planner, replay harness, and companion-window click reviews.
- Production uses `app.ui.MainFrame`; Replay and Deck Planner are embedded modules, while Deck Tracker and Draft Assistant are singleton companion windows reached through explicit shell adapters.
- Coaching remains Replay-contextual, Settings remains shell-owned, and developer fixture/paste behavior lives only in `devtools.ReplayUiHarness`.
- Java 21 is authoritative across build, CI, tooling, and current documentation; the stale Room-card open issue is removed.
- AS-06 final acceptance is the only remaining item in the Application Shell arc. The latest automated working-tree validation before final acceptance is 301 tests passing with zero failures/errors/skips.
- After AS-06 acceptance, return ownership to the human and preserve `SA-MTGA-DEF-005` process-memory collection extraction research as the intended next Engineering Arc unless the human reprioritizes.

### Application Shell acceptance / Memory-Scan arc activation — 2026-08-09

- Human explicitly accepted AS-06 and signed off the Application Shell & UI Consolidation Preparation arc after shell, Deck Planner, replay harness, companion-window, Coaching/Settings, and shutdown/relaunch review.
- Future UI consolidation or look-and-feel planning must start with a fresh click-review and user-evidence pass before concrete plans are made.
- `SA-MTGA-DEF-005` is promoted into the active `Memory-Scan Collection Extraction` arc.
- The new module is deliberately isolated due to Windows and Arena-client-version sensitivity. Its only application-facing API is `getCopiesOwned(id)` and `attemptRealCollectionUpdate()`.
- The active item is MSC-01: build the isolated boundary, separate H2 ownership table, and fake-scanner harness with Attempt scan, progress-message log, and output area before planning real memory heuristics.

### MSC-01 implementation amendment — 2026-08-09

- The isolated two-operation port, independent atomic H2 ownership repository, fake asynchronous scanner, and `devtools.MemoryCollectionScanHarness` are implemented.
- Automated validation passes 305 tests with zero failures/errors/skips; diff check is clean apart from line-ending notices.
- MSC-01 remains open only for human harness click evidence. Do not plan MSC-02 process acquisition details until that evidence is returned.

### MSC-01 acceptance / MSC-02 implementation amendment — 2026-08-09

- Human approved the fake-scanner harness and authorized continuation; MSC-01 is complete.
- MSC-02 now acquires a running `MTGA.exe`, inventories committed readable virtual-memory regions through internal JNA bindings, reports evidence in the harness, and always closes the process handle.
- MSC-02 does not read collection bytes, score candidates, or publish ownership. Automated validation passes 308 tests with zero failures/errors/skips.
- Return condition: human runs the harness with Arena active and reviews process-acquisition, region-inventory, failure/retry, and handle-closure output before MSC-03 planning.

### MSC-02 acceptance / MSC-03 research activation — 2026-08-09

- Human real-client evidence: PID 185456; 7,534 regions; 6,482 committed; 4,334 readable; 2,958,073,856 readable bytes; handle closed; extraction not attempted; ownership retained. MSC-02 is approved and complete.
- The approved MIT reference was inspected locally. It searches little-endian ID/quantity anchors, bounded windows, and 8/12/16-byte strides, then scores known IDs and exact anchors.
- MSC-03 begins with synthetic engine-neutral extraction/scoring fixtures. Do not scan the full readable address space, publish candidates, or couple to existing card/collection repositories.

### MSC-03 synthetic evidence amendment — 2026-08-09

- The pure `CandidateBlockExtractor` evaluates little-endian ID/copy blocks at 8/12/16-byte strides and emits acceptance/rejection evidence without accessing a process or repository.
- Confidence gates cover minimum size, module-supplied known-ID ratio, exact anchors, conflicting duplicates, and ambiguity between distinct ownership maps. Equivalent maps from different alignments are deduplicated before comparison.
- Five deterministic tests cover valid layouts and fail-closed decoy, truncation, implausible quantity, duplicate-conflict, and ambiguity cases. Full relay validation passes 313 tests with zero failures/errors/skips.
- MSC-03 remains active. Next work is bounded anchor-directed reads and harness display only; real candidates still must not publish ownership.

### MSC-03 bounded native-read amendment — 2026-08-09

- Internal JNA now binds `ReadProcessMemory`; exact-length failure diagnostics remain confined to the Windows module.
- `BoundedMemoryWindowReader` rejects unreadable regions, cross-region windows, address overflow, non-positive sizes, and reads above 8 MiB before native access. Partial native reads fail explicitly.
- Full relay validation passes 315 tests with zero failures/errors/skips.
- The reader is not yet invoked by the harness. The next design input is an explicit module-owned known-ID/anchor source; other application repositories must not be used as a shortcut.

### MSC-03 evidence-configuration amendment — 2026-08-09

- The harness now accepts a scanner-owned versioned known-ID JSON file and at least two explicit `arenaId=copies` anchors.
- Validation rejects malformed/unversioned catalogs, invalid or duplicate IDs, malformed quantities, duplicate anchors, anchors absent from the selected catalog, and fewer than two anchors before opening MTGA.exe.
- Accepted evidence identity/counts appear in the output before the existing region inventory. No byte search or publication occurs. Full relay validation passes 317 tests.
- Next work is an isolated producer for the known-ID document, followed by chunked anchor-pattern discovery and bounded candidate windows.

### MSC-03 Arena-local catalog / Mono evidence amendment — 2026-08-09

- The installed client version `0.1.13636.1303683` (dated 2026-07-21) is Unity Mono: it ships `MonoBleedingEdge`, `mscorlib.dll`, `Assembly-CSharp.dll`, and `Core.dll`, with no IL2CPP `GameAssembly.dll` / `global-metadata.dat` markers.
- Managed assembly strings include `_cardInventory`, `ClientPlayerInventory`, `GetPlayerCards`, `CardCollection`, and `GetCardCollection`. A managed dictionary/object graph is now an explicit hypothesis alongside contiguous pair/projection layouts; neither is yet treated as proven.
- The harness can build its scanner-owned known-ID JSON from Arena's read-only `Raw_CardDatabase_*.mtga` SQLite `Cards.GrpId` column. Current real-client evidence is 26,126 distinct valid IDs, range 6,873–107,976.
- Catalog output is versioned and atomically replaced. Xerial SQLite JDBC 3.53.1.0 is used only at this isolated Arena database boundary. Full relay validation passes 318 tests.
- Return condition: human clicks **Build from Arena install**, supplies at least two known owned `arenaId=copies` anchors, and confirms evidence configuration is accepted before real anchor-pattern reads are wired.

### MSC-03 real anchor-discovery amendment — 2026-08-09

- Human confirmed five exact owned printing anchors; Arena-local resolution produced IDs 67692, 104942, 101332, 92156, and 80225 with quantities 2, 4, 1, 2, and 2.
- The first real discovery scanner searches all configured little-endian ID/copy patterns together over committed writable private regions in overlapping 1 MiB reads. It reports progress, failures, and addresses but does not yet extract candidate windows or publish ownership.
- The long-running scan stays off the EDT and the harness is locked behind an indeterminate busy glass pane during catalog builds and scans.

### MSC-03 bounded candidate-window amendment — 2026-08-09

- Real-client evidence returned 14 hits. Two distinct clusters contained all five anchors with identical relative spacing, providing strong evidence of duplicate collection-shaped blocks.
- Nearby hits are now grouped within their original virtual-memory region. Each cluster receives one contained read capped at 8 MiB and is evaluated by the fixture-proven 8/12/16-byte extractor.
- Harness output reports ranked interpretation evidence and explicit `collectionExtraction=EVIDENCE_ONLY` / `collectionPublication=DISABLED`. Even an accepted candidate cannot yet mutate H2 ownership.
- Unique coverage and overlapped transport byte counts are now separate diagnostics. Full relay validation remains 321 tests passing.

### MSC-03 real candidate evidence / consensus amendment — 2026-08-09

- Two five-anchor windows produced strong conflict-free stride-16 candidates, one with 3,337 entries at 99.6% known IDs and one with 3,322 entries at 100% known IDs. Both matched all five human anchors.
- The unequal maps may represent current and stale managed snapshots. A new cross-window gate reports `AMBIGUOUS` unless every accepted window contains the identical ownership map; it also reports per-candidate differences from the first map.
- This evidence validates real extraction while also validating the fail-closed requirement. Publication remains disabled.
- A repeat scan reproduced the same candidates and 15 raw differences. Because the larger map contains out-of-catalog pairs, the consensus gate now compares known-ID projections and reports unknown/raw differences separately. The next click evidence must confirm whether the two known projections are identical; publication remains disabled regardless.

### MSC-03 acceptance / MSC-04 activation — 2026-08-09

- Human evidence confirmed `KNOWN_DOMAIN_CONSENSUS`: two accepted stride-16 windows both projected to the identical 3,322-entry authoritative map with zero known-ID differences.
- The larger raw interpretation contained 15 additional out-of-catalog pairs; these are retained only as boundary-noise diagnostics and are never ownership entries.
- Human approved the evidence. MSC-03 is complete.
- MSC-04 is active: connect only this exact consensus result to the already isolated atomic H2 replacement path, proving all non-consensus and transaction-failure cases preserve the previous publication.

### MSC-04 implementation amendment — 2026-08-09

- The pure known-domain publication gate requires at least two independent accepted windows with identical non-empty authoritative projections. Single-window acceptance is rejected and any known quantity difference is ambiguous.
- Consensus causes the scanner to return a complete known-only map; all other outcomes return no copies. The service publishes complete maps through the independent H2 replacement transaction and retains prior ownership otherwise.
- Automated validation passes 324 tests with zero failures/errors/skips.
- Return condition: human repeats the accepted five-anchor scan and confirms `collectionExtraction=COMPLETE`, `collectionPublication=ELIGIBLE`, `Complete collection published: 3322 entries`, and `Attempt finished successfully`.

### MSC-04 acceptance / MSC-05 activation — 2026-08-09

- Human real-client evidence passed the two-window known-domain consensus gate and atomically published 3,322 entries. The harness reported `Complete collection published: 3322 entries` and `Attempt finished successfully`.
- MSC-04 is approved and complete.
- MSC-05 is active. Production composition may depend only on `CollectionOwnership`; application packages must not import Windows/JNA, scan evidence, candidate, or memory-repository implementation types. Unavailable scanning must leave other modules usable.

### MSC-04 acceptance revocation — 2026-08-09

- Human UI review correctly challenged the statement that 3,322 extracted entries represented 3,322 distinct owned cards. The user's actual ownership scale makes that interpretation implausible.
- Two-window structural consensus proves a stable card-shaped structure, not the semantic meaning of its quantity field. The earlier real publication is no longer accepted evidence.
- Scanner completion/publication is disabled again and reports `ownershipSemantics=UNVERIFIED`. MSC-04 is reopened pending distribution/sample analysis and positive/negative card truth checks.
- The wizard redesign must distinguish catalog cards examined, distinct cards owned, and total copies, and may show rarity/color/set summaries only after the ownership map is semantically validated.

### MSC-05 neutral-protocol foundation — 2026-08-09

- Human refined the boundary: ownership lookup is batch `Collection<Long> -> Map<Long,Integer>`, and the main application owns the collection-sync wizard, user language, card presentation, and set icons.
- New `CollectionUpdate` session types communicate only neutral status, card requirements/options, verified quantities, completion, continue, and cancel concepts. Scanner-domain nomenclature remains internal.
- `MemoryCollectionService` implements the new ownership and update ports; the developer harness has migrated to the session protocol. Full validation remains 324 tests passing.
- Next work is the real first-run conversation and interruptible cancellation, followed by a main-application wizard click-review slice. Do not expose Windows or extraction models to accomplish it.
- Verified quantities now persist in an isolated provider-state table; full playsets are preferred for future automatic verification but never assumed valid without a current scan match.
- Update sessions use interruptible worker tasks and suppress publication after cancellation. The next slice is the application-owned wizard and actual `CardsRequired` conversation.
- `app.collection.ui.CollectionSyncPanel` and `devtools.CollectionSyncWizardHarness` now provide the first application-owned wizard click slice using only `CollectionUpdate`. It includes intro guidance, exact-printing rows, set-code badge fallback, quantity inputs, minimum-card validation, neutral progress, completion, and cancellation.
- The approved wizard is now available from the production shell as the `Collection` module. Arena catalog preparation and the Windows provider are wired only in the `Application` composition root; the module and wizard retain neutral protocol/presentation dependencies. Preparation is asynchronous, closure during preparation is safe, and the latest observed Decks/Collection navigation step is replayed when the modal opens. Full validation: 338 tests green. Human production click review confirmed it “works like a charm”; MSC-05 is approved.
- Return condition: human runs `devtools.CollectionSyncWizardHarness` and reviews the first-run instructions, card/set distinction, quantity entry, validation, progress lock, cancel, and completion language before real provider suggestions are connected.

### DP-06 interaction-polish amendment — 2026-08-08

- Human click review after the 264-test baseline requested one further Candidate UX pass before filtering work.
- Scope is favorite-art presentation consistency, transient art chooser behavior, compact/collapsible Candidate categories, category-local drag/drop ordering/new-category drops, overlay workspace boundary controls, rarity-aware Candidate selection outline, and Catalog scroll-to-selected-Candidate behavior.
- Filtering/tag taxonomy remains outside this amendment.
- Ownership remains with the patch-exchange assistant for this bounded patch; return to the human for repository-side validation and click review.

### DP-06 favorite/layout polish amendment — 2026-08-08

- Human click review after the 271-test baseline found that persisted favorite printing changes did not invalidate the Catalog's logical-identity image cache, Candidate spacing remained too loose, and floating boundary controls could lag panel layout changes.
- Scope is immediate favorite-art refresh for Catalog presentation, approximately halved Candidate spacing, and transparent circular boundary controls with deterministic post-layout positioning.
- Filtering/tag taxonomy remains outside this amendment.
- Ownership remains with the patch-exchange assistant for this bounded patch; return to the human for repository-side validation and click review.


### DP-06 acceptance / DP-07 activation amendment — 2026-08-08

- Human explicitly accepted DP-06 after real-card click review and the clean 272-test `6a67dcfef2a3298ee8b1794dcd7a166576d6db75` baseline.
- DP-06 is closed; DP-07 becomes the active Deck Planner roadmap item.
- The prior DP-07 fixed closing question is withdrawn.
- DP-07 begins with persisted Candidate Set planning notes and a richer strategic-analysis instruction contract.
- `DeckPlannerWorkspacePreview` remains the human click-test harness for DP-07.
- Ownership remains with the human until the next bounded DP-07 implementation delegation.


### DP-07.1 Candidate Set note implementation amendment — 2026-08-08

- Human delegated the first DP-07 implementation slice.
- Candidate Set persistence now carries an optional free-form note with a backward-compatible schema migration.
- Candidate UI exposes `Edit note`; saving the note saves the named Candidate Set, and loading the set restores the note.
- `DeckPlannerWorkspacePreview` is retargeted from DP-06 acceptance wording to DP-07 note-workflow human review while remaining the same repository-owned click-test harness.
- No AI export schema or instruction payload is implemented in this slice.
- Return owner after patch delivery: Human repository owner for application, automated validation, and click review.


### DP-07.2/DP-07.3 exporter implementation amendment — 2026-08-08

- Human confirmed DP-07.1 looked good after the clean 274-test `0680b6046113db4011defb4cffe80590099687e9` baseline and delegated continuation.
- Scope is the first deterministic `MTGA_DECK_BUILD_REQUEST_V1` schema, authoritative Candidate Set/card encoding, the stable deck-analysis instruction brief, and a copyable modeless `AI export` review window in the existing preview harness.
- Export resolution is local/cache-only through `CardNameRepository.resolveIdentity`; generating the request does not perform network enrichment or block on Scryfall.
- Unresolved Candidate identities remain explicit `status=UNRESOLVED`; the instruction contract forbids inventing missing card facts.
- DP-07 remains active after this patch. Human click review of the generated request is required before acceptance, and additional golden coverage for specialized card layouts may still be added if review exposes protocol gaps.
- Return owner after patch delivery: Human repository owner for application, automated validation, and click review.


### DP-07 acceptance / DP-08 activation amendment — 2026-08-08

- Human accepted DP-07 after successfully using the generated `MTGA_DECK_BUILD_REQUEST_V1` prompt in a real deck-design conversation.
- The accepted baseline is clean `14c490a691d43bda6b6b2f005c4f996e5d738a20` with 278 tests passing.
- DP-07 is closed and DP-08 becomes active.
- `DeckPlannerWorkspacePreview` remains the human click-test surface for DP-08 integration and release-evidence work.
- The human supplied a possible future MTGA collection-import route based on process-memory scanning; it is recorded as deferred issue `SA-MTGA-DEF-005` and does not expand DP-08.
- Ownership remains with the human until the next bounded DP-08 implementation delegation.


### DP-08.1 integration/lifecycle amendment — 2026-08-09

- Scope is evidence, not new planner functionality: exercise the production preview catalog path through startup, filter refinement, Candidate import, deterministic AI export, and shutdown.
- `DeckPlannerWorkspacePreview` human review is retargeted from DP-07 prompt quality to DP-08 startup/lifecycle, offline/degraded states, end-to-end workflow continuity, and clean shutdown/relaunch.
- Expected test discovery change: +1 integration fixture (278 → 279).
- Ownership returns to the human for repository-side validation and click review after this patch.


### DP-08.2 performance-evidence amendment — 2026-08-09

- DP-08.1 is complete on clean commit `438e2e4a5640721709697894ba3b5b26c350a132` with 279 tests passing.
- DP-08.2 adds observability only where evidence was previously unavailable: persistent image-cache hit/network counters and browser image-window/cache cardinality.
- Evidence fixtures intentionally avoid hard wall-clock acceptance thresholds. They record elapsed values while asserting qualitative contracts: cached startup is usable without refresh, EDT remains serviceable while background work waits, viewport materialization does not request the full catalog, revisiting a viewport reuses images, scrolling grows cache only with distinct requested cards, and persistent image cache transitions disk→memory without network.
- No optimization or product behavior change is authorized by this slice unless the measurements expose a concrete defect.


### DP-08 real-card review correction amendment — 2026-08-09

- DP-08.2 performance evidence is integrated on clean `0cc4b9e3f7d0c504c40513851f4fbd9885553368` with 283 tests passing.
- Human click review found that cards whose cached metadata lacked image URLs could still render as missing art even when their image file was already persisted. A no-favorite/default-printing path must reuse the persistent image by stable Scryfall/Arena identity before declaring art unavailable.
- Filter collapse must synchronously relayout/reposition the floating boundary controls; Reset filters should match the neighboring Workspace control height.
- Candidate organization needs a denser content-width flow layout, category-aware card drops, whole-category whitespace drag handles with boundary-line feedback, reliable multi-card drag preservation, and hover autoscroll during drag.
- DP-08.3 remains blocked until this correction is green and passes another real-card click review.


### Deck Planner acceptance / Application Shell activation amendment — 2026-08-09

- Human explicitly accepted the Deck Planner after the image cancellation race was confirmed fixed in real-card preview use.
- Accepted repository baseline: clean `58d4ba1dc2bbbbb560b0f7ac91dcf4dcfa9307b0`, 294 tests passing.
- DP-08 and the Deck Planner arc are closed.
- Temporary targeted card-image diagnostics are removed after serving their purpose.
- The new primary arc is `Application Shell & UI Consolidation Preparation`.
- `AS-01` documents the current UI/window ownership and target shell/module boundary before production refactoring starts.
- The intended next arc after Application Shell acceptance is `SA-MTGA-DEF-005` process-memory collection extraction research.
- Ownership returns to the human for transition-patch application and validation before AS-02 implementation.
## Collection wizard click-review slice (2026-08-09)

- Replaced the misleading terminal progress state with a dedicated completion page.
- Completion language distinguishes catalog cards examined, distinct cards owned, and total copies; it can also show top sets, rarity, and color summaries.
- Card verification is application-owned and user-friendly: card-name search, exact-printing results, user-selected rounded card items, artwork slots, and `x0` through `x4` quantity choices.
- Friendly client/navigation messages remain neutral protocol status events. Production Player.log Collection-screen detection is intentionally not claimed: available fixtures show duel/pre-game scene records but no stable Decks/Collection scene signature. A real binding requires captured evidence.
- Structural consensus of 3,322 entries remains **evidence only**. Scanner publication stays disabled until ownership semantics are proven.
- Live Player.log evidence established the ordered navigation sequence `Home -> DeckListViewer`
  (`Navigate to Deck Manager`) followed by `DeckListViewer -> DeckBuilder` (`deck builder`).
  `CollectionNavigationObserver` now recognizes only that ordered sequence and emits application-level
  `DECKS_OPEN` / `COLLECTION_OPEN` readiness steps. It is ready to join the existing raw-log observer
  fan-out when the wizard is installed in the main application.
