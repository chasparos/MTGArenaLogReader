package app.collection.memory.extraction;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CandidateBlockExtractorTest {
    private static final CandidateBlockExtractor.Config CONFIG =
            new CandidateBlockExtractor.Config(1_000, 900_000, 1, 400,
                    5, 0, 0.70, 2, 0.08);
    private static final List<CandidateBlockExtractor.Anchor> ANCHORS = List.of(
            new CandidateBlockExtractor.Anchor(1101, 4),
            new CandidateBlockExtractor.Anchor(1102, 2),
            new CandidateBlockExtractor.Anchor(1103, 1));

    @Test
    void acceptsKnownAnchorBackedBlocksAtAllReferenceStrides() {
        for (int stride : List.of(8, 12, 16)) {
            byte[] fixture = block(stride, List.of(
                    pair(1101, 4), pair(1102, 2), pair(1103, 1),
                    pair(1104, 3), pair(1105, 4), pair(1106, 1)));
            Set<Long> known = known(1101, 1102, 1103, 1104, 1105, 1106);

            CandidateBlockExtractor.Selection selection =
                    new CandidateBlockExtractor(CONFIG).extract(fixture, known, ANCHORS);

            assertEquals(CandidateBlockExtractor.Outcome.ACCEPTED, selection.outcome(),
                    "stride=" + stride + " candidates=" + selection.candidates());
            assertEquals(stride, selection.selected().strideBytes());
            assertEquals(6, selection.selected().knownIds());
            assertEquals(3, selection.selected().exactAnchors());
        }
    }

    @Test
    void rejectsDecoysWithPlausibleNumbersButUnknownIds() {
        byte[] fixture = block(8, List.of(
                pair(700001, 4), pair(700002, 2), pair(700003, 1),
                pair(700004, 3), pair(700005, 4), pair(700006, 1)));

        CandidateBlockExtractor.Selection selection =
                new CandidateBlockExtractor(CONFIG).extract(fixture, known(1101, 1102), ANCHORS);

        assertEquals(CandidateBlockExtractor.Outcome.REJECTED, selection.outcome());
        assertTrue(selection.candidates().stream().anyMatch(candidate ->
                candidate.rejectionReasons().contains("known-ID ratio below threshold")));
    }

    @Test
    void rejectsTruncatedAndImplausibleQuantityFixtures() {
        byte[] truncated = block(8, List.of(
                pair(1101, 4), pair(1102, 2), pair(1103, 1), pair(1104, 3)));
        byte[] implausible = block(8, List.of(
                pair(1101, 4), pair(1102, 2), pair(1103, 9999),
                pair(1104, 3), pair(1105, 4), pair(1106, 1)));
        CandidateBlockExtractor extractor = new CandidateBlockExtractor(CONFIG);

        assertEquals(CandidateBlockExtractor.Outcome.REJECTED,
                extractor.extract(truncated, known(1101, 1102, 1103, 1104), ANCHORS).outcome());
        assertEquals(CandidateBlockExtractor.Outcome.REJECTED,
                extractor.extract(implausible, known(1101, 1102, 1103, 1104, 1105, 1106), ANCHORS).outcome());
    }

    @Test
    void rejectsConflictingDuplicateIds() {
        byte[] fixture = block(8, List.of(
                pair(1101, 4), pair(1102, 2), pair(1103, 1),
                pair(1104, 3), pair(1105, 4), pair(1105, 2)));

        CandidateBlockExtractor.Selection selection = new CandidateBlockExtractor(CONFIG)
                .extract(fixture, known(1101, 1102, 1103, 1104, 1105), ANCHORS);

        assertEquals(CandidateBlockExtractor.Outcome.REJECTED, selection.outcome());
        assertTrue(selection.candidates().stream().anyMatch(candidate ->
                candidate.rejectionReasons().contains("conflicting duplicate IDs")));
    }

    @Test
    void rejectsEquallyCredibleDifferentCandidatesAsAmbiguous() {
        List<int[]> first = List.of(pair(1101, 4), pair(1102, 2), pair(1103, 1),
                pair(1104, 3), pair(1105, 4), pair(1106, 1));
        List<int[]> second = List.of(pair(1101, 4), pair(1102, 2), pair(1103, 1),
                pair(2104, 3), pair(2105, 4), pair(2106, 1));
        byte[] fixture = join(block(8, first), new byte[24], block(8, second));

        CandidateBlockExtractor.Selection selection = new CandidateBlockExtractor(CONFIG)
                .extract(fixture, known(1101, 1102, 1103, 1104, 1105, 1106,
                        2104, 2105, 2106), ANCHORS);

        assertEquals(CandidateBlockExtractor.Outcome.AMBIGUOUS, selection.outcome());
        assertNull(selection.selected());
    }

    private static byte[] block(int stride, List<int[]> pairs) {
        byte[] bytes = new byte[pairs.size() * stride];
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int index = 0; index < pairs.size(); index++) {
            buffer.putInt(index * stride, pairs.get(index)[0]);
            buffer.putInt(index * stride + 4, pairs.get(index)[1]);
        }
        return bytes;
    }

    private static int[] pair(int id, int copies) { return new int[]{id, copies}; }
    private static Set<Long> known(long... ids) {
        Set<Long> values = new LinkedHashSet<>();
        for (long id : ids) values.add(id);
        return values;
    }
    private static byte[] join(byte[]... arrays) {
        int size = Arrays.stream(arrays).mapToInt(array -> array.length).sum();
        byte[] result = new byte[size];
        int offset = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }
}
