package app.deck.model;

import java.util.List;

/**
 * Describes a change between two complete game-deck configurations.
 *
 * <p>Only complete deck snapshots may produce a {@link Confidence#RECONSTRUCTED}
 * change. Partial observations must not be promoted to sideboarding facts.</p>
 */
public record SideboardChange(
        String matchId,
        int gameNumber,
        List<DeckEntry> broughtIn,
        List<DeckEntry> removed,
        Confidence confidence
) {
    public SideboardChange {
        broughtIn = broughtIn == null ? List.of() : List.copyOf(broughtIn);
        removed = removed == null ? List.of() : List.copyOf(removed);
        confidence = confidence == null ? Confidence.UNKNOWN : confidence;
    }

    public boolean changed() {
        return !broughtIn.isEmpty() || !removed.isEmpty();
    }

    public enum Confidence {
        EXPLICIT,
        RECONSTRUCTED,
        INFERRED,
        UNKNOWN
    }
}
