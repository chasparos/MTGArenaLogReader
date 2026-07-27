package app.model.event;

import java.util.List;

/** Stable semantic references for one observed spell or ability target declaration. */
public record TargetObservation(
        ObjectReference source,
        long abilityGrpId,
        List<ObjectReference> targets,
        Confidence confidence) {

    public TargetObservation {
        targets = List.copyOf(targets == null ? List.of() : targets);
    }

    public enum Confidence {
        EXPLICIT
    }
}
