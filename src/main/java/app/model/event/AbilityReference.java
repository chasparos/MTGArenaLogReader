package app.model.event;

import lombok.Data;

@Data
/**
 * Represents AbilityReference at the immutable semantic-event boundary consumed by replay views and exports.
 *
 * <p>Projection creates these values after applying ordered Arena observations to canonical state.</p>
 *
 * <p>The type must not parse raw GRE records or contain Swing presentation behavior.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the immutable semantic-event boundary consumed by replay views and exporters.</p>
 */
public class AbilityReference {
    private long abilityGrpId;
    private long sourceGrpId;
    private String sourceName;
    private String kind;
    private Integer chapter;
    private String effectText;
    private String confidence = "UNKNOWN";

    public String key() { return sourceGrpId + ":" + abilityGrpId; }
}
