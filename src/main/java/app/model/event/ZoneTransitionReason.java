package app.model.event;

/** Machine-readable reason assigned to an observed zone transition. */
public enum ZoneTransitionReason {
    ABILITY_ON_STACK,
    ABILITY_FINISHED,
    PLAYED_LAND,
    CAST,
    DRAWN,
    RESOLVED_TO_BATTLEFIELD,
    RESOLVED_TO_GRAVEYARD,
    COUNTERED,
    LEGEND_RULE,
    PUT_INTO_GRAVEYARD,
    EXILED_FROM_BATTLEFIELD,
    EXILED_FROM_GRAVEYARD,
    RETURNED_TO_BATTLEFIELD,
    RETURNED_TO_HAND,
    UNKNOWN
}
