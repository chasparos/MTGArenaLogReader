package app.projection;

import app.model.event.ZoneTransitionReason;

/**
 * Classifies an observed semantic zone transition without formatting a user-facing event.
 *
 * <p>This type owns only the ordering and precedence of the existing transition rules.
 * It must not read mutable game state, resolve card names, or emit {@code GameEvent}s.</p>
 */
final class ZoneTransitionClassifier {

    enum Kind {
        ABILITY_ON_STACK,
        ABILITY_FINISHED,
        PLAY_LAND,
        CAST_SPELL,
        DRAW,
        RESOLVE_TO_BATTLEFIELD,
        RESOLVE_TO_GRAVEYARD,
        COUNTERED,
        COUNTERED_TO_EXILE,
        LEGEND_RULE,
        BATTLEFIELD_TO_GRAVEYARD,
        BATTLEFIELD_TO_EXILE,
        GRAVEYARD_TO_EXILE,
        GRAVEYARD_TO_BATTLEFIELD,
        GRAVEYARD_TO_HAND,
        GENERIC
    }

    ZoneTransitionReason reason(Kind kind) {
        return switch (kind) {
            case ABILITY_ON_STACK -> ZoneTransitionReason.ABILITY_ON_STACK;
            case ABILITY_FINISHED -> ZoneTransitionReason.ABILITY_FINISHED;
            case PLAY_LAND -> ZoneTransitionReason.PLAYED_LAND;
            case CAST_SPELL -> ZoneTransitionReason.CAST;
            case DRAW -> ZoneTransitionReason.DRAWN;
            case RESOLVE_TO_BATTLEFIELD -> ZoneTransitionReason.RESOLVED_TO_BATTLEFIELD;
            case RESOLVE_TO_GRAVEYARD -> ZoneTransitionReason.RESOLVED_TO_GRAVEYARD;
            case COUNTERED, COUNTERED_TO_EXILE -> ZoneTransitionReason.COUNTERED;
            case LEGEND_RULE -> ZoneTransitionReason.LEGEND_RULE;
            case BATTLEFIELD_TO_GRAVEYARD -> ZoneTransitionReason.PUT_INTO_GRAVEYARD;
            case BATTLEFIELD_TO_EXILE -> ZoneTransitionReason.EXILED_FROM_BATTLEFIELD;
            case GRAVEYARD_TO_EXILE -> ZoneTransitionReason.EXILED_FROM_GRAVEYARD;
            case GRAVEYARD_TO_BATTLEFIELD -> ZoneTransitionReason.RETURNED_TO_BATTLEFIELD;
            case GRAVEYARD_TO_HAND -> ZoneTransitionReason.RETURNED_TO_HAND;
            case GENERIC -> ZoneTransitionReason.UNKNOWN;
        };
    }

    Kind classify(String from, String to, String category, boolean ability, boolean land) {
        if (ability) {
            return "Stack".equals(to) ? Kind.ABILITY_ON_STACK : Kind.ABILITY_FINISHED;
        }
        if ("PlayLand".equals(category)
                || ("Hand".equals(from) && "Battlefield".equals(to) && land)) {
            return Kind.PLAY_LAND;
        }
        if ("CastSpell".equals(category)
                || ("Hand".equals(from) && "Stack".equals(to))) {
            return Kind.CAST_SPELL;
        }
        if ("Draw".equals(category)
                || ("Library".equals(from) && "Hand".equals(to))) {
            return Kind.DRAW;
        }
        if ("Stack".equals(from) && "Battlefield".equals(to)) {
            return Kind.RESOLVE_TO_BATTLEFIELD;
        }
        String normalizedCategory = category == null ? "" : category.toLowerCase(java.util.Locale.ROOT);
        if ("Stack".equals(from) && "Graveyard".equals(to)) {
            if (normalizedCategory.contains("counter")) return Kind.COUNTERED;
            return Kind.RESOLVE_TO_GRAVEYARD;
        }
        if ("Stack".equals(from) && "Exile".equals(to)
                && normalizedCategory.contains("counter")) {
            return Kind.COUNTERED_TO_EXILE;
        }
        if ("Battlefield".equals(from) && "Graveyard".equals(to)) {
            if (normalizedCategory.contains("legend")) return Kind.LEGEND_RULE;
            return Kind.BATTLEFIELD_TO_GRAVEYARD;
        }
        if ("Battlefield".equals(from) && "Exile".equals(to)) {
            return Kind.BATTLEFIELD_TO_EXILE;
        }
        if ("Graveyard".equals(from) && "Exile".equals(to)) {
            return Kind.GRAVEYARD_TO_EXILE;
        }
        if ("Graveyard".equals(from) && "Battlefield".equals(to)) {
            return Kind.GRAVEYARD_TO_BATTLEFIELD;
        }
        if ("Graveyard".equals(from) && "Hand".equals(to)) {
            return Kind.GRAVEYARD_TO_HAND;
        }
        return Kind.GENERIC;
    }
}
