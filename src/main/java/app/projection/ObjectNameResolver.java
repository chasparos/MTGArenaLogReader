package app.projection;

import app.model.card.CardInfo;
import app.model.event.GameEvent;
import app.model.game.GameObjectState;
import app.model.game.GameState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Resolves display names for Arena objects and retains names after transient
 * objects disappear. It also repairs placeholder text when enrichment arrives.
 */
final class ObjectNameResolver {
    private final GameState state;
    private final ObjectIdentityTracker identities;
    private final AbilityNameStore abilityNames;
    private final TokenResolver tokens;
    private final Map<Long, GameObjectState> observedCards;
    private final List<GameEvent> emittedEvents;
    private final IntFunction<String> playerName;
    private final Map<Long, String> historicalObjects = new LinkedHashMap<>();
    private final Map<Long, String> historicalAbilityOwners = new LinkedHashMap<>();

    ObjectNameResolver(
            GameState state,
            ObjectIdentityTracker identities,
            AbilityNameStore abilityNames,
            TokenResolver tokens,
            Map<Long, GameObjectState> observedCards,
            List<GameEvent> emittedEvents,
            IntFunction<String> playerName) {
        this.state = state;
        this.identities = identities;
        this.abilityNames = abilityNames;
        this.tokens = tokens;
        this.observedCards = observedCards;
        this.emittedEvents = emittedEvents;
        this.playerName = playerName;
    }

    String displayName(long instanceId, Map<Long, CardInfo> cards) {
        GameObjectState object = state.getObjects().get(instanceId);
        return object == null ? "object " + instanceId : displayName(object, cards);
    }

    String targetName(long id, Map<Long, CardInfo> cards) {
        GameObjectState object = state.getObjects().get(id);
        if (object != null) return displayName(object, cards);
        if (state.getPlayers().containsKey((int) id)) return playerName.apply((int) id);
        return "object " + id;
    }

    String displayName(GameObjectState object, Map<Long, CardInfo> cards) {
        if (isAbility(object)) {
            String source = cardName(object.getObjectSourceGrpId(), cards);
            String learned = abilityNames.find(
                    object.getObjectSourceGrpId(), object.getGrpId());
            String kind = state.getActivatedAbilityInstances().contains(
                    object.getInstanceId()) ? "activated"
                    : state.getTriggeredAbilityInstances().contains(
                            object.getInstanceId()) ? "triggered"
                    : "unknown";
            String inferred = AbilityHeuristics.infer(
                    cardForGrpId(object.getObjectSourceGrpId(), cards), kind);
            String label = !learned.isBlank() ? learned : inferred;
            return label.isBlank() ? source : source + " \u2014 " + label;
        }
        if (object.getCard() != null
                && object.getCard().getName() != null
                && !object.getCard().getName().isBlank()) {
            return object.getCard().getName();
        }
        if (isToken(object)) return tokens.descriptiveName(object);
        String resolved = cardName(object.getGrpId(), cards);
        return resolved.startsWith("ArenaCard#")
                ? observedDescription(object)
                : resolved;
    }

    String sourceName(long sourceId, long abilityGrpId, Map<Long, CardInfo> cards) {
        GameObjectState source = state.getObjects().get(sourceId);
        if (source != null) return displayName(source, cards);
        if (historicalObjects.containsKey(sourceId)) {
            return historicalObjects.get(sourceId);
        }
        if (abilityGrpId > 0 && historicalAbilityOwners.containsKey(abilityGrpId)) {
            return historicalAbilityOwners.get(abilityGrpId);
        }
        GameObjectState owner = abilityGrpId > 0
                ? findObjectOwningAbilityGroup(abilityGrpId)
                : null;
        if (owner != null) return displayName(owner, cards);
        return abilityGrpId > 0
                ? "Unknown spell or ability [Arena ability #" + abilityGrpId + "]"
                : "Unknown spell or ability";
    }

