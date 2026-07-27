package app.projection;

import app.model.card.CardInfo;
import app.model.game.CounterState;
import app.model.game.GameObjectState;
import app.model.game.GameState;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.function.IntPredicate;

import static app.projection.ArenaJson.arrayAt;
import static app.projection.ArenaJson.intAt;
import static app.projection.ArenaJson.longAt;
import static app.projection.ArenaJson.nullableInt;
import static app.projection.ArenaJson.objectAt;
import static app.projection.ArenaJson.stringAt;

/** Applies an Arena game-object snapshot to canonical object state. */
final class GameObjectProjector {
    record Observation(GameObjectState previous, GameObjectState current,
                       int previousSemanticZone, int incomingZone) {}

    private final GameState state;
    private final ObjectIdentityTracker identities;
    private final CounterProjector counters;
    private final TokenResolver tokens;
    private final Map<String, CardInfo> relatedCards;
    private final IntPredicate transientZone;

    GameObjectProjector(GameState state,
                        ObjectIdentityTracker identities,
                        CounterProjector counters,
                        TokenResolver tokens,
                        Map<String, CardInfo> relatedCards,
                        IntPredicate transientZone) {
        this.state = state;
        this.identities = identities;
        this.counters = counters;
        this.tokens = tokens;
        this.relatedCards = relatedCards;
        this.transientZone = transientZone;
    }

    Observation apply(JsonObject json, Map<Long, CardInfo> cards) {
        long instanceId = longAt(json, "instanceId", -1);
        if (instanceId < 0) return null;

        GameObjectState previous = state.getObjects().get(instanceId);
        GameObjectState current = identities.copyForObservation(instanceId);

        long previousGrpId = previous == null ? current.getGrpId() : previous.getGrpId();
        if (json.has("grpId")) current.setGrpId(longAt(json, "grpId", current.getGrpId()));
        CardFaceSelection face = selectCardFace(current.getGrpId(), cards, previous);
        if (face != null) {
            current.setActiveFaceIndex(face.index());
            current.setCard(face.card());
        }
        if (json.has("type")) current.setObjectType(stringAt(json, "type"));
        if (json.has("objectSourceGrpId")) {
            current.setObjectSourceGrpId(longAt(json, "objectSourceGrpId", 0));
        }
        if (json.has("parentId")) current.setParentId(longAt(json, "parentId", -1));
        if (json.has("ownerSeatId")) {
            current.setOwnerSeatId(intAt(json, "ownerSeatId", current.getOwnerSeatId()));
        }
        if (json.has("controllerSeatId")) {
            current.setControllerSeatId(
                    intAt(json, "controllerSeatId", current.getControllerSeatId()));
        }
        if (current.getControllerSeatId() < 0) {
            current.setControllerSeatId(current.getOwnerSeatId());
        }
        replaceStrings(json, "cardTypes", current.getCardTypes());
        replaceStrings(json, "subtypes", current.getSubtypes());
        replaceStrings(json, "color", current.getColors());
        replaceAbilities(json, current);
        replaceCounters(json, current);

        if (json.has("power")) current.setPower(nullableInt(objectAt(json, "power"), "value"));
        if (json.has("toughness")) {
            current.setToughness(nullableInt(objectAt(json, "toughness"), "value"));
        }
        // Arena gameObjects are full snapshots; an absent isTapped means untapped.
        current.setTapped(json.has("isTapped") && json.get("isTapped").getAsBoolean());
        applyCombatState(json, current);

        if (isToken(current) && current.getCard() == null) {
            current.setCard(tokens.resolve(current, cards, relatedCards));
        }

        int incomingZone = json.has("zoneId")
                ? intAt(json, "zoneId", current.getZoneId())
                : current.getZoneId();
        current.setZoneId(incomingZone);
        int previousSemanticZone = previous == null ? -1 : previous.getSemanticZoneId();
        if (!transientZone.test(incomingZone)) current.setSemanticZoneId(incomingZone);
        else if (previous != null) current.setSemanticZoneId(previousSemanticZone);
        state.getObjects().put(instanceId, current);
        return new Observation(previous, current, previousSemanticZone, incomingZone);
    }

