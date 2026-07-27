package app.projection;

import app.model.card.CardInfo;
import app.model.game.GameObjectState;

import java.util.Map;

/** Converts canonical object lifecycle changes into semantic event descriptions. */
final class ObjectLifecycleEvents {
    interface Context {
        String zoneType(int zoneId);
        String playerName(int seatId);
        String objectName(GameObjectState object, Map<Long, CardInfo> cards);
        boolean isAbility(GameObjectState object);
        boolean isLand(GameObjectState object, Map<Long, CardInfo> cards);
        String abilityVerb(GameObjectState ability);
    }

    record Description(String text, boolean ability) {}

    private final Context context;
    private final ZoneTransitionClassifier classifier = new ZoneTransitionClassifier();
    private final ZoneEventProjector descriptions = new ZoneEventProjector();

    ObjectLifecycleEvents(Context context) {
        this.context = context;
    }

    Description newlyVisible(GameObjectState current, Map<Long, CardInfo> cards) {
        String zone = context.zoneType(current.getSemanticZoneId());
        if (!"Stack".equals(zone) && !"Battlefield".equals(zone)) return null;
        String actor = context.playerName(current.getControllerSeatId());
        String name = context.objectName(current, cards);

        if ("Stack".equals(zone)) {
            boolean ability = context.isAbility(current);
            String text = ability
                    ? actor + " " + context.abilityVerb(current) + " " + name
                    : actor + " casts " + name;
            return new Description(text, ability);
        }
        if (context.isAbility(current)) return null;
        String text = context.isLand(current, cards)
                ? actor + " plays " + name + tappedSuffix(current)
                : name + " entered the battlefield" + tappedSuffix(current)
                        + " under " + actor + "'s control";
        return new Description(text, false);
    }

    String transition(GameObjectState previous, GameObjectState current,
                      Map<Long, CardInfo> cards, String category) {
        String from = context.zoneType(previous.getSemanticZoneId());
        String to = context.zoneType(current.getSemanticZoneId());
        GameObjectState namedObject = "Battlefield".equals(from)
                && !"Battlefield".equals(to) ? previous : current;
        String name = context.objectName(namedObject, cards);
        String actor = context.playerName(current.getControllerSeatId());
        ZoneTransitionClassifier.Kind kind = classifier.classify(
                from,
                to,
                category,
                context.isAbility(current),
                context.isLand(current, cards));
        return descriptions.describe(
                kind,
                from,
                to,
                actor,
                name,
                context.abilityVerb(current),
                tappedSuffix(current));
    }

    private String tappedSuffix(GameObjectState object) {
        if (object.getTapped() == null) return "";
        return object.getTapped() ? " tapped" : " untapped";
    }
}
