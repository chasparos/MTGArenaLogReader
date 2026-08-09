package app.collection.memory.extraction;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuantitySemanticsEvidenceTest {
    @Test
    void summarizesOwnershipShapedQuantitiesWithoutPublishingThem() {
        Map<Long, Integer> copies = new LinkedHashMap<>();
        copies.put(10L, 1);
        copies.put(20L, 2);
        copies.put(30L, 3);
        copies.put(40L, 4);
        copies.put(50L, 7);
        copies.put(60L, 25);

        QuantitySemanticsEvidence evidence = QuantitySemanticsEvidence.summarize(copies);

        assertEquals(6, evidence.entries());
        assertEquals(42, evidence.totalCopies());
        assertEquals(1, evidence.oneCopy());
        assertEquals(1, evidence.twoCopies());
        assertEquals(1, evidence.threeCopies());
        assertEquals(1, evidence.fourCopies());
        assertEquals(1, evidence.fiveToTwentyCopies());
        assertEquals(1, evidence.aboveTwentyCopies());
        assertEquals(25, evidence.maximumCopies());
        assertEquals(Map.entry(60L, 25), evidence.highestQuantities().getFirst());
    }
}
