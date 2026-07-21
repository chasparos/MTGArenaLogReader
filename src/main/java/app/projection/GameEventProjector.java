package app.projection;

import app.model.event.AbilityReference;
import app.model.card.CardInfo;
import app.model.event.GameEvent;
import app.model.game.GameObjectState;
import app.model.game.GameState;
import app.model.InformationBundle;
import app.model.log.LogMessageInterface;
import app.model.log.ModelObject;
import app.model.game.PlayerTurnSnapshot;
import app.model.card.CardRelatedPart;
import app.model.game.CounterState;
import app.model.game.BoardPermanentSnapshot;
import app.model.game.GameResult;
import app.model.game.CombatAttackAssignment;
import app.model.game.CombatBlockAssignment;
import app.model.game.ZoneInfo;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Projects Arena GRE state and annotations into human-readable events.
 * <p><strong>Architectural role:</strong> This type belongs to the projection boundary between ordered Arena observations, canonical game state, and immutable semantic events.</p>
 */
public final class GameEventProjector {
    private final GameState state = new GameState();
    private final Map<Long, CardInfo> knownCards = new LinkedHashMap<>();
    /** Arena-observed metadata retained even when external card enrichment misses. */
    private final Map<Long, GameObjectState> observedCardsByGrpId = new LinkedHashMap<>();
    /** Previously emitted mutable events, used to replace ArenaCard placeholders when enrichment arrives later. */
    private final List<GameEvent> emittedEvents = new ArrayList<>();
    private final Map<String, CardInfo> knownRelatedCards = new LinkedHashMap<>();
    /** Names retained after transient Stack/Limbo objects disappear. */
    private final Map<Long, String> historicalObjectNames = new LinkedHashMap<>();
    /** Arena ability group id -> owning card name. */
    private final Map<Long, String> historicalAbilityOwnerNames = new LinkedHashMap<>();
    /** Persistent annotation id -> attachment relation (attached object -> host object). */
    private final Map<Long, AttachmentRelation> attachmentsByAnnotationId = new LinkedHashMap<>();
    /** Delay turn snapshots until a post-untap state update for that turn. */
    private Integer pendingTurnSnapshot;
    private boolean pendingTurnSnapshotNeedsNextMessage;

    /*
     * A cast is only final once Arena moves the object to the Stack.  Until
     * then it may be in Limbo while targets, modes or payments are selected.
     * Keeping this tiny correlation record lets a Limbo -> Hand rollback
     * become a semantic cancellation instead of a misleading zone movement.
     */
    private final Map<Long, PendingCast> pendingCasts = new LinkedHashMap<>();
    private final AbilityNameStore abilityNames;
    private final OpeningHandTracker openingHandTracker = new OpeningHandTracker();

    private record PendingCast(long instanceId, long grpId, int seatId, String name) {}
    private record AttachmentRelation(long attachedLogicalId, long hostLogicalId) {}

    public GameEventProjector() { this(new AbilityNameStore()); }
    public GameEventProjector(AbilityNameStore abilityNames) { this.abilityNames = abilityNames; }

    public String openingHandPlayer() {
        return state.getOpeningHandSeat() < 0 ? null : playerName(state.getOpeningHandSeat());
    }

    public int mulliganCount() { return state.getMulliganCount(); }

    public List<CardInfo> openingHand() {
        List<Long> ids = state.getOpeningHandGrpIds().getOrDefault(state.getOpeningHandSeat(), List.of());
        return ids.stream().map(knownCards::get).filter(java.util.Objects::nonNull).toList();
    }