    void remember(long instanceId, GameObjectState object, Map<Long, CardInfo> cards) {
        String name = displayName(object, cards);
        if (name.isBlank() || name.startsWith("ArenaCard#")) return;
        historicalObjects.put(instanceId, name);
        for (long abilityGrpId : object.getUniqueAbilityGrpIds()) {
            historicalAbilityOwners.put(abilityGrpId, name);
        }
    }

    void repairPreviouslyUnknownNames(Map<Long, CardInfo> newlyKnown) {
        if (newlyKnown == null || newlyKnown.isEmpty() || emittedEvents.isEmpty()) return;
        for (Map.Entry<Long, CardInfo> entry : newlyKnown.entrySet()) {
            CardInfo card = entry.getValue();
            if (card == null || card.getName() == null || card.getName().isBlank()) continue;
            String placeholder = "ArenaCard#" + entry.getKey();
            for (GameEvent event : emittedEvents) {
                String text = event.getText();
                if (text != null && text.contains(placeholder)) {
                    event.setText(text.replace(placeholder, card.getName()));
                }
            }
        }
    }

    String cardName(long grpId, Map<Long, CardInfo> cards) {
        CardInfo card = cardForGrpId(grpId, cards);
        if (card != null && card.getName() != null && !card.getName().isBlank()) {
            return card.getName();
        }
        GameObjectState observed = observedCards.get(grpId);
        return observed == null
                ? "ArenaCard#" + grpId
                : observedDescription(observed);
    }

    CardInfo cardForGrpId(long grpId, Map<Long, CardInfo> cards) {
        CardInfo direct = cards.get(grpId);
        if (direct != null) {
            if (direct.isMultiFaced()) return direct.faceView(0, grpId);
            return direct;
        }
        for (CardInfo candidate : cards.values()) {
            if (candidate == null || !candidate.isMultiFaced()
                    || candidate.getArenaId() == null) continue;
            long offset = grpId - candidate.getArenaId();
            if (offset >= 0 && offset < candidate.getCardFaces().size()) {
                return candidate.faceView(Math.toIntExact(offset), grpId);
            }
        }
        return null;
    }

    String observedDescription(GameObjectState object) {
        if (object == null) return "Unknown card";
        StringBuilder out = new StringBuilder("Unknown");
        if (!object.getColors().isEmpty()) {
            out.append(' ').append(String.join("/", object.getColors()).toLowerCase());
        }
        if (!object.getSubtypes().isEmpty()) {
            out.append(' ').append(String.join(" ", object.getSubtypes()));
        }
        if (!object.getCardTypes().isEmpty()) {
            out.append(' ').append(
                    String.join(" ", object.getCardTypes()).toLowerCase());
        } else if (object.getObjectType() != null
                && !object.getObjectType().isBlank()) {
            out.append(' ').append(clean(object.getObjectType()).toLowerCase());
        } else {
            out.append(" card");
        }
        if (object.getPower() != null && object.getToughness() != null) {
            out.append(" (").append(object.getPower()).append('/')
                    .append(object.getToughness()).append(')');
        }
        if (object.getGrpId() > 0) {
            out.append(" [Arena #").append(object.getGrpId()).append(']');
        }
        return out.toString();
    }

    void clearHistory() {
        historicalObjects.clear();
        historicalAbilityOwners.clear();
    }

    private GameObjectState findObjectOwningAbilityGroup(long abilityGrpId) {
        return state.getObjects().values().stream()
                .filter(object -> object.getUniqueAbilityGrpIds().contains(abilityGrpId))
                .filter(identities::isCurrent)
                .findFirst()
                .orElseGet(() -> state.getObjects().values().stream()
                        .filter(object -> object.getUniqueAbilityGrpIds()
                                .contains(abilityGrpId))
                        .findFirst().orElse(null));
    }

    private boolean isAbility(GameObjectState object) {
        return "GameObjectType_Ability".equals(object.getObjectType());
    }

    private boolean isToken(GameObjectState object) {
        return object.getObjectType() != null
                && object.getObjectType().contains("Token");
    }

    private String clean(String value) {
        if (value == null) return "";
        int underscore = value.indexOf('_');
        return underscore >= 0 ? value.substring(underscore + 1) : value;
    }
}
