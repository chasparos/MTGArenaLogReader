package app.projection;

/**
 * Formats classified semantic zone transitions as user-facing event descriptions.
 *
 * <p>This collaborator owns only the existing wording for zone-transition events.
 * It must not mutate game state, inspect Arena JSON, resolve card data, or emit
 * {@code GameEvent} instances.</p>
 */
final class ZoneEventProjector {
    private final ZoneTransitionClassifier classifier;

    ZoneEventProjector(ZoneTransitionClassifier classifier) {
        this.classifier = classifier;
    }

    String describe(String from,
                    String to,
                    String category,
                    boolean ability,
                    boolean land,
                    String actor,
                    String name,
                    String abilityVerb,
                    String tappedSuffix) {
        ZoneTransitionClassifier.Kind kind =
                classifier.classify(from, to, category, ability, land);

        return switch (kind) {
            case ABILITY_ON_STACK -> actor + " " + abilityVerb + " " + name;
            case ABILITY_FINISHED -> name + " finishes resolving";
            case PLAY_LAND -> actor + " plays " + name + tappedSuffix;
            case CAST_SPELL -> actor + " casts " + name;
            case DRAW -> actor + " draws " + name;
            case RESOLVE_TO_BATTLEFIELD ->
                    name + " resolves and enters the battlefield" + tappedSuffix;
            case RESOLVE_TO_GRAVEYARD -> name + " resolves and is put into the graveyard";
            case BATTLEFIELD_TO_GRAVEYARD -> name + " is put into the graveyard";
            case BATTLEFIELD_TO_EXILE -> name + " is exiled";
            case GRAVEYARD_TO_EXILE -> name + " is exiled from the graveyard";
            case GRAVEYARD_TO_BATTLEFIELD ->
                    name + " returns from the graveyard to the battlefield" + tappedSuffix;
            case GRAVEYARD_TO_HAND -> actor + " returns " + name + " from the graveyard to hand";
            case GENERIC -> actor + ": " + name + " moved " + from + " → " + to;
        };
    }
}
