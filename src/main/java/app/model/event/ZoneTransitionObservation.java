package app.model.event;

/**
 * Structured semantic observation of one Arena zone transition.
 *
 * <p>The reason describes only what can be established from the observed
 * transition and its Arena category. It does not guess the spell or ability
 * that caused the movement.</p>
 */
public record ZoneTransitionObservation(
        String fromZone,
        String toZone,
        ZoneTransitionReason reason,
        ObjectReference subject) {
}