    private void replaceStrings(JsonObject json, String key, java.util.List<String> target) {
        if (!json.has(key)) return;
        target.clear();
        for (JsonElement value : arrayAt(json, key)) target.add(clean(value.getAsString()));
    }

    private void replaceAbilities(JsonObject json, GameObjectState current) {
        if (!json.has("uniqueAbilities")) return;
        current.getUniqueAbilityGrpIds().clear();
        for (JsonElement element : arrayAt(json, "uniqueAbilities")) {
            if (!element.isJsonObject()) continue;
            long grpId = longAt(element.getAsJsonObject(), "grpId", -1);
            if (grpId > 0) current.getUniqueAbilityGrpIds().add(grpId);
        }
    }

    private void replaceCounters(JsonObject json, GameObjectState current) {
        if (!json.has("counters")) return;
        current.getCounters().clear();
        for (JsonElement element : arrayAt(json, "counters")) {
            if (!element.isJsonObject()) continue;
            JsonObject value = element.getAsJsonObject();
            CounterState counter = new CounterState();
            String type = stringAt(value, "type");
            if (type.isBlank()) type = stringAt(value, "counterType");
            int typeId = (int) longAt(value, "counterTypeId", longAt(value, "id", -1));
            counter.setArenaType(typeId);
            if (type.isBlank()) {
                type = typeId < 0 ? "Unknown" : counters.counterTypeName(typeId);
            }
            counter.setType(clean(type));
            counter.setCount(intAt(value, "count", intAt(value, "value", 1)));
            current.getCounters().add(counter);
        }
    }

    private void applyCombatState(JsonObject json, GameObjectState current) {
        if (json.has("attackState")) {
            current.setAttackState(stringAt(json, "attackState"));
            if (!current.getAttackState().endsWith("_Attacking")
                    && !current.getAttackState().endsWith("_Declared")) {
                current.setAttackTargetId(null);
            }
        }
        if (json.has("attackInfo")) {
            long targetId = longAt(objectAt(json, "attackInfo"), "targetId", -1);
            current.setAttackTargetId(targetId < 0 ? null : targetId);
        }
        if (json.has("blockState")) {
            current.setBlockState(stringAt(json, "blockState"));
            if (!current.getBlockState().endsWith("_Blocking")
                    && !current.getBlockState().endsWith("_Declared")) {
                current.getBlockedAttackerIds().clear();
            }
        }
        if (json.has("blockInfo")) {
            current.getBlockedAttackerIds().clear();
            current.getBlockedAttackerIds().addAll(
                    ArenaJson.longArray(objectAt(json, "blockInfo"), "attackerIds"));
        }
    }

    private boolean isToken(GameObjectState object) {
        return object.getObjectType() != null && object.getObjectType().contains("Token");
    }

    private CardFaceSelection selectCardFace(long grpId, Map<Long, CardInfo> cards,
                                             GameObjectState previous) {
        if (grpId <= 0) return null;
        for (CardInfo candidate : cards.values()) {
            if (candidate == null || !candidate.isMultiFaced()
                    || candidate.getArenaId() == null) continue;
            long offset = grpId - candidate.getArenaId();
            if (offset >= 0 && offset < candidate.getCardFaces().size()) {
                int index = Math.toIntExact(offset);
                return new CardFaceSelection(index, candidate.faceView(index, grpId));
            }
        }
        CardInfo direct = cards.get(grpId);
        if (direct != null && direct.isMultiFaced()) {
            return new CardFaceSelection(0, direct.faceView(0, grpId));
        }
        if (previous != null && previous.getCard() != null
                && previous.getCard().isMultiFaced()
                && previous.getGrpId() != grpId) {
            int index = previous.getActiveFaceIndex() == 0 ? 1 : 0;
            return new CardFaceSelection(index,
                    previous.getCard().faceView(index, grpId));
        }
        return direct == null ? null : new CardFaceSelection(0, direct);
    }

    private record CardFaceSelection(int index, CardInfo card) {}

    private String clean(String value) {
        if (value == null) return "";
        int underscore = value.indexOf('_');
        return underscore >= 0 ? value.substring(underscore + 1) : value;
    }
}
