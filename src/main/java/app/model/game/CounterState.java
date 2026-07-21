package app.model.game;

import lombok.Data;

/** Canonical Arena counter information. Counter types are retained even when the UI does not render them yet. */
@Data
/**
 * Represents CounterState within the canonical per-game state and snapshot model.
 *
 * <p>Projection code creates or mutates this data from Arena observations; replay and export layers consume derived events rather than reparsing raw messages.</p>
 *
 * <p>Observed, reconstructed, and unknown information must remain distinguishable and conservative.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the canonical per-game state model used by projection before semantic events are presented.</p>
 */
public class CounterState {
    /** Raw Arena counter_type value. Unknown values are preserved. */
    private int arenaType = -1;
    /** Human-readable type when known, otherwise Counter#N. */
    private String type = "";
    private int count;

    public CounterState copy() {
        CounterState copy = new CounterState();
        copy.arenaType = arenaType;
        copy.type = type;
        copy.count = count;
        return copy;
    }
}
