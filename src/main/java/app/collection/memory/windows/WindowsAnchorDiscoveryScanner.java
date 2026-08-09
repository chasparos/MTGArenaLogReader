package app.collection.memory.windows;

import app.collection.memory.CollectionScanEngine;
import app.collection.memory.extraction.AnchorPatternFinder;
import app.collection.memory.extraction.CandidateBlockExtractor;
import app.collection.memory.extraction.ScanEvidenceConfigLoader;
import app.collection.memory.extraction.KnownDomainConsensus;
import app.collection.memory.extraction.QuantitySemanticsEvidence;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Discovers candidates and returns a complete map only after independent known-domain consensus. */
public final class WindowsAnchorDiscoveryScanner implements CollectionScanEngine {
    private static final int CHUNK_BYTES = 1024 * 1024;
    private static final int OVERLAP_BYTES = 7;
    private static final int MAX_REPORTED_HITS = 512;
    private final WindowsProcessApi api;
    private final Supplier<Optional<WindowsRegionInventoryScanner.ProcessTarget>> targetFinder;
    private final Supplier<ScanEvidenceConfigLoader.Config> evidence;

    public WindowsAnchorDiscoveryScanner(Supplier<ScanEvidenceConfigLoader.Config> evidence) {
        this(new JnaWindowsProcessApi(), WindowsRegionInventoryScanner::findArena, evidence);
    }

    WindowsAnchorDiscoveryScanner(WindowsProcessApi api,
            Supplier<Optional<WindowsRegionInventoryScanner.ProcessTarget>> targetFinder,
            Supplier<ScanEvidenceConfigLoader.Config> evidence) {
        this.api = Objects.requireNonNull(api);
        this.targetFinder = Objects.requireNonNull(targetFinder);
        this.evidence = Objects.requireNonNull(evidence);
    }

    @Override public ScanResult scan(Consumer<String> progress) throws Exception {
        ScanEvidenceConfigLoader.Config config = Objects.requireNonNull(evidence.get(),
                "Evidence configuration was not supplied");
        progress.accept("Locating MTGA.exe");
        var target = targetFinder.get().orElseThrow(() -> new IllegalStateException("MTGA.exe is not running"));
        WindowsProcessApi.ProcessHandleRef handle = null;
        try {
            handle = api.open(target.processId());
            progress.accept("Arena client process acquired: pid=" + target.processId());
            List<WindowsProcessApi.MemoryRegion> inventory = api.inventory(handle, progress::accept);
            List<WindowsProcessApi.MemoryRegion> regions = inventory.stream()
                    .filter(WindowsAnchorDiscoveryScanner::likelyMonoHeap).toList();
            long eligibleBytes = regions.stream().mapToLong(WindowsProcessApi.MemoryRegion::size).sum();
            progress.accept("Anchor discovery started: " + regions.size()
                    + " writable private regions, " + eligibleBytes + " bytes");
            Discovery discovery = discover(handle, regions, config, progress);
            progress.accept("Anchor discovery complete: " + discovery.hits().size()
                    + " hits, " + discovery.failedReads() + " unreadable chunks skipped");
            List<CandidateWindow> candidates = extractCandidateWindows(
                    handle, discovery.hits(), config, progress);
            KnownDomainConsensus.Decision consensus = consensus(candidates, config.knownArenaIds());
            boolean structuralConsensus = consensus.outcome() == KnownDomainConsensus.Outcome.CONSENSUS;
            boolean publicationEligible = structuralConsensus
                    && ownershipShapeIsVerified(consensus.copies(), config.anchors());
            progress.accept(publicationEligible
                    ? "Verified collection ownership generation found: " + consensus.copies().size() + " cards"
                    : "Known-domain consensus not accepted: " + consensus.outcome());
            return new ScanResult(publicationEligible,
                    publicationEligible ? consensus.copies() : Map.of(), consensus.copies(),
                    report(target, config, regions, eligibleBytes, discovery, candidates,
                            consensus, publicationEligible));
        } finally {
            if (handle != null) {
                api.close(handle);
                progress.accept("Arena client process handle closed");
            }
        }
    }

