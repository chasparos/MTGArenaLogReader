package app.collection.memory.extraction;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/** Pure byte-fixture extractor and fail-closed candidate selector. Performs no process access. */
public final class CandidateBlockExtractor {
    public record Anchor(long arenaId, int copies) { }

    public record Config(long minimumArenaId, long maximumArenaId,
                         int minimumCopies, int maximumCopies,
                         int minimumEntries, int maximumGapRecords,
                         double minimumKnownRatio, int minimumExactAnchors,
                         double ambiguityMargin) {
        public static Config conservative() {
            return new Config(1_000, 900_000, 1, 400,
                    50, 64, 0.70, 2, 0.08);
        }
    }

    public record Evidence(Map<Long, Integer> copies, int strideBytes, int offsetBytes,
                           int knownIds, int exactAnchors, int anchorIds,
                           int conflictingDuplicates, double score,
                           List<String> rejectionReasons) {
        public Evidence {
            copies = Map.copyOf(copies);
            rejectionReasons = List.copyOf(rejectionReasons);
        }
        public double knownRatio() {
            return copies.isEmpty() ? 0 : (double) knownIds / copies.size();
        }
        public boolean valid() { return rejectionReasons.isEmpty(); }
    }

    public enum Outcome { ACCEPTED, AMBIGUOUS, REJECTED }

    public record Selection(Outcome outcome, Evidence selected,
                            List<Evidence> candidates, String explanation) {
        public Selection { candidates = List.copyOf(candidates); }
    }

    private final Config config;

    public CandidateBlockExtractor(Config config) {
        this.config = Objects.requireNonNull(config);
    }

    public Selection extract(byte[] bytes, Set<Long> knownIds, List<Anchor> anchors) {
        Objects.requireNonNull(bytes);
        Set<Long> known = Set.copyOf(knownIds == null ? Set.of() : knownIds);
        List<Anchor> anchorList = List.copyOf(anchors == null ? List.of() : anchors);
        List<RawCandidate> raw = new ArrayList<>();
        for (int strideWords : List.of(2, 3, 4)) {
            for (int offsetWords = 0; offsetWords < strideWords; offsetWords++) {
                extractAt(bytes, strideWords, offsetWords, raw);
            }
        }
        List<Evidence> scored = raw.stream()
                .map(candidate -> score(candidate, known, anchorList))
                .toList();
        Map<Map<Long, Integer>, Evidence> distinctInterpretations = new LinkedHashMap<>();
        for (Evidence candidate : scored) {
            distinctInterpretations.merge(candidate.copies(), candidate,
                    (existing, replacement) -> existing.score() >= replacement.score()
                            ? existing : replacement);
        }
        List<Evidence> evidence = distinctInterpretations.values().stream()
                .sorted(Comparator.comparingDouble(Evidence::score).reversed()
                        .thenComparing(Comparator.comparingInt(
                                (Evidence item) -> item.copies().size()).reversed()))
                .toList();
        List<Evidence> valid = evidence.stream().filter(Evidence::valid).toList();
        if (valid.isEmpty()) {
            return new Selection(Outcome.REJECTED, null, evidence,
                    "No candidate satisfied the confidence thresholds");
        }
        Evidence best = valid.get(0);
        if (valid.size() > 1) {
            Evidence second = valid.get(1);
            if (!best.copies().equals(second.copies())
                    && best.score() - second.score() < config.ambiguityMargin()) {
                return new Selection(Outcome.AMBIGUOUS, null, evidence,
                        "Top candidates are within ambiguity margin: "
                                + format(best.score() - second.score()));
            }
        }
        return new Selection(Outcome.ACCEPTED, best, evidence,
                "Accepted unique highest-confidence candidate");
    }

    private void extractAt(byte[] bytes, int strideWords, int offsetWords,
                           List<RawCandidate> target) {
        int strideBytes = strideWords * Integer.BYTES;
        int position = offsetWords * Integer.BYTES;
        LinkedHashMap<Long, Integer> current = new LinkedHashMap<>();
        int conflicts = 0;
        int misses = 0;
        int start = position;
        while (position + 8 <= bytes.length) {
            long id = Integer.toUnsignedLong(readInt(bytes, position));
            long copies = Integer.toUnsignedLong(readInt(bytes, position + 4));
            if (validPair(id, copies)) {
                Integer previous = current.putIfAbsent(id, (int) copies);
                if (previous != null && previous != (int) copies) conflicts++;
                misses = 0;
            } else {
                misses++;
                if (misses > config.maximumGapRecords()) {
                    flush(target, current, strideBytes, start, conflicts);
                    current = new LinkedHashMap<>();
                    conflicts = 0;
                    misses = 0;
                    start = position + strideBytes;
                }
            }
            position += strideBytes;
        }
        flush(target, current, strideBytes, start, conflicts);
    }

    private void flush(List<RawCandidate> target, LinkedHashMap<Long, Integer> current,
                       int strideBytes, int offsetBytes, int conflicts) {
        if (current.size() >= config.minimumEntries()) {
            target.add(new RawCandidate(Map.copyOf(current), strideBytes, offsetBytes, conflicts));
        }
    }

    private Evidence score(RawCandidate candidate, Set<Long> known, List<Anchor> anchors) {
        int knownCount = (int) candidate.copies().keySet().stream().filter(known::contains).count();
        int exact = 0;
        int anchorIds = 0;
        for (Anchor anchor : anchors) {
            Integer copies = candidate.copies().get(anchor.arenaId());
            if (copies != null) {
                anchorIds++;
                if (copies == anchor.copies()) exact++;
            }
        }
        double knownRatio = candidate.copies().isEmpty()
                ? 0 : (double) knownCount / candidate.copies().size();
        double anchorDenominator = Math.max(1, anchors.size());
        double sizeScore = Math.min(1.0, (double) candidate.copies().size() / 5_000);
        double score = knownRatio * 0.55
                + (exact / anchorDenominator) * 0.30
                + (anchorIds / anchorDenominator) * 0.10
                + sizeScore * 0.05
                - Math.min(0.25, candidate.conflicts() * 0.05);
        List<String> rejected = new ArrayList<>();
        if (candidate.copies().size() < config.minimumEntries()) rejected.add("too few entries");
        if (knownRatio < config.minimumKnownRatio()) rejected.add("known-ID ratio below threshold");
        if (exact < Math.min(config.minimumExactAnchors(), anchors.size())) {
            rejected.add("insufficient exact anchors");
        }
        if (candidate.conflicts() > 0) rejected.add("conflicting duplicate IDs");
        return new Evidence(candidate.copies(), candidate.strideBytes(), candidate.offsetBytes(),
                knownCount, exact, anchorIds, candidate.conflicts(), score, rejected);
    }

    private boolean validPair(long id, long copies) {
        return id >= config.minimumArenaId() && id < config.maximumArenaId()
                && copies >= config.minimumCopies() && copies <= config.maximumCopies();
    }

    private static int readInt(byte[] bytes, int offset) {
        return ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private record RawCandidate(Map<Long, Integer> copies, int strideBytes,
                                int offsetBytes, int conflicts) { }
}
