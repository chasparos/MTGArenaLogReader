package app.collection.memory.extraction;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/** Finds exact little-endian Arena ID/copy anchor pairs in an arbitrary byte chunk. */
public final class AnchorPatternFinder {
    public record Hit(CandidateBlockExtractor.Anchor anchor, int offset) { }

    public List<Hit> find(byte[] bytes, List<CandidateBlockExtractor.Anchor> anchors) {
        Objects.requireNonNull(bytes);
        List<Hit> hits = new ArrayList<>();
        for (CandidateBlockExtractor.Anchor anchor : List.copyOf(anchors)) {
            byte[] pattern = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(Math.toIntExact(anchor.arenaId())).putInt(anchor.copies()).array();
            for (int offset = 0; offset <= bytes.length - pattern.length; offset++) {
                int index = 0;
                while (index < pattern.length && bytes[offset + index] == pattern[index]) index++;
                if (index == pattern.length) hits.add(new Hit(anchor, offset));
            }
        }
        return List.copyOf(hits);
    }
}
