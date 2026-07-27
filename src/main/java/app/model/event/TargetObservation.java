package app.model.event;

import java.util.List;

/** Stable semantic references for one observed spell or ability target declaration. */
public record TargetObservation(
        ObjectReference source,
        long abilityGrpId,
        List<ObjectReference> targets,
        SemanticProvenance provenance,
        SemanticConfidence confidence) {

    public TargetObservation {
        targets = List.copyOf(targets == null ? List.of() : targets);
        provenance = provenance == null
                ? SemanticProvenance.ARENA_TARGET_DECLARATION : provenance;
        confidence = confidence == null ? SemanticConfidence.EXPLICIT : confidence;
    }
}