    private Discovery discover(WindowsProcessApi.ProcessHandleRef handle,
            List<WindowsProcessApi.MemoryRegion> regions, ScanEvidenceConfigLoader.Config config,
            Consumer<String> progress) throws InterruptedException {
        AnchorPatternFinder finder = new AnchorPatternFinder();
        List<LocatedHit> hits = new ArrayList<>();
        long covered = 0;
        long transported = 0;
        long nextProgress = 256L * 1024 * 1024;
        int failed = 0;
        for (var region : regions) {
            long offset = 0;
            while (offset < region.size()) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Anchor discovery cancelled");
                int length = (int) Math.min(CHUNK_BYTES, region.size() - offset);
                long address = region.baseAddress() + offset;
                try {
                    byte[] chunk = api.read(handle, address, length);
                    for (var hit : finder.find(chunk, config.anchors())) {
                        if (hits.size() < MAX_REPORTED_HITS) {
                            hits.add(new LocatedHit(hit.anchor().arenaId(), hit.anchor().copies(),
                                    address + hit.offset(), region));
                        }
                    }
                } catch (RuntimeException error) {
                    failed++;
                }
                transported += length;
                covered += length - coveredWithinRegion(offset);
                if (covered >= nextProgress) {
                    progress.accept("Anchor discovery covered " + covered + " / " +
                            regions.stream().mapToLong(WindowsProcessApi.MemoryRegion::size).sum() + " unique bytes");
                    nextProgress += 256L * 1024 * 1024;
                }
                if (length == region.size() - offset) break;
                offset += length - OVERLAP_BYTES;
            }
        }
        return new Discovery(covered, transported, failed, List.copyOf(hits));
    }

    private static long coveredWithinRegion(long offset) {
        return offset == 0 ? 0 : OVERLAP_BYTES;
    }

    private List<CandidateWindow> extractCandidateWindows(WindowsProcessApi.ProcessHandleRef handle,
            List<LocatedHit> hits, ScanEvidenceConfigLoader.Config config, Consumer<String> progress) {
        List<List<LocatedHit>> clusters = cluster(hits);
        progress.accept("Reading " + clusters.size() + " bounded candidate windows");
        List<CandidateWindow> results = new ArrayList<>();
        BoundedMemoryWindowReader reader = new BoundedMemoryWindowReader(api);
        CandidateBlockExtractor extractor = new CandidateBlockExtractor(
                CandidateBlockExtractor.Config.conservative());
        for (int index = 0; index < clusters.size(); index++) {
            List<LocatedHit> cluster = clusters.get(index);
            var region = cluster.getFirst().region();
            long first = cluster.getFirst().address();
            long last = cluster.getLast().address() + 8;
            long regionEnd = region.baseAddress() + region.size();
            long start = Math.max(region.baseAddress(), first - 4L * 1024 * 1024);
            long end = Math.min(regionEnd, start + BoundedMemoryWindowReader.MAX_WINDOW_BYTES);
            if (end < last) {
                end = Math.min(regionEnd, last + 4L * 1024 * 1024);
                start = Math.max(region.baseAddress(), end - BoundedMemoryWindowReader.MAX_WINDOW_BYTES);
            }
            try {
                byte[] bytes = reader.read(handle, region, start, Math.toIntExact(end - start));
                var selection = extractor.extract(bytes, config.knownArenaIds(), config.anchors());
                results.add(new CandidateWindow(start, bytes.length, cluster.size(), selection, null));
                progress.accept("Candidate window " + (index + 1) + "/" + clusters.size()
                        + ": " + selection.outcome() + ", " + selection.candidates().size()
                        + " interpretations");
            } catch (RuntimeException error) {
                results.add(new CandidateWindow(start, Math.toIntExact(end - start),
                        cluster.size(), null, error.getMessage()));
                progress.accept("Candidate window " + (index + 1) + " failed: " + error.getMessage());
            }
        }
        return List.copyOf(results);
    }

    private static List<List<LocatedHit>> cluster(List<LocatedHit> hits) {
        List<LocatedHit> sorted = hits.stream().sorted(Comparator
                .comparingLong((LocatedHit hit) -> hit.region().baseAddress())
                .thenComparingLong(LocatedHit::address)).toList();
        List<List<LocatedHit>> clusters = new ArrayList<>();
        for (LocatedHit hit : sorted) {
            if (clusters.isEmpty()) {
                clusters.add(new ArrayList<>(List.of(hit)));
                continue;
            }
            List<LocatedHit> current = clusters.getLast();
            LocatedHit previous = current.getLast();
            if (previous.region().baseAddress() == hit.region().baseAddress()
                    && hit.address() - previous.address() <= BoundedMemoryWindowReader.MAX_WINDOW_BYTES) {
                current.add(hit);
            } else {
                clusters.add(new ArrayList<>(List.of(hit)));
            }
        }
        return clusters;
    }

    private static boolean likelyMonoHeap(WindowsProcessApi.MemoryRegion region) {
        int access = region.protection() & 0xff;
        boolean writable = access == 0x04 || access == 0x08 || access == 0x40 || access == 0x80;
        return region.committedReadable() && region.type() == 0x20000 && writable;
    }

    private static String report(WindowsRegionInventoryScanner.ProcessTarget target,
            ScanEvidenceConfigLoader.Config config, List<WindowsProcessApi.MemoryRegion> regions,
            long eligibleBytes, Discovery discovery, List<CandidateWindow> candidates,
            KnownDomainConsensus.Decision consensus, boolean publicationEligible) {
        StringBuilder text = new StringBuilder("WINDOWS ANCHOR DISCOVERY\n")
                .append("processId=").append(target.processId()).append('\n')
                .append("catalogVersion=").append(config.version()).append('\n')
                .append("anchors=").append(config.anchors().size()).append('\n')
                .append("eligibleRegions=").append(regions.size()).append('\n')
                .append("eligibleBytes=").append(eligibleBytes).append('\n')
                .append("uniqueBytesCovered=").append(discovery.coveredBytes()).append('\n')
                .append("transportBytesRead=").append(discovery.transportedBytes()).append('\n')
                .append("failedChunks=").append(discovery.failedReads()).append('\n')
                .append("anchorHits=").append(discovery.hits().size()).append('\n');
        for (LocatedHit hit : discovery.hits()) {
            text.append("hit=").append(hit.arenaId()).append('x').append(hit.copies())
                    .append(" @ 0x").append(Long.toHexString(hit.address())).append('\n');
        }
        text.append("candidateWindows=").append(candidates.size()).append('\n');
        for (int index = 0; index < candidates.size(); index++) {
            CandidateWindow window = candidates.get(index);
            text.append("candidateWindow=").append(index + 1)
                    .append(" @ 0x").append(Long.toHexString(window.address()))
                    .append(" bytes=").append(window.bytes())
                    .append(" anchorHits=").append(window.anchorHits());
            if (window.failure() != null) {
                text.append(" failure=").append(window.failure()).append('\n');
                continue;
            }
            var selection = window.selection();
            text.append(" outcome=").append(selection.outcome())
                    .append(" interpretations=").append(selection.candidates().size())
                    .append(" explanation=").append(selection.explanation()).append('\n');
            selection.candidates().stream().limit(5).forEach(candidate -> text
                    .append("  evidence stride=").append(candidate.strideBytes())
                    .append(" offset=").append(candidate.offsetBytes())
                    .append(" entries=").append(candidate.copies().size())
                    .append(" knownRatio=").append(String.format(Locale.ROOT, "%.3f", candidate.knownRatio()))
                    .append(" exactAnchors=").append(candidate.exactAnchors())
                    .append(" conflicts=").append(candidate.conflictingDuplicates())
                    .append(" score=").append(String.format(Locale.ROOT, "%.3f", candidate.score()))
                    .append(" rejected=").append(candidate.rejectionReasons()).append('\n'));
        }
        appendConsensus(text, consensus, config.knownArenaIds());
        return text.append("collectionExtraction=")
                .append(publicationEligible ? "COMPLETE" : "STRUCTURAL_EVIDENCE_ONLY").append('\n')
                .append("ownershipSemantics=")
                .append(publicationEligible ? "VALIDATED" : "UNVERIFIED").append('\n')
                .append("collectionPublication=")
                .append(publicationEligible ? "ELIGIBLE" : "DISABLED").append('\n').toString();
    }

    private static KnownDomainConsensus.Decision consensus(List<CandidateWindow> windows,
                                                            Set<Long> knownIds) {
        List<Map<Long, Integer>> accepted = windows.stream()
                .map(CandidateWindow::selection).filter(Objects::nonNull)
                .filter(selection -> selection.outcome() == CandidateBlockExtractor.Outcome.ACCEPTED)
                .map(CandidateBlockExtractor.Selection::selected)
                .map(CandidateBlockExtractor.Evidence::copies).toList();
        return new KnownDomainConsensus().decide(accepted, knownIds, 2);
    }

    private static void appendConsensus(StringBuilder text, KnownDomainConsensus.Decision decision,
                                        Set<Long> knownIds) {
        List<Map<Long, Integer>> accepted = decision.rawCandidates();
        text.append("acceptedCandidateWindows=").append(accepted.size()).append('\n');
        if (decision.outcome() == KnownDomainConsensus.Outcome.CONSENSUS) {
            text.append("globalCandidateOutcome=KNOWN_DOMAIN_CONSENSUS\n")
                    .append("consensusEntries=").append(decision.copies().size()).append('\n');
            appendQuantityEvidence(text, decision.copies());
        } else if (decision.outcome() == KnownDomainConsensus.Outcome.AMBIGUOUS) {
            text.append("globalCandidateOutcome=AMBIGUOUS\n");
        } else {
            text.append("globalCandidateOutcome=REJECTED\n");
        }
        if (accepted.isEmpty()) return;
        Map<Long, Integer> rawReference = accepted.getFirst();
        Map<Long, Integer> reference = KnownDomainConsensus.project(rawReference, knownIds);
        for (int index = 0; index < accepted.size(); index++) {
            Map<Long, Integer> candidate = accepted.get(index);
            Map<Long, Integer> projected = KnownDomainConsensus.project(candidate, knownIds);
            text.append("acceptedCandidate=").append(index + 1)
                    .append(" entries=").append(candidate.size())
                    .append(" knownEntries=").append(projected.size())
                    .append(" unknownEntries=").append(candidate.size() - projected.size())
                    .append(" rawDifferencesFromFirst=")
                    .append(differenceCount(rawReference, candidate))
                    .append(" knownDifferencesFromFirst=")
                    .append(differenceCount(reference, projected)).append('\n');
        }
    }

    private static void appendQuantityEvidence(StringBuilder text, Map<Long, Integer> copies) {
        QuantitySemanticsEvidence evidence = QuantitySemanticsEvidence.summarize(copies);
        text.append("quantitySemantics=UNVERIFIED\n")
                .append("quantityEntries=").append(evidence.entries()).append('\n')
                .append("quantityTotalCopies=").append(evidence.totalCopies()).append('\n')
                .append("quantity1=").append(evidence.oneCopy()).append('\n')
                .append("quantity2=").append(evidence.twoCopies()).append('\n')
                .append("quantity3=").append(evidence.threeCopies()).append('\n')
                .append("quantity4=").append(evidence.fourCopies()).append('\n')
                .append("quantity5to20=").append(evidence.fiveToTwentyCopies()).append('\n')
                .append("quantityAbove20=").append(evidence.aboveTwentyCopies()).append('\n')
                .append("quantityMaximum=").append(evidence.maximumCopies()).append('\n');
        for (Map.Entry<Long, Integer> entry : evidence.highestQuantities()) {
            text.append("highestQuantity=").append(entry.getKey()).append('x')
                    .append(entry.getValue()).append('\n');
        }
    }

    private static int differenceCount(Map<Long, Integer> first, Map<Long, Integer> second) {
        Set<Long> ids = new HashSet<>(first.keySet());
        ids.addAll(second.keySet());
        return (int) ids.stream().filter(id -> !Objects.equals(first.get(id), second.get(id))).count();
    }

    private static boolean ownershipShapeIsVerified(Map<Long, Integer> copies,
                                                     List<CandidateBlockExtractor.Anchor> anchors) {
        if (copies.isEmpty() || anchors.size() < 2) return false;
        if (copies.values().stream().anyMatch(quantity -> quantity < 1 || quantity > 4)) return false;
        return anchors.stream().allMatch(anchor ->
                Objects.equals(copies.get(anchor.arenaId()), anchor.copies()));
    }

    private record LocatedHit(long arenaId, int copies, long address,
                              WindowsProcessApi.MemoryRegion region) { }
    private record Discovery(long coveredBytes, long transportedBytes, int failedReads,
                             List<LocatedHit> hits) { }
    private record CandidateWindow(long address, int bytes, int anchorHits,
                                   CandidateBlockExtractor.Selection selection, String failure) { }
}
