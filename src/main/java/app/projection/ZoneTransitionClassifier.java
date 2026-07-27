package app.projection;

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
        LEGEND_RULE,
        BATTLEFIELD_TO_GRAVEYARD,
        BATTLEFIELD_TO_EXILE,
        GRAVEYARD_TO_EXILE,
        GRAVEYARD_TO_BATTLEFIELD,
        GRAVEYARD_TO_HAND,
        GENERIC
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
