# Memory-Scan Collection Extraction

## Purpose

This document is the durable reference record for the completed `Memory-Scan
Collection Extraction` Engineering Arc (MSC-01 through MSC-06). It preserves
the research/implementation narrative and evidence that justified the design,
so the bounded roadmap history does not need to keep it in full. For ongoing
operation, drift, and recovery guidance, see
`docs/guides/memory-collection-sync-maintenance.md`.

## Mission and boundary

Research and prototype a manual full-collection synchronization path by
reading the running Windows MTG Arena client process, kept unusually isolated
because it is Windows- and Arena-client-version-sensitive:

- Windows/JNA process access, memory-layout heuristics, diagnostics, scan
  output, and persistence schema stay inside `app.collection.memory` and never
  leak into other application packages.
- The only application-facing API is `CollectionOwnership.getCopiesOwned(ids)`
  (batch `Collection<Long> -> Map<Long,Integer>`) and
  `attemptRealCollectionUpdate()` via the neutral `CollectionUpdate` session
  protocol. Scanner configuration, process discovery, regions, byte patterns,
  candidate scoring, diagnostics, output models, and repository types remain
  internal.
- The module owns a separate H2 ownership table (Arena ID → known owned-copy
  count). It must not reuse, extend, join, or depend on the log-observation
  collection tables.
- A successful scan publishes atomically; a failed, cancelled, partial, or
  low-confidence scan must never replace the last known-good ownership table.
- Development began with a standalone `devtools.MemoryCollectionScanHarness`
  (Attempt scan button, append-only progress log, separate output area) before
  any real process access, using a fake scanner first.

## Implementation narrative

- **MSC-01 (isolated boundary):** package/port boundary, internal scan-result
  contracts, independent H2 schema, and the harness skeleton, proven first
  with a deterministic fake scanner. Reflection coverage prevents the
  two-operation port from silently growing. Human harness click review
  approved continuation.
- **MSC-02 (process acquisition and region inventory):** internal JNA bindings
  acquire `MTGA.exe` with query/read rights, enumerate virtual regions with
  `VirtualQueryEx`, classify committed readable/non-guard pages, and always
  close the handle in a `finally` boundary. No collection extraction is
  attempted. Real-client evidence: PID 185456, 7,534 regions, 6,482 committed,
  4,334 readable, 2,958,073,856 readable bytes.
- **MSC-03 (candidate extraction and confidence evidence):** the largest
  research slice.
  - The approved MIT-licensed `NthPhantom10/MTGA-collection-exporter`
    reference was inspected as research evidence, not copied: it searches
    little-endian `(arenaId, quantity)` anchor pairs, reads a bounded window
    around hits, and evaluates 8/12/16-byte strides.
  - `CandidateBlockExtractor` is a pure, fixture-driven component with no
    process/persistence/UI dependency. It fails closed on minimum size,
    known-ID ratio, exact-anchor, conflicting-duplicate, and distinct-map
    ambiguity checks; equivalent maps from different alignments are
    deduplicated before ambiguity comparison.
  - `BoundedMemoryWindowReader` rejects unreadable regions, cross-region
    windows, address overflow, non-positive sizes, and reads above 8 MiB
    before any native access; partial native reads fail explicitly.
  - Scanner confidence inputs are explicit: a scanner-owned, versioned JSON
    known-ID catalog plus at least two human-confirmed `arenaId=copies`
    anchors, both validated before process acquisition.
  - The installed client (`0.1.13636.1303683`, Unity Mono, not IL2CPP) exposes
    managed symbols such as `_cardInventory`, `ClientPlayerInventory`,
    `GetPlayerCards`, and `CardCollection`, kept as an open hypothesis
    alongside the pair/projection layout.
  - The known-ID catalog is produced directly from Arena's own read-only
    `Raw_CardDatabase_*.mtga` SQLite `Cards.GrpId` column (26,126 distinct IDs
    at the time of validation), versioned and atomically replaced. Xerial
    SQLite JDBC is used only at this isolated boundary.
  - Real anchor discovery searches committed writable `MEM_PRIVATE` regions in
    overlapping 1 MiB chunks; hits are clustered per inventoried region with
    an 8 MiB maximum candidate window per cluster. Long scans run off the EDT
    behind an indeterminate busy glass pane.
  - Multiple accepted windows require known-domain consensus: real evidence
    found unequal raw 3,337- and 3,322-entry maps, so the scanner compares
    Arena-local known-ID projections and reports raw/known-domain/unknown
    differences separately rather than trusting either raw map. Final human
    evidence reported `KNOWN_DOMAIN_CONSENSUS` — both accepted windows
    projected to the identical 3,322-entry known-ID map.
- **MSC-04 (atomic real-collection publication):**
  - `KnownDomainConsensus` requires at least two independently accepted
    windows with identical non-empty known-ID projections; one window is
    rejected, and any known-quantity difference is ambiguous.
  - Structural consensus alone was later found **necessary but not
    sufficient** for ownership semantics: an evidence-only before/after
    pack-opening experiment (3,322 → 3,325 entries, exactly matching three
    First-marked cards, six quantity changes, none exceeding four) causally
    validated the quantity field. Publication additionally requires
    independently supported, uniquely monotonic generation consensus.
  - Publication was briefly disabled after a human UI review correctly
    challenged an earlier premature "3,322 distinct cards owned" claim, then
    restored only behind the stricter generation-aware gate above.
- **MSC-05 (narrow application integration):**
  - `CollectionOwnership` accepts a batch of Arena IDs and returns a complete
    map, preserving `-1` (unknown) before first publication and `0` for
    absent IDs afterward.
  - `CollectionUpdate` is an application-neutral session conversation with no
    memory/scanner/anchor terminology in its public surface; verified card
    quantities persist in an isolated provider-owned state table, and active
    session cancellation is a hard publication barrier.
  - `app.collection.ui.CollectionSyncPanel` and
    `devtools.CollectionSyncWizardHarness` are the first application-owned
    wizard slice; the wizard is now composed as the production `Collection`
    shell module. Its preparation runs off the EDT and resolves card
    name/set suggestions from Arena's local card database. Human production
    click review confirmed the flow "works like a charm."
- **MSC-06 (acceptance and resilience handoff):** full human real-client
  review exercised acquisition, diagnostics, repeated scanning, evidence-only
  comparison, successful publication, retry, production wizard use, and normal
  shutdown. Tested evidence: Arena client `2026.61.30.13636` / Unity player
  `0.1.13636.1303683`, catalog `Raw_CardDatabase_7bc4fb29468604399aa7f1c7afb07405.mtga`
  with 26,126 known IDs. Final automated validation: 338 tests green.

## Durable outcome

MSC-01 through MSC-06 are complete and human-approved. Ownership publication
remains isolated, atomic, fail-closed, and reachable by the rest of the
application only through `getCopiesOwned(ids)` and the `CollectionUpdate`
session protocol. See `.steadyarc/engineering-notes.md` for the condensed
durable invariants and `docs/guides/memory-collection-sync-maintenance.md` for
operational maintenance guidance.
