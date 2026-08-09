package app.collection.memory.extraction;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class KnownDomainConsensusTest {
    private static final Set<Long> KNOWN = Set.of(1001L, 1002L);

    @Test
    void requiresTwoIndependentCandidates() {
        var decision = new KnownDomainConsensus().decide(
                List.of(Map.of(1001L, 4)), KNOWN, 2);
        assertEquals(KnownDomainConsensus.Outcome.REJECTED, decision.outcome());
        assertTrue(decision.copies().isEmpty());
    }

    @Test
    void acceptsIdenticalKnownProjectionAndExcludesUnknownPairs() {
        var decision = new KnownDomainConsensus().decide(List.of(
                Map.of(1001L, 4, 1002L, 2, 9999L, 7),
                Map.of(1001L, 4, 1002L, 2)), KNOWN, 2);
        assertEquals(KnownDomainConsensus.Outcome.CONSENSUS, decision.outcome());
        assertEquals(Map.of(1001L, 4, 1002L, 2), decision.copies());
    }

    @Test
    void rejectsKnownQuantityDifferencesAsAmbiguous() {
        var decision = new KnownDomainConsensus().decide(List.of(
                Map.of(1001L, 4, 1002L, 2),
                Map.of(1001L, 3, 1002L, 2)), KNOWN, 2);
        assertEquals(KnownDomainConsensus.Outcome.AMBIGUOUS, decision.outcome());
        assertTrue(decision.copies().isEmpty());
    }

    @Test
    void selectsUniquelyNewerMonotonicGenerationWithIndependentSupport() {
        Map<Long, Integer> old = Map.of(1001L, 1, 1002L, 2);
        Map<Long, Integer> newer = Map.of(1001L, 2, 1002L, 2, 1003L, 1);
        var decision = new KnownDomainConsensus().decide(
                List.of(old, newer, old, newer, newer), Set.of(1001L, 1002L, 1003L), 2);
        assertEquals(KnownDomainConsensus.Outcome.CONSENSUS, decision.outcome());
        assertEquals(newer, decision.copies());
    }

    @Test
    void rejectsIndependentlySupportedNonMonotonicGenerations() {
        Map<Long, Integer> first = Map.of(1001L, 2, 1002L, 1);
        Map<Long, Integer> conflicting = Map.of(1001L, 1, 1002L, 2);
        var decision = new KnownDomainConsensus().decide(
                List.of(first, first, conflicting, conflicting), Set.of(1001L, 1002L), 2);
        assertEquals(KnownDomainConsensus.Outcome.AMBIGUOUS, decision.outcome());
        assertTrue(decision.copies().isEmpty());
    }
}
