package app.model.event;

import java.util.List;

/**
 * An Arena-observed player choice together with the legal alternatives Arena
 * presented at that moment.
 *
 * <p>This is observation, not strategic evaluation. Alternatives are emitted
 * only when Arena supplied the complete selectable set.</p>
 */
public record DecisionObservation(
        Kind kind,
        ObjectReference source,
        List<ObjectReference> selected,
        List<ObjectReference> alternatives,
        int minimumSelections,
        int maximumSelections,
        Confidence confidence) {

    public DecisionObservation {
        selected = List.copyOf(selected == null ? List.of() : selected);
        alternatives = List.copyOf(alternatives == null ? List.of() : alternatives);
    }

    public enum Kind {
        TARGET
    }

    public enum Confidence {
        EXPLICIT
    }
}
