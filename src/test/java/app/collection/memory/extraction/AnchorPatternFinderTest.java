package app.collection.memory.extraction;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnchorPatternFinderTest {
    @Test
    void findsExactLittleEndianPairsAndRejectsWrongQuantities() {
        byte[] bytes = new byte[40];
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(3, 67692).putInt(7, 2);
        buffer.putInt(19, 104942).putInt(23, 3);
        var anchors = List.of(new CandidateBlockExtractor.Anchor(67692, 2),
                new CandidateBlockExtractor.Anchor(104942, 4));

        var hits = new AnchorPatternFinder().find(bytes, anchors);

        assertEquals(1, hits.size());
        assertEquals(3, hits.getFirst().offset());
        assertEquals(67692, hits.getFirst().anchor().arenaId());
    }
}
