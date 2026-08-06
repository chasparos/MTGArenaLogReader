package app.deckplanner.collection;

import java.time.Instant;
import java.util.Map;

public record ArenaCollectionSnapshot(Map<Long, Integer> ownedCopies,
                                      Instant observedAt,
                                      long sourceSequence,
                                      Source source) {
    public ArenaCollectionSnapshot {
        ownedCopies = Map.copyOf(ownedCopies);
        if (ownedCopies.isEmpty()) throw new IllegalArgumentException("Complete collection is empty");
        if (ownedCopies.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getKey() <= 0
                        || entry.getValue() == null || entry.getValue() <= 0)) {
            throw new IllegalArgumentException("Collection entries must be positive");
        }
        if (observedAt == null) throw new IllegalArgumentException("observedAt is null");
        if (sourceSequence < 0) throw new IllegalArgumentException("sourceSequence < 0");
        if (source == null) throw new IllegalArgumentException("source is null");
    }

    public enum Source {
        PLAYER_INVENTORY_GET_PLAYER_CARDS_V3,
        BARE_NUMERIC_CARD_MAP
    }
}
