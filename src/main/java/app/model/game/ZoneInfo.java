package app.model.game;

import lombok.Data;

@Data
/**
 * Represents ZoneInfo within the canonical per-game state and snapshot model.
 *
 * <p>Projection code creates or mutates this data from Arena observations; replay and export layers consume derived events rather than reparsing raw messages.</p>
 *
 * <p>Observed, reconstructed, and unknown information must remain distinguishable and conservative.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the canonical per-game state model used by projection before semantic events are presented.</p>
 */
public class ZoneInfo {
    private int zoneId;
    private String type;
    private Integer ownerSeatId;
    private int objectCount = -1;

    public String displayName() {
        if (type == null || type.isBlank()) {
            return "Zone " + zoneId;
        }
        String value = type.startsWith("ZoneType_") ? type.substring("ZoneType_".length()) : type;
        return value.replace('_', ' ');
    }
}