    public List<GameEvent> project(LogMessageInterface message, ModelObject model) {
        String raw = message.getRawText().strip();
        if (!raw.startsWith("{")) return List.of();

        try {
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            Map<Long, CardInfo> cards = cards(model);
            knownCards.putAll(cards);
            repairPreviouslyUnknownNames(cards);
            if (model instanceof InformationBundle bundle) knownRelatedCards.putAll(bundle.getRelatedCards());
            cards = knownCards;
            List<GameEvent> result = new ArrayList<>();

            if (root.has("matchGameRoomStateChangedEvent")) {
                projectRoomState(message, root.getAsJsonObject("matchGameRoomStateChangedEvent"), result);
            }
            if (root.has("payload")) {
                projectClientMessage(message, root.getAsJsonObject("payload"), cards, result);
            }
            if (root.has("greToClientEvent")) {
                projectGreEvent(message, root.getAsJsonObject("greToClientEvent"), cards, result);
            }
            emittedEvents.addAll(result);
            return result;
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }


    /**
     * Watches the player's outgoing GRE responses.  ActionType_Cast means
     * "the player has begun the casting flow", not yet "the spell was cast".
     * Confirmation still comes from the canonical zone transition to Stack.
     */
    private void projectClientMessage(LogMessageInterface source,
                                      JsonObject payload,
                                      Map<Long, CardInfo> cards,
                                      List<GameEvent> result) {
        String type = stringAt(payload, "type");

        if ("ClientMessageType_PerformActionResp".equals(type)) {
            for (JsonElement element : arrayAt(objectAt(payload, "performActionResp"), "actions")) {
                if (!element.isJsonObject()) continue;
                JsonObject action = element.getAsJsonObject();
                String actionType = stringAt(action, "actionType");

                if ("ActionType_Cast".equals(actionType)) {
                    long instanceId = longAt(action, "instanceId", -1);
                    long grpId = longAt(action, "grpId", -1);
                    if (instanceId < 0) continue;

                    GameObjectState object = state.getObjects().get(instanceId);
                    int seatId = object == null ? -1 : object.getControllerSeatId();
                    String name = object == null
                            ? cardName(grpId, cards)
                            : objectDisplayName(object, cards);
                    pendingCasts.put(instanceId,
                            new PendingCast(instanceId, grpId, seatId, name));
                } else if (actionType.contains("Cancel")) {
                    cancelMostRecentPendingCast(source, result);
                }
            }
            return;
        }

        /*
         * Arena versions have used SelectAction_Cancel in target/mode
         * responses.  Search only response payloads that belong to an active
         * casting flow; do not treat UI/settings strings containing "Cancel"
         * as gameplay.
         */
        if (type.endsWith("Resp")
                && (type.contains("Target")
                || type.contains("Select")
                || type.contains("Optional")
                || type.contains("Casting"))) {
            if (containsCancelAction(payload)) {
                cancelMostRecentPendingCast(source, result);
            }
        }
    }

    private boolean containsCancelAction(JsonElement element) {
        if (element == null || element.isJsonNull()) return false;
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String value = element.getAsString();
            return value.startsWith("ActionType_Cancel")
                    || value.startsWith("SelectAction_Cancel")
                    || value.equals("Cancel");
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (containsCancelAction(child)) return true;
            }
            return false;
        }
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                if (containsCancelAction(entry.getValue())) return true;
            }
        }
        return false;
    }

    private void cancelMostRecentPendingCast(LogMessageInterface source,
                                             List<GameEvent> result) {
        PendingCast pending = null;
        for (PendingCast candidate : pendingCasts.values()) pending = candidate;
        if (pending == null) return;
        pendingCasts.remove(pending.instanceId());
        result.add(cancelledCastEvent(source, pending));
    }

    private GameEvent cancelledCastEvent(LogMessageInterface source, PendingCast pending) {
        String actor = pending.seatId() < 0
                ? "Player"
                : playerName(pending.seatId());
        return event(source, actor + " cancels casting " + pending.name());
    }

    private PendingCast removePendingCastFor(long instanceId, GameObjectState object) {
        PendingCast direct = pendingCasts.remove(instanceId);
        if (direct != null) return direct;

        long logicalId = state.getLogicalIds().getOrDefault(instanceId, instanceId);
        for (Map.Entry<Long, PendingCast> entry : new ArrayList<>(pendingCasts.entrySet())) {
            long pendingLogicalId = state.getLogicalIds()
                    .getOrDefault(entry.getKey(), entry.getKey());
            PendingCast candidate = entry.getValue();
            boolean sameLogicalObject = pendingLogicalId == logicalId;
            boolean sameKnownCard = object != null
                    && object.getGrpId() > 0
                    && candidate.grpId() > 0
                    && object.getGrpId() == candidate.grpId();
            if (sameLogicalObject || sameKnownCard) {
                pendingCasts.remove(entry.getKey());
                return candidate;
            }
        }
        return null;
    }

    private void projectRoomState(LogMessageInterface message, JsonObject event, List<GameEvent> result) {
        JsonObject config = objectAt(event, "gameRoomInfo", "gameRoomConfig");
        String incomingMatchId = stringAt(config, "matchId");
        if (!incomingMatchId.isBlank() && !incomingMatchId.equals(state.getMatchId())) {
            state.reset(incomingMatchId);
            historicalObjectNames.clear();
            observedCardsByGrpId.clear();
            emittedEvents.clear();
            historicalAbilityOwnerNames.clear();
            pendingCasts.clear();
            attachmentsByAnnotationId.clear();
            pendingTurnSnapshot = null;
            pendingTurnSnapshotNeedsNextMessage = false;
            for (JsonElement element : arrayAt(config, "reservedPlayers")) {
                if (!element.isJsonObject()) continue;
                JsonObject player = element.getAsJsonObject();
                int seat = intAt(player, "systemSeatId", -1);
                if (seat >= 0) state.getPlayers().put(seat, stringAt(player, "playerName"));
            }
            result.add(event(message, "Match started"));
        }
    }

    private void projectGreEvent(LogMessageInterface message, JsonObject greEvent,
                                 Map<Long, CardInfo> cards, List<GameEvent> result) {
        for (JsonElement element : arrayAt(greEvent, "greToClientMessages")) {
            if (!element.isJsonObject()) continue;
            JsonObject greMessage = element.getAsJsonObject();
            String type = stringAt(greMessage, "type");
            if ("GREMessageType_GameStateMessage".equals(type)
                    || "GREMessageType_QueuedGameStateMessage".equals(type)) {
                projectGameState(message, objectAt(greMessage, "gameStateMessage"), cards, result);
            } else if ("GREMessageType_DieRollResultsResp".equals(type)) {
                projectDieRoll(message, objectAt(greMessage, "dieRollResultsResp"), result);
            }
        }
    }

    private void projectGameState(LogMessageInterface message, JsonObject incoming,
                                  Map<Long, CardInfo> cards, List<GameEvent> result) {
        int previousTurn = state.getTurnNumber() == null ? -1 : state.getTurnNumber();
        int messageStartIndex = result.size();
        updateTurnContext(objectAt(incoming, "turnInfo"));
        updatePlayers(message, arrayAt(incoming, "players"), result);
        updateZones(arrayAt(incoming, "zones"));

        JsonArray annotations = arrayAt(incoming, "annotations");
        JsonArray persistentAnnotations = arrayAt(incoming, "persistentAnnotations");
        learnObjectIdChanges(annotations);
        learnAbilityKinds(annotations);
        reconcileAttachments(persistentAnnotations, arrayAt(incoming, "diffDeletedPersistentAnnotationIds"));

        Set<Long> transferredIds = zoneTransferAffectedIds(annotations);
        for (JsonElement element : arrayAt(incoming, "gameObjects")) {
            if (element.isJsonObject()) {
                projectObjectChange(message, element.getAsJsonObject(), cards, result, transferredIds);
            }
        }

        projectCounterChanges(message, annotations, result);
        reconcilePersistentCounters(persistentAnnotations);
        projectCombatDeclarations(message, cards, result);
        projectZoneTransfers(message, annotations, cards, result);
        projectTargets(message, persistentAnnotations, cards, result);
        reorderLandPlayBeforeOwnAbilities(result, messageStartIndex);
        openingHandTracker.observe(state, knownCards);
        boolean turnChanged = state.getTurnNumber() != null && state.getTurnNumber() != previousTurn;
        if (turnChanged && state.getLastSnapshotTurn() != state.getTurnNumber()) {
            pendingTurnSnapshot = state.getTurnNumber();
            pendingTurnSnapshotNeedsNextMessage = true;
        } else if (pendingTurnSnapshot != null
                && pendingTurnSnapshot.equals(state.getTurnNumber())) {
            if (pendingTurnSnapshotNeedsNextMessage) {
                pendingTurnSnapshotNeedsNextMessage = false;
            } else if (isPostUntapBoundary()) {
                result.add(messageStartIndex, turnSnapshotEvent(message));
                state.setLastSnapshotTurn(state.getTurnNumber());
                pendingTurnSnapshot = null;
            }
        }

        for (JsonElement deleted : arrayAt(incoming, "diffDeletedInstanceIds")) {
            if (deleted.isJsonPrimitive()) state.getObjects().remove(deleted.getAsLong());
        }

        JsonObject gameInfo = objectAt(incoming, "gameInfo");
        String matchState = stringAt(gameInfo, "matchState");
        if (matchState.contains("Complete") && !state.isCompletionEmitted()) {
            state.setCompletionEmitted(true);
            result.add(gameResultEvent(message, gameInfo, arrayAt(incoming, "players"), result));
        }
    }

    private GameEvent gameResultEvent(LogMessageInterface source, JsonObject gameInfo,
                                      JsonArray players, List<GameEvent> preceding) {
        GameResult gameResult = new GameResult();
        int winningTeam = -1;
        String explicitResultReason = "";
        for (JsonElement element : arrayAt(gameInfo, "results")) {
            if (!element.isJsonObject()) continue;
            JsonObject result = element.getAsJsonObject();
            if ("ResultType_Draw".equals(stringAt(result, "result"))) {
                gameResult.setReason(GameResult.Reason.DRAW);
                gameResult.setConfidence(GameResult.Confidence.EXPLICIT);
            }
            int team = intAt(result, "winningTeamId", -1);
            if (team >= 0) winningTeam = team;
            String reason = stringAt(result, "reason");
            if (!reason.isBlank()) explicitResultReason = reason;
        }

        Map<Integer, Integer> seatTeams = new LinkedHashMap<>();
        for (JsonElement element : players) {
            if (!element.isJsonObject()) continue;
            JsonObject player = element.getAsJsonObject();
            int seat = intAt(player, "systemSeatNumber", -1);
            int team = intAt(player, "teamId", -1);
            if (seat >= 0) seatTeams.put(seat, team);
        }
        for (Map.Entry<Integer, Integer> entry : seatTeams.entrySet()) {
            if (entry.getValue() == winningTeam) {
                gameResult.setWinnerSeatId(entry.getKey());
                gameResult.setWinnerName(playerName(entry.getKey()));
            } else if (winningTeam >= 0) {
                gameResult.setLoserSeatId(entry.getKey());
                gameResult.setLoserName(playerName(entry.getKey()));
            }
        }

        if (gameResult.getReason() != GameResult.Reason.DRAW) {
            Integer loser = gameResult.getLoserSeatId();
            int loserPoison = loser == null ? 0 : state.getPoisonCounters().getOrDefault(loser, 0);
            Integer loserLife = loser == null ? null : state.getLifeTotals().get(loser);
            Integer library = loser == null ? null : librarySize(loser);
            if (loserPoison >= 10) {
                gameResult.setReason(GameResult.Reason.POISON);
                gameResult.setConfidence(GameResult.Confidence.CORRELATED);
            } else if (loserLife != null && loserLife <= 0) {
                gameResult.setReason(GameResult.Reason.DAMAGE);
                gameResult.setConfidence(GameResult.Confidence.CORRELATED);
            } else if (library != null && library == 0) {
                gameResult.setReason(GameResult.Reason.EMPTY_LIBRARY);
                gameResult.setConfidence(GameResult.Confidence.INFERRED);
            } else if (explicitResultReason.toLowerCase().contains("concede")) {
                gameResult.setReason(GameResult.Reason.CONCEDE);
                gameResult.setConfidence(GameResult.Confidence.EXPLICIT);
            } else {
                gameResult.setReason(GameResult.Reason.OTHER);
                gameResult.setConfidence(GameResult.Confidence.INFERRED);
            }
        }

        for (int i = preceding.size() - 1; i >= 0; i--) {
            GameEvent prior = preceding.get(i);
            if (!prior.getCards().isEmpty()) {
                gameResult.setFinishingCard(prior.getCards().get(0).getName());
                break;
            }
        }

        String winner = gameResult.getWinnerName() == null ? "Game" : gameResult.getWinnerName();
        String text = switch (gameResult.getReason()) {
            case DAMAGE -> winner + " wins by damage";
            case POISON -> winner + " wins by poison";
            case EMPTY_LIBRARY -> winner + " wins because the opponent drew from an empty library";
            case CONCEDE -> winner + " wins by concession";
            case EFFECT -> winner + " wins by a card effect";
            case OTHER -> winner + " wins (reason not identified)";
            case DRAW -> "Game ends in a draw";
            case UNKNOWN -> winner + " wins";
        };
        if (gameResult.getFinishingCard() != null
                && (gameResult.getReason() == GameResult.Reason.EFFECT
                || gameResult.getReason() == GameResult.Reason.UNKNOWN)) {
            text += " via " + gameResult.getFinishingCard();
        }

        GameEvent event = event(source, text);
        event.setGameResult(gameResult);
        return event;
    }

    private Integer librarySize(int seat) {
        return state.getZones().values().stream()
                .filter(zone -> zone.getOwnerSeatId() != null && zone.getOwnerSeatId() == seat)
                .filter(zone -> "Library".equals(zone.displayName()))
                .map(ZoneInfo::getObjectCount)
                .filter(count -> count >= 0)
                .findFirst().orElse(null);
    }

    private void updateZones(JsonArray zones) {
        for (JsonElement element : zones) {
            if (!element.isJsonObject()) continue;
            JsonObject json = element.getAsJsonObject();
            int zoneId = intAt(json, "zoneId", -1);
            if (zoneId < 0) continue;
            ZoneInfo zone = state.getZones().computeIfAbsent(zoneId, ignored -> new ZoneInfo());
            zone.setZoneId(zoneId);
            String type = stringAt(json, "type");
            if (!type.isBlank()) zone.setType(type);
            if (json.has("ownerSeatId")) zone.setOwnerSeatId(intAt(json, "ownerSeatId", -1));
            if (json.has("objectInstanceIds")) zone.setObjectCount(arrayAt(json, "objectInstanceIds").size());
        }
    }

    private void learnObjectIdChanges(JsonArray annotations) {
        for (JsonElement element : annotations) {
            if (!element.isJsonObject()) continue;
            JsonObject annotation = element.getAsJsonObject();
            if (!hasType(annotation, "AnnotationType_ObjectIdChanged")) continue;
            long oldId = detailLong(annotation, "orig_id", -1);
            long newId = detailLong(annotation, "new_id", -1);
            if (oldId < 0 || newId < 0) continue;
            long logicalId = state.getLogicalIds().getOrDefault(oldId, oldId);
            state.getLogicalIds().put(oldId, logicalId);
            state.getLogicalIds().put(newId, logicalId);
            state.getCurrentInstanceByLogicalId().put(logicalId, newId);
            GameObjectState previous = state.getObjects().get(oldId);
            if (previous != null && !state.getObjects().containsKey(newId)) {
                GameObjectState copy = previous.copy();
                copy.setInstanceId(newId);
                copy.setLogicalObjectId(logicalId);
                state.getObjects().put(newId, copy);
            }
        }
    }

    private void learnAbilityKinds(JsonArray annotations) {
        for (JsonElement element : annotations) {
            if (!element.isJsonObject()) continue;
            JsonObject annotation = element.getAsJsonObject();
            List<Long> affected = longArray(annotation, "affectedIds");
            if (hasType(annotation, "AnnotationType_UserActionTaken")
                    && detailLong(annotation, "actionType", -1) == 2) {
                state.getActivatedAbilityInstances().addAll(affected);
            }
            if (hasType(annotation, "AnnotationType_TriggeringObject")) {
                state.getTriggeredAbilityInstances().addAll(affected);
                long affector = longAt(annotation, "affectorId", -1);
                if (affector >= 0) state.getTriggeredAbilityInstances().add(affector);
            }
        }
    }

    private Set<Long> zoneTransferAffectedIds(JsonArray annotations) {
        Set<Long> result = new LinkedHashSet<>();
        for (JsonElement element : annotations) {
            if (element.isJsonObject() && hasType(element.getAsJsonObject(), "AnnotationType_ZoneTransfer")) {
                result.addAll(longArray(element.getAsJsonObject(), "affectedIds"));
            }
        }
        return result;
    }

    private void projectObjectChange(LogMessageInterface source, JsonObject json,
                                     Map<Long, CardInfo> cards, List<GameEvent> result,
                                     Set<Long> transferredIds) {
        long instanceId = longAt(json, "instanceId", -1);
        if (instanceId < 0) return;

        GameObjectState previous = state.getObjects().get(instanceId);
        GameObjectState current = previous == null ? new GameObjectState() : previous.copy();
        current.setInstanceId(instanceId);
        current.setLogicalObjectId(state.getLogicalIds().getOrDefault(instanceId, instanceId));
        state.getLogicalIds().putIfAbsent(instanceId, current.getLogicalObjectId());
        state.getCurrentInstanceByLogicalId().merge(
                current.getLogicalObjectId(), instanceId, Math::max);

        if (json.has("grpId")) current.setGrpId(longAt(json, "grpId", current.getGrpId()));
        if (current.getGrpId() > 0 && cards.containsKey(current.getGrpId())) current.setCard(cards.get(current.getGrpId()));
        if (json.has("type")) current.setObjectType(stringAt(json, "type"));
        if (json.has("objectSourceGrpId")) current.setObjectSourceGrpId(longAt(json, "objectSourceGrpId", 0));
        if (json.has("parentId")) current.setParentId(longAt(json, "parentId", -1));
        if (json.has("ownerSeatId")) current.setOwnerSeatId(intAt(json, "ownerSeatId", current.getOwnerSeatId()));
        if (json.has("controllerSeatId")) current.setControllerSeatId(intAt(json, "controllerSeatId", current.getControllerSeatId()));
        if (current.getControllerSeatId() < 0) current.setControllerSeatId(current.getOwnerSeatId());
        if (json.has("cardTypes")) {
            current.getCardTypes().clear();
            for (JsonElement type : arrayAt(json, "cardTypes")) current.getCardTypes().add(clean(type.getAsString()));
        }
        if (json.has("subtypes")) {
            current.getSubtypes().clear();
            for (JsonElement type : arrayAt(json, "subtypes")) current.getSubtypes().add(clean(type.getAsString()));
        }
        if (json.has("color")) {
            current.getColors().clear();
            for (JsonElement color : arrayAt(json, "color")) current.getColors().add(clean(color.getAsString()));
        }
        if (json.has("uniqueAbilities")) {
            current.getUniqueAbilityGrpIds().clear();
            for (JsonElement ability : arrayAt(json, "uniqueAbilities")) {
                if (!ability.isJsonObject()) continue;
                long abilityGrpId = longAt(ability.getAsJsonObject(), "grpId", -1);
                if (abilityGrpId > 0) current.getUniqueAbilityGrpIds().add(abilityGrpId);
            }
        }
        if (json.has("counters")) {
            current.getCounters().clear();
            for (JsonElement counterElement : arrayAt(json, "counters")) {
                if (!counterElement.isJsonObject()) continue;
                JsonObject counterJson = counterElement.getAsJsonObject();
                CounterState counter = new CounterState();
                String type = stringAt(counterJson, "type");
                if (type.isBlank()) type = stringAt(counterJson, "counterType");
                int typeId = (int) longAt(counterJson, "counterTypeId",
                        longAt(counterJson, "id", -1));
                counter.setArenaType(typeId);
                if (type.isBlank()) {
                    type = typeId < 0 ? "Unknown" : counterTypeName(typeId);
                }
                counter.setType(clean(type));
                counter.setCount(intAt(counterJson, "count",
                        intAt(counterJson, "value", 1)));
                current.getCounters().add(counter);
            }
        }
        if (json.has("power")) current.setPower(nullableInt(objectAt(json, "power"), "value"));
        if (json.has("toughness")) current.setToughness(nullableInt(objectAt(json, "toughness"), "value"));
        /*
         * Arena gameObjects are current object snapshots, not sparse patches.
         * isTapped is only present when true; its absence means untapped.
         * Retaining the previous true value made lands and attackers appear
         * permanently tapped in later turn snapshots.
         */
        current.setTapped(json.has("isTapped") && json.get("isTapped").getAsBoolean());
        if (json.has("attackState")) {
            current.setAttackState(stringAt(json, "attackState"));
            if (!current.getAttackState().endsWith("_Attacking")
                    && !current.getAttackState().endsWith("_Declared")) {
                current.setAttackTargetId(null);
            }
        }
        if (json.has("attackInfo")) {
            JsonObject attackInfo = objectAt(json, "attackInfo");
            long targetId = longAt(attackInfo, "targetId", -1);
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
            current.getBlockedAttackerIds().addAll(longArray(objectAt(json, "blockInfo"), "attackerIds"));
        }
        if (isToken(current) && current.getCard() == null) current.setCard(bestGuessToken(current));

        int incomingZone = json.has("zoneId") ? intAt(json, "zoneId", current.getZoneId()) : current.getZoneId();
        current.setZoneId(incomingZone);
        int previousSemanticZone = previous == null ? -1 : previous.getSemanticZoneId();
        if (!isTransientZone(incomingZone)) current.setSemanticZoneId(incomingZone);
        else if (previous != null) current.setSemanticZoneId(previousSemanticZone);
        state.getObjects().put(instanceId, current);
        if (current.getGrpId() > 0 && !isAbility(current)) {
            observedCardsByGrpId.put(current.getGrpId(), current.copy());
        }

        // A real Stack object is the authoritative confirmation that the cast
        // completed.  Do not emit a cancellation after this point.
        if ("Stack".equals(zoneType(incomingZone))) {
            removePendingCastFor(instanceId, current);
        }

        // TargetSpec annotations can outlive the transient spell/ability object.
        // Retain both the instance name and the owning card for each ability grpId.
        String historicalName = objectDisplayName(current, cards);
        if (!historicalName.isBlank() && !historicalName.startsWith("ArenaCard#")) {
            historicalObjectNames.put(instanceId, historicalName);
            for (long abilityGrpId : current.getUniqueAbilityGrpIds()) {
                historicalAbilityOwnerNames.put(abilityGrpId, historicalName);
            }
        }

        if (transferredIds.contains(instanceId)) return; // authoritative annotation handles it
        if (previous == null) emitNewVisibleObject(source, current, cards, result);
        else if (current.getSemanticZoneId() >= 0 && previousSemanticZone >= 0
                && current.getSemanticZoneId() != previousSemanticZone) {
            result.add(event(source, describeTransition(previous, current, cards, "")));
        }
    }


    /**
     * Projects declarations from the canonical battlefield at stable combat
     * boundaries. Arena keeps historical object aliases after ObjectIdChanged;
     * only the current instance for each logical object is eligible.
     */
    private void projectCombatDeclarations(LogMessageInterface source,
                                           Map<Long, CardInfo> cards,
                                           List<GameEvent> result) {
        String step = state.getStep() == null ? "" : state.getStep();
        // Attackers are final once Arena enters DeclareBlock.  Blockers are
        // final when their state becomes BlockState_Blocking during that step.
        // Waiting until CombatDamage loses blockers that die in the first damage
        // update, which is exactly what happened in the supplied turn 16.
        boolean attackersStable = step.contains("DeclareBlock");
        boolean blockersStable = step.contains("DeclareBlock")
                || step.contains("CombatDamage")
                || step.contains("EndCombat");

        List<GameObjectState> battlefield = state.getObjects().values().stream()
                .filter(this::isCurrentLogicalInstance)
                .filter(this::isOnBattlefield)
                .toList();

        List<GameObjectState> attackers = battlefield.stream()
                .filter(this::isAttacking)
                .filter(a -> state.getActivePlayerSeat() == null
                        || a.getControllerSeatId() == state.getActivePlayerSeat())
                .sorted(java.util.Comparator.comparingLong(GameObjectState::getLogicalObjectId))
                .toList();

        if (attackersStable && !attackers.isEmpty()) {
            String signature = String.valueOf(state.getTurnNumber()) + ":"
                    + attackers.stream()
                    .map(a -> a.getLogicalObjectId() + ">" + a.getAttackTargetId())
                    .collect(Collectors.joining("|"));
            if (!signature.equals(state.getEmittedAttackSignature())) {
                result.add(attackersDeclaredEvent(source, attackers, cards));
                state.setEmittedAttackSignature(signature);
                state.setEmittedBlockSignature("");
            }
        }

        List<GameObjectState> blockers = battlefield.stream()
                .filter(this::isBlocking)
                .sorted(java.util.Comparator.comparingLong(GameObjectState::getLogicalObjectId))
                .toList();

        if (blockersStable && !blockers.isEmpty()) {
            String signature = String.valueOf(state.getTurnNumber()) + ":"
                    + blockers.stream()
                    .map(b -> b.getLogicalObjectId() + ">"
                            + b.getBlockedAttackerIds().stream()
                            .map(id -> state.getLogicalIds().getOrDefault(id, id))
                            .sorted()
                            .map(String::valueOf)
                            .collect(Collectors.joining(",")))
                    .collect(Collectors.joining("|"));
            if (!signature.equals(state.getEmittedBlockSignature())) {
                result.add(blockersDeclaredEvent(source, blockers, cards));
                state.setEmittedBlockSignature(signature);
            }
        }
    }

    private boolean isCurrentLogicalInstance(GameObjectState object) {
        long current = state.getCurrentInstanceByLogicalId()
                .getOrDefault(object.getLogicalObjectId(), object.getInstanceId());
        return current == object.getInstanceId();
    }

    private boolean isOnBattlefield(GameObjectState object) {
        return "Battlefield".equals(zoneType(object.getSemanticZoneId()));
    }

    private boolean isAttacking(GameObjectState object) {
        return object.getAttackState() != null
                && (object.getAttackState().endsWith("_Attacking")
                || object.getAttackState().endsWith("_Declared"))
                && object.getAttackTargetId() != null;
    }

    private boolean isBlocking(GameObjectState object) {
        return object.getBlockState() != null
                && object.getBlockState().endsWith("_Blocking")
                && !object.getBlockedAttackerIds().isEmpty();
    }

    private GameEvent attackersDeclaredEvent(LogMessageInterface source,
                                             List<GameObjectState> attackers,
                                             Map<Long, CardInfo> cards) {
        int attackingSeat = attackers.get(0).getControllerSeatId();
        Map<Long, List<GameObjectState>> byTarget = new LinkedHashMap<>();
        for (GameObjectState attacker : attackers) {
            byTarget.computeIfAbsent(attacker.getAttackTargetId(), ignored -> new ArrayList<>())
                    .add(attacker);
        }

        String groups = byTarget.entrySet().stream().map(entry -> {
            String target = targetDisplayName(entry.getKey(), cards);
            String names = entry.getValue().stream()
                    .map(a -> combatDisplayName(a, cards))
                    .collect(Collectors.joining(", "));
            return target + " with " + names;
        }).collect(Collectors.joining("; "));

        GameEvent event = event(source, playerName(attackingSeat) + " attacks " + groups);
        event.setPhase("Phase_Combat");
        event.setStep("Step_DeclareAttack");
        for (GameObjectState attacker : attackers) {
            long targetId = attacker.getAttackTargetId();
            event.getAttackers().add(new CombatAttackAssignment(
                    attacker.getLogicalObjectId(),
                    attacker.getInstanceId(),
                    combatDisplayName(attacker, cards),
                    attacker.getControllerSeatId(),
                    targetId,
                    targetDisplayName(targetId, cards)));
            if (attacker.getCard() != null && !event.getCards().contains(attacker.getCard())) {
                event.getCards().add(attacker.getCard());
            }
        }
        return event;
    }

    private GameEvent blockersDeclaredEvent(LogMessageInterface source,
                                            List<GameObjectState> blockers,
                                            Map<Long, CardInfo> cards) {
        int defendingSeat = blockers.get(0).getControllerSeatId();
        List<String> clauses = new ArrayList<>();
        GameEvent event = event(source, "");
        event.setPhase("Phase_Combat");
        event.setStep("Step_DeclareBlock");

        for (GameObjectState blocker : blockers) {
            List<Long> logicalIds = new ArrayList<>();
            List<String> attackerNames = new ArrayList<>();
            for (long attackerInstanceId : blocker.getBlockedAttackerIds()) {
                GameObjectState attacker = state.getObjects().get(attackerInstanceId);
                long logicalId = state.getLogicalIds().getOrDefault(attackerInstanceId, attackerInstanceId);
                logicalIds.add(logicalId);
                attackerNames.add(attacker == null
                        ? targetDisplayName(attackerInstanceId, cards)
                        : objectDisplayName(attacker, cards));
            }
            String blockerName = objectDisplayName(blocker, cards);
            clauses.add(blockerName + " blocks " + String.join(", ", attackerNames));
            event.getBlockers().add(new CombatBlockAssignment(
                    blocker.getLogicalObjectId(),
                    blocker.getInstanceId(),
                    blockerName,
                    blocker.getControllerSeatId(),
                    logicalIds,
                    attackerNames));
            if (blocker.getCard() != null && !event.getCards().contains(blocker.getCard())) {
                event.getCards().add(blocker.getCard());
            }
        }

        event.setText(playerName(defendingSeat) + " blocks: " + String.join("; ", clauses));
        return event;
    }

    private void projectZoneTransfers(LogMessageInterface source, JsonArray annotations,
                                      Map<Long, CardInfo> cards, List<GameEvent> result) {
        for (JsonElement element : annotations) {
            if (!element.isJsonObject()) continue;
            JsonObject annotation = element.getAsJsonObject();
            if (!hasType(annotation, "AnnotationType_ZoneTransfer") || !markAnnotation(annotation)) continue;
            int fromZone = (int) detailLong(annotation, "zone_src", -1);
            int toZone = (int) detailLong(annotation, "zone_dest", -1);
            String category = detailString(annotation, "category");
            for (long instanceId : longArray(annotation, "affectedIds")) {
                GameObjectState object = state.getObjects().get(instanceId);
                if (object == null) continue;
                GameObjectState before = object.copy();
                before.setSemanticZoneId(fromZone);
                object.setSemanticZoneId(toZone);
                object.setZoneId(toZone);

                if ("Stack".equals(zoneType(toZone))) {
                    removePendingCastFor(instanceId, object);
                }

                /*
                 * Cancelling target/mode/payment selection commonly rolls the
                 * card from Limbo back to Hand.  Limbo is intentionally not a
                 * semantic zone elsewhere, but it is meaningful for this one
                 * correlation.
                 */
                if ("Hand".equals(zoneType(toZone))
                        && ("Limbo".equals(zoneType(fromZone))
                        || "Stack".equals(zoneType(fromZone)))) {
                    PendingCast pending = removePendingCastFor(instanceId, object);
                    if (pending != null) {
                        result.add(cancelledCastEvent(source, pending));
                        continue;
                    }
                }

                result.add(event(source, describeTransition(before, object, cards, category)));
            }
        }
    }

    private void projectTargets(LogMessageInterface source, JsonArray persistentAnnotations,
                                Map<Long, CardInfo> cards, List<GameEvent> result) {
        Map<Long, List<Long>> targetsBySource = new LinkedHashMap<>();
        Map<Long, Long> sourceGrpIds = new LinkedHashMap<>();

        for (JsonElement element : persistentAnnotations) {
            if (!element.isJsonObject()) continue;
            JsonObject annotation = element.getAsJsonObject();
            if (!hasType(annotation, "AnnotationType_TargetSpec") || !markAnnotation(annotation)) continue;

            long sourceId = longAt(annotation, "affectorId", -1);
            if (sourceId < 0) continue;
            targetsBySource.computeIfAbsent(sourceId, ignored -> new ArrayList<>())
                    .addAll(longArray(annotation, "affectedIds"));

            long abilityGrpId = detailLong(annotation, "abilityGrpId", -1);
            if (abilityGrpId > 0) sourceGrpIds.putIfAbsent(sourceId, abilityGrpId);
        }

        for (Map.Entry<Long, List<Long>> entry : targetsBySource.entrySet()) {
            long sourceId = entry.getKey();
            GameObjectState sourceObject = state.getObjects().get(sourceId);
            long abilityGrpId = sourceGrpIds.getOrDefault(sourceId, -1L);
            String sourceName;
            if (sourceObject != null) {
                sourceName = objectDisplayName(sourceObject, cards);
            } else if (historicalObjectNames.containsKey(sourceId)) {
                sourceName = historicalObjectNames.get(sourceId);
            } else if (abilityGrpId > 0 && historicalAbilityOwnerNames.containsKey(abilityGrpId)) {
                sourceName = historicalAbilityOwnerNames.get(abilityGrpId);
            } else {
                GameObjectState abilityOwner = abilityGrpId > 0
                        ? findObjectOwningAbilityGroup(abilityGrpId)
                        : null;
                sourceName = abilityOwner == null
                        ? (abilityGrpId > 0
                            ? "Unknown spell or ability [Arena ability #" + abilityGrpId + "]"
                            : "Unknown spell or ability")
                        : objectDisplayName(abilityOwner, cards);
            }

            String targets = entry.getValue().stream().distinct()
                    .map(id -> targetDisplayName(id, cards))
                    .collect(Collectors.joining(", "));
            if (!targets.isBlank()) result.add(event(source, sourceName + " targets " + targets));
        }
    }

    private void emitNewVisibleObject(LogMessageInterface source, GameObjectState current,
                                      Map<Long, CardInfo> cards, List<GameEvent> result) {
        String zoneType = zoneType(current.getSemanticZoneId());
        if (!"Stack".equals(zoneType) && !"Battlefield".equals(zoneType)) return;
        String actor = playerName(current.getControllerSeatId());
        String name = objectDisplayName(current, cards);

        if ("Stack".equals(zoneType)) {
            if (isAbility(current)) {
                String verb = abilityVerb(current);
                result.add(abilityEvent(source, actor + " " + verb + " " + name, current));
            } else {
                result.add(event(source, actor + " casts " + name));
            }
        } else if (!isAbility(current)) {
            if (isLand(current, cards)) {
                result.add(event(source, actor + " plays " + name + tappedSuffix(current)));
            } else {
                result.add(event(source, name + " entered the battlefield"
                        + tappedSuffix(current) + " under " + actor + "'s control"));
            }
        }
    }

    private String describeTransition(GameObjectState previous, GameObjectState current,
                                      Map<Long, CardInfo> cards, String category) {
        String from = zoneType(previous.getSemanticZoneId());
        String to = zoneType(current.getSemanticZoneId());
        String name = objectDisplayName(current, cards);
        String actor = playerName(current.getControllerSeatId());

        if (isAbility(current)) {
            if ("Stack".equals(to)) return actor + " " + abilityVerb(current) + " " + name;
            return name + " finishes resolving";
        }
        if ("PlayLand".equals(category) || ("Hand".equals(from) && "Battlefield".equals(to) && isLand(current, cards)))
            return actor + " plays " + name + tappedSuffix(current);
        if ("CastSpell".equals(category) || ("Hand".equals(from) && "Stack".equals(to)))
            return actor + " casts " + name;
        if ("Draw".equals(category) || ("Library".equals(from) && "Hand".equals(to)))
            return actor + " draws " + name;
        if ("Stack".equals(from) && "Battlefield".equals(to))
            return name + " resolves and enters the battlefield" + tappedSuffix(current);
        if ("Stack".equals(from) && "Graveyard".equals(to)) return name + " resolves and is put into the graveyard";
        if ("Battlefield".equals(from) && "Graveyard".equals(to)) return name + " is put into the graveyard";
        if ("Battlefield".equals(from) && "Exile".equals(to)) return name + " is exiled";
        if ("Graveyard".equals(from) && "Exile".equals(to)) return name + " is exiled from the graveyard";
        if ("Graveyard".equals(from) && "Battlefield".equals(to))
            return name + " returns from the graveyard to the battlefield" + tappedSuffix(current);
        if ("Graveyard".equals(from) && "Hand".equals(to)) return actor + " returns " + name + " from the graveyard to hand";
        return actor + ": " + name + " moved " + from + " → " + to;
    }


    private GameObjectState findObjectOwningAbilityGroup(long abilityGrpId) {
        return state.getObjects().values().stream()
                .filter(object -> object.getUniqueAbilityGrpIds().contains(abilityGrpId))
                .filter(this::isCurrentLogicalInstance)
                .findFirst()
                .orElseGet(() -> state.getObjects().values().stream()
                        .filter(object -> object.getUniqueAbilityGrpIds().contains(abilityGrpId))
                        .findFirst()
                        .orElse(null));
    }

    /**
     * Arena can report an enters-the-battlefield ability object before the
     * ZoneTransfer/PlayLand annotation in the same GameStateMessage.  Humans
     * expect the land play first, so move only that land's play event ahead of
     * its own already-projected ability events.
     */
    private void reorderLandPlayBeforeOwnAbilities(List<GameEvent> events, int fromIndex) {
        for (int playIndex = fromIndex; playIndex < events.size(); playIndex++) {
            String text = events.get(playIndex).getText();
            int marker = text == null ? -1 : text.indexOf(" plays ");
            if (marker < 0) continue;

            String landName = text.substring(marker + " plays ".length())
                    .replaceFirst("\\s+(?:tapped|untapped)$", "");
            int earliestOwnAbility = playIndex;
            for (int i = fromIndex; i < playIndex; i++) {
                String earlier = events.get(i).getText();
                if (earlier != null && (earlier.contains("ability from " + landName)
                        || earlier.contains(landName + "'s ability"))) {
                    earliestOwnAbility = i;
                    break;
                }
            }
            if (earliestOwnAbility < playIndex) {
                GameEvent play = events.remove(playIndex);
                events.add(earliestOwnAbility, play);
            }
        }
    }

    private String tappedSuffix(GameObjectState object) {
        if (object.getTapped() == null) return "";
        return object.getTapped() ? " tapped" : " untapped";
    }

    private String abilityVerb(GameObjectState ability) {
        if (state.getActivatedAbilityInstances().contains(ability.getInstanceId())) return "activates";
        if (state.getTriggeredAbilityInstances().contains(ability.getInstanceId())) return "triggers";
        return "puts an ability from";
    }

    private boolean isAbility(GameObjectState object) {
        return "GameObjectType_Ability".equals(object.getObjectType());
    }

    private String objectDisplayName(long instanceId, Map<Long, CardInfo> cards) {
        GameObjectState object = state.getObjects().get(instanceId);
        if (object == null) return "object " + instanceId;
        return objectDisplayName(object, cards);
    }

    private String targetDisplayName(long id, Map<Long, CardInfo> cards) {
        GameObjectState object = state.getObjects().get(id);
        if (object != null) return objectDisplayName(object, cards);
        if (state.getPlayers().containsKey((int) id)) return playerName((int) id);
        return "object " + id;
    }

    private String objectDisplayName(GameObjectState object, Map<Long, CardInfo> cards) {
        if (isAbility(object)) {
            String source = cardName(object.getObjectSourceGrpId(), cards);
            String learned = abilityNames.find(object.getObjectSourceGrpId(), object.getGrpId());
            String kind = state.getActivatedAbilityInstances().contains(object.getInstanceId()) ? "activated" :
                    state.getTriggeredAbilityInstances().contains(object.getInstanceId()) ? "triggered" : "unknown";
            String inferred = AbilityHeuristics.infer(cards.get(object.getObjectSourceGrpId()), kind);
            String label = !learned.isBlank() ? learned : inferred;
            return label.isBlank() ? source + "'s ability" : source + " — " + label;
        }
        if (object.getCard() != null && object.getCard().getName() != null && !object.getCard().getName().isBlank()) {
            return object.getCard().getName();
        }
        if (isToken(object)) return descriptiveTokenName(object);
        String resolved = cardName(object.getGrpId(), cards);
        if (!resolved.startsWith("ArenaCard#")) return resolved;
        return observedCardDescription(object);
    }

    private String combatDisplayName(GameObjectState object, Map<Long, CardInfo> cards) {
        String name = objectDisplayName(object, cards);
        if (object.getPower() != null && object.getToughness() != null) {
            return name + " (" + object.getPower() + "/" + object.getToughness() + ")";
        }
        return name;
    }

    private String observedCardDescription(GameObjectState object) {
        if (object == null) return "Unknown card";
        StringBuilder out = new StringBuilder("Unknown");
        if (!object.getColors().isEmpty()) {
            out.append(' ').append(String.join("/", object.getColors()).toLowerCase());
        }
        if (!object.getSubtypes().isEmpty()) {
            out.append(' ').append(String.join(" ", object.getSubtypes()));
        }
        if (!object.getCardTypes().isEmpty()) {
            out.append(' ').append(String.join(" ", object.getCardTypes()).toLowerCase());
        } else if (object.getObjectType() != null && !object.getObjectType().isBlank()) {
            out.append(' ').append(clean(object.getObjectType()).toLowerCase());
        } else {
            out.append(" card");
        }
        if (object.getPower() != null && object.getToughness() != null) {
            out.append(" (").append(object.getPower()).append('/').append(object.getToughness()).append(')');
        }
        if (object.getGrpId() > 0) out.append(" [Arena #").append(object.getGrpId()).append(']');
        return out.toString();
    }

    private void repairPreviouslyUnknownNames(Map<Long, CardInfo> newlyKnown) {
        if (newlyKnown == null || newlyKnown.isEmpty() || emittedEvents.isEmpty()) return;
        for (Map.Entry<Long, CardInfo> entry : newlyKnown.entrySet()) {
            CardInfo card = entry.getValue();
            if (card == null || card.getName() == null || card.getName().isBlank()) continue;
            String placeholder = "ArenaCard#" + entry.getKey();
            for (GameEvent event : emittedEvents) {
                if (event.getText() != null && event.getText().contains(placeholder)) {
                    event.setText(event.getText().replace(placeholder, card.getName()));
                }
            }
        }
    }

    /**
     * Applies Arena's explicit counter transactions. Counter type 3 is the
     * player poison counter used by Fynn's triggered ability. Other types are
     * retained generically on the affected permanent.
     */
    private void projectCounterChanges(LogMessageInterface source,
                                       JsonArray annotations,
                                       List<GameEvent> result) {
        for (JsonElement element : annotations) {
            if (!element.isJsonObject()) continue;
            JsonObject annotation = element.getAsJsonObject();
            boolean added = hasType(annotation, "AnnotationType_CounterAdded");
            boolean removed = hasType(annotation, "AnnotationType_CounterRemoved");
            if ((!added && !removed) || !markAnnotation(annotation)) continue;

            int counterType = (int) detailLong(annotation, "counter_type", -1);
            int amount = (int) detailLong(annotation, "transaction_amount", 1);
            if (amount < 0) amount = -amount;
            int delta = removed ? -amount : amount;

            for (long affectedId : longArray(annotation, "affectedIds")) {
                if (state.getPlayers().containsKey((int) affectedId)) {
                    applyPlayerCounter(source, (int) affectedId, counterType, delta, result);
                    continue;
                }

                GameObjectState object = findObjectIncludingAliases(affectedId);
                if (object != null) applyPermanentCounter(object, counterType, delta);
            }
        }
    }

    /**
     * Persistent AnnotationType_Counter entries contain the absolute count.
     * They let replay reconstruction start in the middle of a game and also
     * correct any missed transaction without emitting duplicate prose.
     */
    private void reconcilePersistentCounters(JsonArray persistentAnnotations) {
        for (JsonElement element : persistentAnnotations) {
            if (!element.isJsonObject()) continue;
            JsonObject annotation = element.getAsJsonObject();
            if (!hasType(annotation, "AnnotationType_Counter")) continue;

            int counterType = (int) detailLong(annotation, "counter_type", -1);
            int count = (int) detailLong(annotation, "count", 0);
            for (long affectedId : longArray(annotation, "affectedIds")) {
                if (state.getPlayers().containsKey((int) affectedId)) {
                    if (counterType == 3) {
                        state.getPoisonCounters().put((int) affectedId, Math.max(0, count));
                    }
                    continue;
                }

                GameObjectState object = findObjectIncludingAliases(affectedId);
                if (object != null) setPermanentCounter(object, counterType, count);
            }
        }
    }

    private void applyPlayerCounter(LogMessageInterface source,
                                    int seatId,
                                    int counterType,
                                    int delta,
                                    List<GameEvent> result) {
        // Verified from the supplied Fynn game: Arena counter_type 3 on a
        // player seat is poison.
        if (counterType != 3) return;

        int previous = state.getPoisonCounters().getOrDefault(seatId, 0);
        int current = Math.max(0, previous + delta);
        state.getPoisonCounters().put(seatId, current);
        if (current == previous) return;

        int changed = current - previous;
        String verb = changed > 0
                ? "gets " + changed + " poison counter" + (changed == 1 ? "" : "s")
                : "loses " + Math.abs(changed) + " poison counter"
                    + (changed == -1 ? "" : "s");
        result.add(event(source, playerName(seatId) + " " + verb
                + " (" + current + " total)"));
    }

    private void applyPermanentCounter(GameObjectState object,
                                       int counterType,
                                       int delta) {
        CounterState counter = counterState(object, counterType);
        int current = Math.max(0, counter.getCount() + delta);
        if (current == 0) {
            object.getCounters().remove(counter);
        } else {
            counter.setCount(current);
        }
    }

    private void setPermanentCounter(GameObjectState object,
                                     int counterType,
                                     int count) {
        CounterState counter = counterState(object, counterType);
        if (count <= 0) {
            object.getCounters().remove(counter);
        } else {
            counter.setCount(count);
        }
    }

    private CounterState counterState(GameObjectState object, int counterType) {
        String key = counterTypeName(counterType);
        for (CounterState counter : object.getCounters()) {
            if (counter.getArenaType() == counterType
                    || key.equals(counter.getType())) {
                counter.setArenaType(counterType);
                counter.setType(key);
                return counter;
            }
        }
        CounterState counter = new CounterState();
        counter.setArenaType(counterType);
        counter.setType(key);
        object.getCounters().add(counter);
        return counter;
    }

    private String counterTypeName(int counterType) {
        return switch (counterType) {
            case 1 -> "+1/+1";
            case 2 -> "-1/-1";
            case 3 -> "Poison";
            default -> "Counter#" + counterType;
        };
    }

    private GameObjectState findObjectIncludingAliases(long instanceId) {
        GameObjectState direct = state.getObjects().get(instanceId);
        if (direct != null) return direct;

        long logicalId = state.getLogicalIds().getOrDefault(instanceId, instanceId);
        Long currentId = state.getCurrentInstanceByLogicalId().get(logicalId);
        if (currentId != null) {
            GameObjectState current = state.getObjects().get(currentId);
            if (current != null) return current;
        }

        for (GameObjectState candidate : state.getObjects().values()) {
            if (candidate.getLogicalObjectId() == logicalId) return candidate;
        }
        return null;
    }

    private void updatePlayers(LogMessageInterface source, JsonArray players,
                               List<GameEvent> result) {
        for (JsonElement element : players) {
            if (!element.isJsonObject()) continue;
            JsonObject player = element.getAsJsonObject();
            int seat = intAt(player, "systemSeatNumber", intAt(player, "systemSeatId", -1));
            if (seat < 0) continue;
            if (player.has("lifeTotal")) state.getLifeTotals().put(seat, intAt(player, "lifeTotal", 0));
            Integer poison = poisonCount(player);
            if (poison != null) {
                Integer previous = state.getPoisonCounters().put(seat, poison);
                if (previous != null && previous.intValue() != poison.intValue()) {
                    int delta = poison - previous;
                    String verb = delta > 0 ? "gets " + delta + " poison counter"
                            + (delta == 1 ? "" : "s")
                            : "loses " + Math.abs(delta) + " poison counter"
                            + (delta == -1 ? "" : "s");
                    result.add(event(source, playerName(seat) + " " + verb
                            + " (" + poison + " total)"));
                }
            }
        }
    }

    private Integer poisonCount(JsonObject player) {
        for (String key : List.of("poisonCount", "poisonCounter", "poisonCounters")) {
            if (player.has(key) && player.get(key).isJsonPrimitive()) return player.get(key).getAsInt();
        }
        for (JsonElement element : arrayAt(player, "counters")) {
            if (!element.isJsonObject()) continue;
            JsonObject counter = element.getAsJsonObject();
            String type = stringAt(counter, "type");
            if (type.toLowerCase().contains("poison")) return intAt(counter, "count", intAt(counter, "value", 0));
        }
        return null;
    }

    private GameEvent turnSnapshotEvent(LogMessageInterface source) {
        GameEvent event = event(source, "Turn state");
        event.getBattlefieldObservation().addAll(currentBattlefieldObservation());
        for (Map.Entry<Integer, String> player : state.getPlayers().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            PlayerTurnSnapshot snapshot = new PlayerTurnSnapshot();
            snapshot.setSeatId(player.getKey());
            snapshot.setPlayerName(player.getValue());
            snapshot.setLifeTotal(state.getLifeTotals().get(player.getKey()));
            snapshot.setPoisonCounters(state.getPoisonCounters().getOrDefault(player.getKey(), 0));
            snapshot.setHandSize(handSize(player.getKey()));
            event.getTurnSnapshot().add(snapshot);
        }
        return event;
    }

    private List<BoardPermanentSnapshot> currentBattlefieldObservation() {
        return state.getObjects().values().stream()
                .filter(this::isCurrentLogicalInstance)
                .filter(this::isOnBattlefield)
                .filter(object -> !isAbility(object))
                .sorted(java.util.Comparator
                        .comparingInt(GameObjectState::getControllerSeatId)
                        .thenComparingLong(GameObjectState::getLogicalObjectId))
                .map(object -> {
                    BoardPermanentSnapshot permanent = new BoardPermanentSnapshot();
                    permanent.setLogicalObjectId(object.getLogicalObjectId());
                    permanent.setOwnerSeatId(object.getOwnerSeatId());
                    permanent.setControllerSeatId(object.getControllerSeatId());
                    permanent.setName(objectDisplayName(object, knownCards));
                    permanent.setCard(object.getCard());
                    permanent.setTapped(object.getTapped());
                    permanent.setPower(object.getPower());
                    permanent.setToughness(object.getToughness());
                    permanent.setAttachedToLogicalObjectId(attachedHostFor(object.getLogicalObjectId()));
                    object.getCounters().forEach(counter ->
                            permanent.getCounters().add(counter.copy()));
                    return permanent;
                })
                .toList();
    }

    private void reconcileAttachments(JsonArray persistentAnnotations, JsonArray deletedIds) {
        for (JsonElement deleted : deletedIds) {
            if (deleted.isJsonPrimitive()) attachmentsByAnnotationId.remove(deleted.getAsLong());
        }
        for (JsonElement element : persistentAnnotations) {
            if (!element.isJsonObject()) continue;
            JsonObject annotation = element.getAsJsonObject();
            if (!hasAnnotationType(annotation, "AnnotationType_Attachment")) continue;
            long annotationId = longAt(annotation, "id", -1);
            long attachedId = longAt(annotation, "affectorId", -1);
            JsonArray affected = arrayAt(annotation, "affectedIds");
            if (annotationId < 0 || attachedId < 0 || affected.size() == 0) continue;
            long hostId = affected.get(0).getAsLong();
            long attachedLogical = state.getLogicalIds().getOrDefault(attachedId, attachedId);
            long hostLogical = state.getLogicalIds().getOrDefault(hostId, hostId);
            attachmentsByAnnotationId.put(annotationId,
                    new AttachmentRelation(attachedLogical, hostLogical));
        }
    }

    private static boolean hasAnnotationType(JsonObject annotation, String expectedType) {
        if (annotation == null || expectedType == null) return false;
        JsonElement typeElement = annotation.get("type");
        if (typeElement == null || typeElement.isJsonNull()) return false;
        if (typeElement.isJsonArray()) {
            for (JsonElement element : typeElement.getAsJsonArray()) {
                if (element.isJsonPrimitive() && expectedType.equals(element.getAsString())) {
                    return true;
                }
            }
            return false;
        }
        return typeElement.isJsonPrimitive()
                && expectedType.equals(typeElement.getAsString());
    }

    private Long attachedHostFor(long attachedLogicalId) {
        for (AttachmentRelation relation : attachmentsByAnnotationId.values()) {
            if (relation.attachedLogicalId() == attachedLogicalId) {
                return relation.hostLogicalId();
            }
        }
        return null;
    }

    private boolean isPostUntapBoundary() {
        String phase = state.getPhase() == null ? "" : state.getPhase();
        String step = state.getStep() == null ? "" : state.getStep();
        if (!phase.contains("Beginning")) return true;
        return step.contains("Upkeep") || step.contains("Draw");
    }

    private Integer handSize(int seat) {
        return state.getZones().values().stream()
                .filter(zone -> zone.getOwnerSeatId() != null && zone.getOwnerSeatId() == seat)
                .filter(zone -> "Hand".equals(zone.displayName()))
                .map(ZoneInfo::getObjectCount).filter(count -> count >= 0).findFirst().orElse(null);
    }

    private boolean isToken(GameObjectState object) {
        return object.getObjectType() != null && object.getObjectType().contains("Token");
    }

    private CardInfo bestGuessToken(GameObjectState token) {
        CardInfo source = knownCards.get(token.getObjectSourceGrpId());
        if (source == null || source.getAllParts() == null) return null;
        CardInfo best = null;
        int bestScore = Integer.MIN_VALUE;
        int second = Integer.MIN_VALUE;
        for (CardRelatedPart part : source.getAllParts()) {
            if (part == null || !"token".equalsIgnoreCase(part.getComponent())) continue;
            CardInfo candidate = knownRelatedCards.get(part.getId());
            int score = candidate == null ? scoreRelatedName(token, part) : scoreToken(token, candidate);
            if (score > bestScore) { second = bestScore; bestScore = score; best = candidate != null ? candidate : syntheticRelated(part); }
            else if (score > second) second = score;
        }
        if (best != null && bestScore >= 20 && bestScore - second >= 5) return best;
        return null;
    }

    private int scoreRelatedName(GameObjectState token, CardRelatedPart part) {
        String haystack = ((part.getName() == null ? "" : part.getName()) + " " +
                (part.getTypeLine() == null ? "" : part.getTypeLine())).toLowerCase();
        int score = 5;
        for (String subtype : token.getSubtypes()) if (haystack.contains(subtype.toLowerCase())) score += 12;
        if (token.getPower() != null && token.getToughness() != null && haystack.contains(token.getPower() + "/" + token.getToughness())) score += 20;
        return score;
    }

    private int scoreToken(GameObjectState token, CardInfo candidate) {
        int score = 10;
        String type = candidate.effectiveTypeLine() == null ? "" : candidate.effectiveTypeLine().toLowerCase();
        for (String subtype : token.getSubtypes()) if (type.contains(subtype.toLowerCase())) score += 15;
        if (token.getPower() != null && String.valueOf(token.getPower()).equals(candidate.getPower())) score += 12;
        if (token.getToughness() != null && String.valueOf(token.getToughness()).equals(candidate.getToughness())) score += 12;
        if (candidate.getColors() != null && !candidate.getColors().isEmpty() && token.getColors().containsAll(candidate.getColors())) score += 8;
        return score;
    }

    private CardInfo syntheticRelated(CardRelatedPart part) {
        CardInfo card = new CardInfo();
        card.setId(part.getId());
        card.setName(part.getName());
        card.setTypeLine(part.getTypeLine());
        return card;
    }

    private String descriptiveTokenName(GameObjectState token) {
        StringBuilder text = new StringBuilder();
        if (token.getPower() != null && token.getToughness() != null) text.append(token.getPower()).append('/').append(token.getToughness()).append(' ');
        if (!token.getColors().isEmpty()) text.append(String.join("/", token.getColors()).toLowerCase()).append(' ');
        if (!token.getSubtypes().isEmpty()) text.append(String.join(" ", token.getSubtypes())).append(' ');
        text.append("token");
        return text.toString();
    }

    private Integer nullableInt(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : null;
    }

    private void updateTurnContext(JsonObject turnInfo) {
        if (turnInfo.size() == 0) return;
        int turn = intAt(turnInfo, "turnNumber", state.getTurnNumber() == null ? -1 : state.getTurnNumber());
        int active = intAt(turnInfo, "activePlayer", state.getActivePlayerSeat() == null ? -1 : state.getActivePlayerSeat());
        String phase = clean(stringAt(turnInfo, "phase"));
        String step = clean(stringAt(turnInfo, "step"));
        if (turn >= 0) state.setTurnNumber(turn);
        if (active >= 0) state.setActivePlayerSeat(active);
        if (!phase.isBlank()) {
            if (!phase.equals(state.getPhase()) && step.isBlank()) state.setStep("");
            state.setPhase(phase);
        }
        if (!step.isBlank()) state.setStep(step);
    }

    private void projectDieRoll(LogMessageInterface message, JsonObject response, List<GameEvent> result) {
        for (JsonElement element : arrayAt(response, "playerDieRolls")) {
            if (!element.isJsonObject()) continue;
            JsonObject roll = element.getAsJsonObject();
            result.add(event(message, playerName(intAt(roll, "systemSeatId", -1)) + " rolled " + intAt(roll, "rollValue", -1)));
        }
    }

    private GameEvent event(LogMessageInterface source, String text) {
        GameEvent event = new GameEvent();
        event.setSequence(source.getSequence());
        event.setTimestamp(source.getTimestamp());
        event.setTurnNumber(state.getTurnNumber());
        event.setActivePlayerSeat(state.getActivePlayerSeat());
        event.setActivePlayerName(playerName(state.getActivePlayerSeat()));
        event.setPhase(state.getPhase());
        event.setStep(state.getStep());
        event.setText(text);
        java.util.stream.Stream.concat(knownCards.values().stream(), knownRelatedCards.values().stream())
                .filter(card -> card.getName() != null && text.contains(card.getName()))
                .distinct()
                .forEach(event.getCards()::add);
        return event;
    }

    private GameEvent abilityEvent(LogMessageInterface source, String text, GameObjectState ability) {
        GameEvent event = event(source, text);
        AbilityReference reference = new AbilityReference();
        reference.setAbilityGrpId(ability.getGrpId());
        reference.setSourceGrpId(ability.getObjectSourceGrpId());
        reference.setSourceName(cardName(ability.getObjectSourceGrpId(), knownCards));
        reference.setKind(state.getActivatedAbilityInstances().contains(ability.getInstanceId()) ? "activated" :
                state.getTriggeredAbilityInstances().contains(ability.getInstanceId()) ? "triggered" : "unknown");
        event.setAbility(reference);
        CardInfo sourceCard = knownCards.get(ability.getObjectSourceGrpId());
        if (sourceCard != null && !event.getCards().contains(sourceCard)) event.getCards().add(sourceCard);
        return event;
    }

    private boolean markAnnotation(JsonObject annotation) {
        long id = longAt(annotation, "id", -1);
        return id < 0 || state.getEmittedAnnotationIds().add(id);
    }

    private boolean hasType(JsonObject annotation, String expected) {
        for (JsonElement type : arrayAt(annotation, "type")) if (expected.equals(type.getAsString())) return true;
        return false;
    }

    private long detailLong(JsonObject annotation, String key, long fallback) {
        for (JsonElement element : arrayAt(annotation, "details")) {
            if (!element.isJsonObject()) continue;
            JsonObject detail = element.getAsJsonObject();
            if (!key.equals(stringAt(detail, "key"))) continue;
            JsonArray values = arrayAt(detail, "valueInt32");
            if (!values.isEmpty()) return values.get(0).getAsLong();
            values = arrayAt(detail, "valueUint32");
            if (!values.isEmpty()) return values.get(0).getAsLong();
        }
        return fallback;
    }

    private String detailString(JsonObject annotation, String key) {
        for (JsonElement element : arrayAt(annotation, "details")) {
            if (!element.isJsonObject()) continue;
            JsonObject detail = element.getAsJsonObject();
            if (!key.equals(stringAt(detail, "key"))) continue;
            JsonArray values = arrayAt(detail, "valueString");
            if (!values.isEmpty()) return values.get(0).getAsString();
        }
        return "";
    }

    private List<Long> longArray(JsonObject root, String key) {
        List<Long> result = new ArrayList<>();
        for (JsonElement value : arrayAt(root, key)) if (value.isJsonPrimitive()) result.add(value.getAsLong());
        return result;
    }

    private boolean isTransientZone(int zoneId) {
        String type = zoneType(zoneId);
        return "Limbo".equals(type) || "Pending".equals(type) || "Suppressed".equals(type);
    }

    private String zoneType(int zoneId) {
        ZoneInfo info = state.getZones().get(zoneId);
        return info == null ? "Zone " + zoneId : info.displayName();
    }

    private boolean isLand(GameObjectState object, Map<Long, CardInfo> cards) {
        if (object.getCardTypes().contains("Land")) return true;
        CardInfo card = object.getCard() != null ? object.getCard() : cards.get(object.getGrpId());
        String typeLine = card == null ? null : card.effectiveTypeLine();
        return typeLine != null && typeLine.contains("Land");
    }

    private Map<Long, CardInfo> cards(ModelObject model) {
        return model instanceof InformationBundle bundle ? bundle.getCards() : Map.of();
    }

    private String cardName(long grpId, Map<Long, CardInfo> cards) {
        if (grpId <= 0) return "unknown source";
        CardInfo card = cards.get(grpId);
        if (card != null && card.getName() != null && !card.getName().isBlank()) return card.getName();
        GameObjectState observed = observedCardsByGrpId.get(grpId);
        return observed == null ? "ArenaCard#" + grpId : observedCardDescription(observed);
    }

    private String playerName(Integer seat) {
        if (seat == null || seat < 0) return "Unknown player";
        return state.getPlayers().getOrDefault(seat, "Seat " + seat);
    }

    private String clean(String value) {
        if (value == null) return "";
        int underscore = value.indexOf('_');
        return underscore >= 0 ? value.substring(underscore + 1) : value;
    }

    private JsonObject objectAt(JsonObject root, String... path) {
        JsonElement current = root;
        for (String key : path) {
            if (current == null || !current.isJsonObject()) return new JsonObject();
            current = current.getAsJsonObject().get(key);
        }
        return current != null && current.isJsonObject() ? current.getAsJsonObject() : new JsonObject();
    }

    private JsonArray arrayAt(JsonObject root, String key) {
        JsonElement value = root.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private String stringAt(JsonObject root, String... path) {
        JsonElement current = root;
        for (String key : path) {
            if (current == null || !current.isJsonObject()) return "";
            current = current.getAsJsonObject().get(key);
        }
        return current == null || current.isJsonNull() ? "" : current.getAsString();
    }

    private int intAt(JsonObject root, String key, int fallback) {
        JsonElement value = root.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
    }

    private long longAt(JsonObject root, String key, long fallback) {
        JsonElement value = root.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsLong() : fallback;
    }
}
