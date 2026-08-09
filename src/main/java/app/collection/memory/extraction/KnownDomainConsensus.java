package app.collection.memory.extraction;

import java.util.*;

/** Fail-closed consensus over independently extracted raw ownership maps. */
public final class KnownDomainConsensus {
    public enum Outcome { REJECTED, CONSENSUS, AMBIGUOUS }
    public record Decision(Outcome outcome, Map<Long, Integer> copies,
                           List<Map<Long, Integer>> rawCandidates) {
        public Decision {
            copies = Map.copyOf(copies);
            rawCandidates = rawCandidates.stream().map(Map::copyOf).toList();
        }
    }

    public Decision decide(List<Map<Long, Integer>> candidates, Set<Long> knownIds,
                           int minimumIndependentCandidates) {
        List<Map<Long, Integer>> raw = List.copyOf(candidates);
        if (raw.size() < minimumIndependentCandidates) {
            return new Decision(Outcome.REJECTED, Map.of(), raw);
        }
        List<Map<Long, Integer>> projected = raw.stream()
                .map(candidate -> project(candidate, knownIds)).filter(candidate -> !candidate.isEmpty()).toList();
        if (projected.isEmpty()) return new Decision(Outcome.REJECTED, Map.of(), raw);
        Map<Map<Long, Integer>, Integer> support = new LinkedHashMap<>();
        projected.forEach(candidate -> support.merge(candidate, 1, Integer::sum));
        List<Map<Long, Integer>> independentlySupported = support.entrySet().stream()
                .filter(entry -> entry.getValue() >= minimumIndependentCandidates)
                .map(Map.Entry::getKey).toList();
        if (independentlySupported.isEmpty()) return new Decision(Outcome.AMBIGUOUS, Map.of(), raw);
        List<Map<Long, Integer>> maximal = independentlySupported.stream()
                .filter(candidate -> independentlySupported.stream()
                        .noneMatch(other -> other != candidate && strictlyDominates(other, candidate)))
                .toList();
        return maximal.size() == 1
                ? new Decision(Outcome.CONSENSUS, maximal.getFirst(), raw)
                : new Decision(Outcome.AMBIGUOUS, Map.of(), raw);
    }

    public static Map<Long, Integer> project(Map<Long, Integer> copies, Set<Long> knownIds) {
        Map<Long, Integer> projected = new LinkedHashMap<>();
        copies.forEach((id, quantity) -> {
            if (knownIds.contains(id)) projected.put(id, quantity);
        });
        return Map.copyOf(projected);
    }

    private static boolean strictlyDominates(Map<Long, Integer> newer, Map<Long, Integer> older) {
        boolean increased = false;
        Set<Long> ids = new HashSet<>(older.keySet());
        ids.addAll(newer.keySet());
        for (Long id : ids) {
            int before = older.getOrDefault(id, 0);
            int after = newer.getOrDefault(id, 0);
            if (after < before) return false;
            if (after > before) increased = true;
        }
        return increased;
    }
}
