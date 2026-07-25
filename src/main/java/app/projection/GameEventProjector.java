package app.projection;

import app.model.event.AbilityReference;
import app.model.card.CardInfo;
import app.model.card.CardFaceInfo;
import app.model.event.GameEvent;
import app.model.event.GameEventType;
import app.model.event.DecisionObservation;
import app.model.event.ObjectReference;
import app.model.game.*;
import app.model.InformationBundle;
import app.model.match.MatchState;
import app.model.log.LogMessageInterface;
import app.model.log.ModelObject;
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

import static app.projection.ArenaJson.*;

/** Projects Arena GRE state and annotations into human-readable events.
 * <p><strong>Architectural role:</strong> This type belongs to the projection boundary between ordered Arena observations, canonical game state, and immutable semantic events.</p>
 */
public final class GameEventProjector {
    private final GameState state = new GameState();
    private final MatchState matchState;
    private final ObjectIdentityTracker objectIdentityTracker = new ObjectIdentityTracker(state);
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
    private final AttachmentTracker attachmentTracker = new AttachmentTracker();
    private final CounterProjector counterProjector = new CounterProjector();
    private final TokenResolver tokenResolver = new TokenResolver();
    private final ZoneTransitionClassifier zoneTransitionClassifier = new ZoneTransitionClassifier();
    private final ZoneEventProjector zoneEventProjector = new ZoneEventProjector();
    private final CombatProjector combatProjector = new CombatProjector(
            state,
            objectIdentityTracker,
            this::zoneType,
            this::playerName,
            this::objectDisplayName,
            this::targetDisplayName,
            this::event);
    private final DamageProjector damageProjector = new DamageProjector(
            state,
            objectIdentityTracker,
            this::playerName,
            this::objectDisplayName,
            this::event,
            this::markAnnotation);
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
    private final Map<Long, PendingTargetDecision> pendingTargetDecisions = new LinkedHashMap<>();
    /** Cast events retained until the parent Room card identity is known. */
    private final Map<Long, List<RoomCastProjection>> roomCastEvents = new LinkedHashMap<>();
    private final AbilityNameStore abilityNames;
    private final OpeningHandTracker openingHandTracker = new OpeningHandTracker();
    private boolean openingHandEventEmitted;

    private record PendingCast(long instanceId, long grpId, int seatId, String name) {}

    private enum RoomHalf { LEFT, RIGHT }

    private record RoomCastProjection(GameEvent event, RoomHalf half, int seatId) {}

    private record PendingTargetDecision(
            ObjectReference source,
            List<ObjectReference> legalTargets,
            int minimumSelections,
            int maximumSelections) {
        private PendingTargetDecision {
            legalTargets = List.copyOf(legalTargets);
        }
    }

    public GameEventProjector() { this(new AbilityNameStore(), null); }

    public GameEventProjector(AbilityNameStore abilityNames) {
        this(abilityNames, null);
    }

    public GameEventProjector(AbilityNameStore abilityNames, MatchState matchState) {
        this.abilityNames = abilityNames;
        this.matchState = matchState;
        if (matchState != null) {
            state.getPlayers().putAll(matchState.playerSnapshot());
            knownCards.putAll(matchState.knownCardSnapshot());
            observedCardsByGrpId.putAll(matchState.observedCardSnapshot());
        }
    }

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
            updateMatchState();
            return result;
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }


    private void updateMatchState() {
        if (matchState == null) return;
        matchState.observePlayers(state.getPlayers());
        matchState.observeKnownCards(knownCards);
        matchState.observeArenaCards(observedCardsByGrpId);
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

        if ("ClientMessageType_CancelActionReq".equals(type)) {
            cancelMostRecentPendingCast(source, result);
            return;
        }

        if ("ClientMessageType_SelectTargetsResp".equals(type)) {
            projectTargetDecisionResponse(source, payload, result);
            return;
        }

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

        long logicalId = objectIdentityTracker.logicalIdOf(instanceId);
        for (Map.Entry<Long, PendingCast> entry : new ArrayList<>(pendingCasts.entrySet())) {
            long pendingLogicalId = objectIdentityTracker.logicalIdOf(entry.getKey());
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
            openingHandEventEmitted = false;
            historicalObjectNames.clear();
            observedCardsByGrpId.clear();
            emittedEvents.clear();
            historicalAbilityOwnerNames.clear();
            pendingCasts.clear();
            pendingTargetDecisions.clear();
            roomCastEvents.clear();
            attachmentTracker.reset();
            pendingTurnSnapshot = null;
            pendingTurnSnapshotNeedsNextMessage = false;
            for (JsonElement element : arrayAt(config, "reservedPlayers")) {
                if (!element.isJsonObject()) continue;
                JsonObject player = element.getAsJsonObject();
                int seat = intAt(player, "systemSeatId", -1);
                if (seat >= 0) state.getPlayers().put(seat, stringAt(player, "playerName"));
            }
            GameEvent matchStarted = event(message, "Match started");
            matchStarted.setType(GameEventType.MATCH_STARTED);
            result.add(matchStarted);
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
            } else if ("GREMessageType_SelectTargetsReq".equals(type)) {
                observeTargetDecisionRequest(greMessage, cards);
            }
        }
    }

    private void observeTargetDecisionRequest(JsonObject greMessage,
                                              Map<Long, CardInfo> cards) {
        long messageId = longAt(greMessage, "msgId", -1);
        JsonObject request = objectAt(greMessage, "selectTargetsReq");
        if (messageId < 0 || request.size() == 0) return;

        ObjectReference source = objectReference(longAt(request, "sourceId", -1), cards);
        List<ObjectReference> legalTargets = new ArrayList<>();
        int minimumSelections = 0;
        int maximumSelections = 0;

        for (JsonElement targetGroupElement : arrayAt(request, "targets")) {
            if (!targetGroupElement.isJsonObject()) continue;
            JsonObject targetGroup = targetGroupElement.getAsJsonObject();
            minimumSelections += Math.max(0, intAt(targetGroup, "minTargets", 0));
            maximumSelections += Math.max(0, intAt(targetGroup, "maxTargets", 0));

            for (JsonElement targetElement : arrayAt(targetGroup, "targets")) {
                if (!targetElement.isJsonObject()) continue;
                JsonObject target = targetElement.getAsJsonObject();
                String legalAction = stringAt(target, "legalAction");
                if (!legalAction.isBlank() && !legalAction.contains("Select")) continue;
                long targetId = longAt(target, "targetInstanceId",
                        longAt(target, "targetPlayerId", -1));
                ObjectReference reference = objectReference(targetId, cards);
                if (reference != null && !containsReference(legalTargets, reference)) {
                    legalTargets.add(reference);
                }
            }
        }

        if (!legalTargets.isEmpty()) {
            pendingTargetDecisions.put(messageId, new PendingTargetDecision(
                    source, legalTargets, minimumSelections, maximumSelections));
        }
    }

    private void projectTargetDecisionResponse(LogMessageInterface source,
                                               JsonObject payload,
                                               List<GameEvent> result) {
        long responseTo = longAt(payload, "respId", -1);
        PendingTargetDecision pending = pendingTargetDecisions.remove(responseTo);
        if (pending == null) return;

        List<ObjectReference> selected = new ArrayList<>();
        JsonObject response = objectAt(payload, "selectTargetsResp");
        collectSelectedTargets(objectAt(response, "target"), selected);
        for (JsonElement targetElement : arrayAt(response, "targets")) {
            if (targetElement.isJsonObject()) {
                collectSelectedTargets(targetElement.getAsJsonObject(), selected);
            }
        }

        List<ObjectReference> alternatives = pending.legalTargets().stream()
                .filter(candidate -> !containsReference(selected, candidate))
                .toList();
        String chosen = selected.isEmpty()
                ? "no target"
                : selected.stream().map(this::referenceDisplayName)
                        .collect(Collectors.joining(", "));
        String sourceName = pending.source() == null
                ? "Unknown spell or ability"
                : referenceDisplayName(pending.source());

        GameEvent event = event(source, sourceName + " chooses " + chosen);
        event.setType(GameEventType.DECISION);
        event.setDecision(new DecisionObservation(
                DecisionObservation.Kind.TARGET,
                pending.source(),
                selected,
                alternatives,
                pending.minimumSelections(),
                pending.maximumSelections(),
                DecisionObservation.Confidence.EXPLICIT));
        if (pending.source() != null) event.getObjects().add(pending.source());
        selected.forEach(reference -> addReference(event, reference));
        alternatives.forEach(reference -> addReference(event, reference));
        result.add(event);
    }

    private void collectSelectedTargets(JsonObject target,
                                        List<ObjectReference> selected) {
        if (target.size() == 0) return;
        for (JsonElement selectedElement : arrayAt(target, "targets")) {
            if (!selectedElement.isJsonObject()) continue;
            JsonObject selectedTarget = selectedElement.getAsJsonObject();
            long targetId = longAt(selectedTarget, "targetInstanceId",
                    longAt(selectedTarget, "targetPlayerId", -1));
            ObjectReference reference = objectReference(targetId, knownCards);
            if (reference != null && !containsReference(selected, reference)) {
                selected.add(reference);
            }
        }
    }

    private ObjectReference objectReference(long arenaId,
                                            Map<Long, CardInfo> cards) {
        if (arenaId < 0) return null;
        if (state.getPlayers().containsKey((int) arenaId)) {
            return new ObjectReference(
                    -1, arenaId, -1, playerName((int) arenaId),
                    (int) arenaId, playerName((int) arenaId));
        }

        GameObjectState object = findObjectIncludingAliases(arenaId);
        if (object == null) return new ObjectReference(
                -1, arenaId, -1, "object " + arenaId, null, null);
        long logicalId = object.getLogicalObjectId() > 0
                ? object.getLogicalObjectId()
                : objectIdentityTracker.logicalIdOf(arenaId);
        return new ObjectReference(
                logicalId,
                arenaId,
                object.getGrpId(),
                objectDisplayName(object, cards),
                null,
                null);
    }

    private String referenceDisplayName(ObjectReference reference) {
        return reference.isPlayer()
                ? reference.playerName()
                : reference.name();
    }

    private boolean containsReference(List<ObjectReference> references,
                                      ObjectReference candidate) {
        return references.stream().anyMatch(existing -> sameReference(existing, candidate));
    }

    private boolean sameReference(ObjectReference left, ObjectReference right) {
        if (left.isPlayer() || right.isPlayer()) {
            return left.playerSeat() != null
                    && left.playerSeat().equals(right.playerSeat());
        }
        if (left.logicalObjectId() > 0 && right.logicalObjectId() > 0) {
            return left.logicalObjectId() == right.logicalObjectId();
        }
        return left.arenaInstanceId() == right.arenaInstanceId();
    }

    private void addReference(GameEvent event, ObjectReference reference) {
        if (!containsReference(event.getObjects(), reference)) {
            event.getObjects().add(reference);
        }
    }

    private GameEvent objectEvent(LogMessageInterface source,
                                  String text,
                                  GameObjectState... objects) {
        GameEvent event = event(source, text);
        for (GameObjectState object : objects) {
            if (object == null) continue;
            ObjectReference reference = objectReference(object.getInstanceId(), knownCards);
            if (reference != null) addReference(event, reference);
        }
        return event;
    }

    private void projectGameState(LogMessageInterface message, JsonObject incoming,
                                  Map<Long, CardInfo> cards, List<GameEvent> result) {
        int previousTurn = state.getTurnNumber() == null ? -1 : state.getTurnNumber();
        int messageStartIndex = result.size();
        updateTurnContext(objectAt(incoming, "turnInfo"));
        Map<Integer, Integer> previousLifeTotals = new LinkedHashMap<>(state.getLifeTotals());
        updatePlayers(message, arrayAt(incoming, "players"), result);
        updateZones(arrayAt(incoming, "zones"));

        JsonArray annotations = arrayAt(incoming, "annotations");
        JsonArray persistentAnnotations = arrayAt(incoming, "persistentAnnotations");
        objectIdentityTracker.observeIdChanges(annotations);
        learnAbilityKinds(annotations);
        attachmentTracker.reconcile(
                persistentAnnotations,
                arrayAt(incoming, "diffDeletedPersistentAnnotationIds"),
                objectIdentityTracker::logicalIdOf);

        Set<Long> transferredIds = zoneTransferAffectedIds(annotations);
        for (JsonElement element : arrayAt(incoming, "gameObjects")) {
            if (element.isJsonObject()) {
                projectObjectChange(message, element.getAsJsonObject(), cards, result, transferredIds);
            }
        }

        projectCounterChanges(message, annotations, result);
        reconcilePersistentCounters(persistentAnnotations);
        damageProjector.project(message, previousLifeTotals, annotations, cards, result);
        combatProjector.projectDeclarations(message, cards, result);
        projectZoneTransfers(message, annotations, cards, result);
        projectTargets(message, persistentAnnotations, cards, result);
        reorderBattlefieldEntryBeforeOwnAbilities(result, messageStartIndex);
        openingHandTracker.observe(state, knownCards);
        projectOpeningHand(message, result);
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
        event.setType(GameEventType.GAME_RESULT);
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
        GameObjectState current = objectIdentityTracker.copyForObservation(instanceId);

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
                    type = typeId < 0 ? "Unknown" : counterProjector.counterTypeName(typeId);
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
        if (isToken(current) && current.getCard() == null) {
            current.setCard(tokenResolver.resolve(current, knownCards, knownRelatedCards));
        }

        int incomingZone = json.has("zoneId") ? intAt(json, "zoneId", current.getZoneId()) : current.getZoneId();
        current.setZoneId(incomingZone);
        int previousSemanticZone = previous == null ? -1 : previous.getSemanticZoneId();
        if (!isTransientZone(incomingZone)) current.setSemanticZoneId(incomingZone);
        else if (previous != null) current.setSemanticZoneId(previousSemanticZone);
        state.getObjects().put(instanceId, current);
        repairRoomCastEvents(current, cards);
        if (current.getGrpId() > 0 && !isAbility(current)) {
            observedCardsByGrpId.put(current.getGrpId(), current.copy());
        }

        /*
         * Seeing the spell on the Stack confirms the cast attempt, but Arena can
         * still roll that object back when the player cancels target, mode, or
         * payment selection. Keep the correlation until the object leaves the
         * Stack normally or an explicit cancellation is observed.
         */
        if (previous != null
                && "Stack".equals(zoneType(previousSemanticZone))
                && !"Stack".equals(zoneType(incomingZone))
                && !"Hand".equals(zoneType(incomingZone))) {
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

        /*
         * RoomLeft/RoomRight objects are facets of the parent Room permanent,
         * not independent permanents. Retain them in canonical state as Arena
         * evidence, but never project their zone changes as semantic objects.
         */
        if (isRoomFacet(current)) return;

        if (transferredIds.contains(instanceId)) return; // authoritative annotation handles it
        if (previous == null) emitNewVisibleObject(source, current, cards, result);
        else if (current.getSemanticZoneId() >= 0 && previousSemanticZone >= 0
                && current.getSemanticZoneId() != previousSemanticZone) {
            result.add(objectEvent(source, describeTransition(previous, current, cards, ""), current));
        }
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

                /*
                 * Room halves are represented by Arena as child game objects,
                 * but their zone transfers describe the parent Room spell or
                 * permanent. They are evidence for the parent, not separate
                 * semantic objects.
                 */
                if (isRoomFacet(object)) continue;

                GameObjectState before = object.copy();
                before.setSemanticZoneId(fromZone);
                object.setSemanticZoneId(toZone);
                object.setZoneId(toZone);

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
                } else if ("Stack".equals(zoneType(fromZone))) {
                    removePendingCastFor(instanceId, object);
                }

                if ("CastSpell".equals(category) && isRoomParent(object)) {
                    RoomHalf half = roomHalfFor(object);
                    if (half != null && object.getGrpId() > 0
                            && !object.getUnlockedRoomGrpIds().contains(object.getGrpId())) {
                        object.getUnlockedRoomGrpIds().add(object.getGrpId());
                    }
                    GameEvent castEvent = objectEvent(
                            source,
                            describeRoomCast(object, cards, half),
                            object);
                    result.add(castEvent);
                    if (half != null) {
                        roomCastEvents
                                .computeIfAbsent(object.getLogicalObjectId(), ignored -> new ArrayList<>())
                                .add(new RoomCastProjection(
                                        castEvent, half, object.getControllerSeatId()));
                    }
                } else {
                    result.add(objectEvent(source,
                            describeTransition(before, object, cards, category), object));
                }
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

            List<Long> targetIds = entry.getValue().stream().distinct().toList();
            String targets = targetIds.stream()
                    .map(id -> targetDisplayName(id, cards))
                    .collect(Collectors.joining(", "));
            if (!targets.isBlank()) {
                GameEvent targetEvent = event(source, sourceName + " targets " + targets);
                ObjectReference sourceReference = objectReference(sourceId, cards);
                if (sourceReference != null) addReference(targetEvent, sourceReference);
                for (long targetId : targetIds) {
                    ObjectReference targetReference = objectReference(targetId, cards);
                    if (targetReference != null) addReference(targetEvent, targetReference);
                }
                result.add(targetEvent);
            }
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
                result.add(objectEvent(source, actor + " casts " + name, current));
            }
        } else if (!isAbility(current)) {
            if (isLand(current, cards)) {
                result.add(objectEvent(source, actor + " plays " + name + tappedSuffix(current), current));
            } else {
                result.add(objectEvent(source, name + " entered the battlefield"
                        + tappedSuffix(current) + " under " + actor + "'s control", current));
            }
        }
    }

    private String describeTransition(GameObjectState previous, GameObjectState current,
                                      Map<Long, CardInfo> cards, String category) {
        String from = zoneType(previous.getSemanticZoneId());
        String to = zoneType(current.getSemanticZoneId());
        String name = objectDisplayName(current, cards);
        String actor = playerName(current.getControllerSeatId());

        ZoneTransitionClassifier.Kind kind = zoneTransitionClassifier.classify(
                from,
                to,
                category,
                isAbility(current),
                isLand(current, cards));

        return zoneEventProjector.describe(
                kind,
                from,
                to,
                actor,
                name,
                abilityVerb(current),
                tappedSuffix(current));
    }


    private GameObjectState findObjectOwningAbilityGroup(long abilityGrpId) {
        return state.getObjects().values().stream()
                .filter(object -> object.getUniqueAbilityGrpIds().contains(abilityGrpId))
                .filter(objectIdentityTracker::isCurrent)
                .findFirst()
                .orElseGet(() -> state.getObjects().values().stream()
                        .filter(object -> object.getUniqueAbilityGrpIds().contains(abilityGrpId))
                        .findFirst()
                        .orElse(null));
    }

    /**
     * Arena game-state messages are batched observations rather than a
     * rules-ordered event stream. An ability object can therefore be listed on
     * the stack before the same message's authoritative zone-transfer
     * annotation reports its source entering the battlefield.
     *
     * <p>Normalize only the explicit causal relationship we can prove: a
     * battlefield-entry (or land-play) event precedes ability events whose
     * structured source group id is the entering object's group id. Unrelated
     * triggers retain Arena's observed order.</p>
     */
    private void reorderBattlefieldEntryBeforeOwnAbilities(List<GameEvent> events, int fromIndex) {
        for (int entryIndex = fromIndex; entryIndex < events.size(); entryIndex++) {
            GameEvent entryEvent = events.get(entryIndex);
            if (!isBattlefieldEntryEvent(entryEvent)) continue;

            Set<Long> enteringGrpIds = entryEvent.getObjects().stream()
                    .filter(reference -> !reference.isPlayer())
                    .map(ObjectReference::arenaGrpId)
                    .filter(grpId -> grpId > 0)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (enteringGrpIds.isEmpty()) continue;

            int earliestOwnAbility = entryIndex;
            for (int i = fromIndex; i < entryIndex; i++) {
                AbilityReference ability = events.get(i).getAbility();
                if (ability != null && enteringGrpIds.contains(ability.getSourceGrpId())) {
                    earliestOwnAbility = i;
                    break;
                }
            }

            if (earliestOwnAbility < entryIndex) {
                GameEvent entry = events.remove(entryIndex);
                events.add(earliestOwnAbility, entry);
            }
        }
    }

    private boolean isBattlefieldEntryEvent(GameEvent event) {
        if (event.getAbility() != null || event.getObjects().isEmpty()) return false;
        String text = event.getText();
        return text != null
                && (text.contains(" enters the battlefield")
                || text.contains(" entered the battlefield")
                || text.contains(" plays "));
    }

    private String tappedSuffix(GameObjectState object) {
        if (object.getTapped() == null) return "";
        return object.getTapped() ? " tapped" : " untapped";
    }

    private String abilityVerb(GameObjectState ability) {
        if (state.getActivatedAbilityInstances().contains(ability.getInstanceId())) return "activates";
        if (state.getTriggeredAbilityInstances().contains(ability.getInstanceId())) return "triggers ability of";
        return "uses ability of";
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
            return label.isBlank() ? source : source + " — " + label;
        }
        if (object.getCard() != null && object.getCard().getName() != null && !object.getCard().getName().isBlank()) {
            return object.getCard().getName();
        }
        if (isToken(object)) return tokenResolver.descriptiveName(object);
        String resolved = cardName(object.getGrpId(), cards);
        if (!resolved.startsWith("ArenaCard#")) return resolved;
        return observedCardDescription(object);
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
                if (object != null) counterProjector.applyDelta(object, counterType, delta);
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
                if (object != null) counterProjector.setCount(object, counterType, count);
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

    private GameObjectState findObjectIncludingAliases(long instanceId) {
        return objectIdentityTracker.findIncludingAliases(instanceId);
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
            snapshot.getKnownHand().addAll(knownCardsInZone(player.getKey(), "Hand"));
            snapshot.getKnownGraveyard().addAll(knownCardsInZone(player.getKey(), "Graveyard"));
            snapshot.getKnownExile().addAll(knownCardsInZone(player.getKey(), "Exile"));
            event.getTurnSnapshot().add(snapshot);
        }
        return event;
    }

    private List<BoardPermanentSnapshot> currentBattlefieldObservation() {
        return state.getObjects().values().stream()
                .filter(objectIdentityTracker::isCurrent)
                .filter(object -> "Battlefield".equals(zoneType(object.getSemanticZoneId())))
                .filter(object -> !isAbility(object))
                .filter(object -> !isRoomFacet(object))
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
                    permanent.setAttachedToLogicalObjectId(
                            attachmentTracker.attachedHostFor(object.getLogicalObjectId()));
                    object.getCounters().forEach(counter ->
                            permanent.getCounters().add(counter.copy()));
                    roomUnlockedHalfNames(object, knownCards)
                            .forEach(permanent.getUnlockedRoomHalves()::add);
                    observedEvergreenAbilities(object)
                            .forEach(permanent.getEvergreenAbilities()::add);
                    return permanent;
                })
                .toList();
    }

    private List<String> observedEvergreenAbilities(GameObjectState object) {
        java.util.LinkedHashSet<String> abilities = new java.util.LinkedHashSet<>();
        if (object.getCard() != null && object.getCard().getKeywords() != null) {
            object.getCard().getKeywords().stream()
                    .map(keyword -> keyword == null ? "" : keyword.trim().toLowerCase(java.util.Locale.ROOT))
                    .filter(this::isBattleRelevantEvergreen)
                    .forEach(abilities::add);
        }
        object.getCounters().stream()
                .map(counter -> counter.getType() == null ? "" : counter.getType())
                .map(type -> type.toLowerCase(java.util.Locale.ROOT)
                        .replace("countertype_", "")
                        .replace("counter_", "")
                        .replace(" counter", "")
                        .replace('_', ' ')
                        .trim())
                .filter(type -> isBattleRelevantEvergreen(type) || "shield".equals(type))
                .forEach(abilities::add);
        return List.copyOf(abilities);
    }

    private boolean isBattleRelevantEvergreen(String ability) {
        return java.util.Set.of(
                "deathtouch", "defender", "double strike", "first strike", "flying",
                "haste", "hexproof", "indestructible", "lifelink", "menace",
                "reach", "trample", "vigilance", "ward").contains(ability);
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

    private List<CardInfo> knownCardsInZone(int seat, String zoneName) {
        return state.getObjects().values().stream()
                .filter(objectIdentityTracker::isCurrent)
                .filter(object -> object.getOwnerSeatId() == seat)
                .filter(object -> zoneName.equals(zoneType(object.getSemanticZoneId())))
                .filter(object -> !isAbility(object) && !isRoomFacet(object))
                .map(GameObjectState::getCard)
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparing(CardInfo::getName,
                        java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    private boolean isRoomParent(GameObjectState object) {
        return object != null
                && !isRoomFacet(object)
                && object.getSubtypes().contains("Room");
    }

    private RoomHalf roomHalfFor(GameObjectState room) {
        if (!isRoomParent(room) || room.getGrpId() <= 0) return null;
        for (GameObjectState candidate : state.getObjects().values()) {
            if (!isRoomFacet(candidate) || candidate.getGrpId() != room.getGrpId()) continue;
            long parentLogicalId = objectIdentityTracker.logicalIdOf(candidate.getParentId());
            if (parentLogicalId != room.getLogicalObjectId()) continue;
            return "GameObjectType_RoomLeft".equals(candidate.getObjectType())
                    ? RoomHalf.LEFT
                    : "GameObjectType_RoomRight".equals(candidate.getObjectType())
                    ? RoomHalf.RIGHT
                    : null;
        }
        return null;
    }

    private String describeRoomCast(GameObjectState room,
                                    Map<Long, CardInfo> cards,
                                    RoomHalf half) {
        String halfName = roomHalfName(room, cards, half);
        String actor = playerName(room.getControllerSeatId());
        return actor + " casts " + halfName;
    }

    private String roomHalfName(GameObjectState room,
                                Map<Long, CardInfo> cards,
                                RoomHalf half) {
        CardInfo parentCard = roomParentCard(room, cards);
        if (parentCard != null && parentCard.getCardFaces() != null
                && parentCard.getCardFaces().size() >= 2 && half != null) {
            CardFaceInfo face = parentCard.getCardFaces().get(half == RoomHalf.LEFT ? 0 : 1);
            if (face != null && face.getName() != null && !face.getName().isBlank()) {
                return face.getName();
            }
        }
        return half == RoomHalf.LEFT ? "left Room half"
                : half == RoomHalf.RIGHT ? "right Room half"
                : objectDisplayName(room, cards);
    }

    private CardInfo roomParentCard(GameObjectState room, Map<Long, CardInfo> cards) {
        if (room == null) return null;
        if (room.getCard() != null && room.getCard().getCardFaces() != null
                && room.getCard().getCardFaces().size() >= 2) {
            return room.getCard();
        }
        CardInfo direct = cards.get(room.getGrpId());
        if (direct != null && direct.getCardFaces() != null
                && direct.getCardFaces().size() >= 2) {
            return direct;
        }
        return null;
    }

    private List<String> roomUnlockedHalfNames(GameObjectState room,
                                                Map<Long, CardInfo> cards) {
        if (!isRoomParent(room) || room.getUnlockedRoomGrpIds().isEmpty()) return List.of();
        List<String> names = new ArrayList<>();
        for (long grpId : room.getUnlockedRoomGrpIds()) {
            RoomHalf half = null;
            for (GameObjectState candidate : state.getObjects().values()) {
                if (isRoomFacet(candidate) && candidate.getGrpId() == grpId) {
                    half = "GameObjectType_RoomLeft".equals(candidate.getObjectType())
                            ? RoomHalf.LEFT : RoomHalf.RIGHT;
                    break;
                }
            }
            String name = roomHalfName(room, cards, half);
            if (!names.contains(name)) names.add(name);
        }
        return names;
    }

    private void repairRoomCastEvents(GameObjectState room,
                                      Map<Long, CardInfo> cards) {
        if (!isRoomParent(room)) return;
        CardInfo parentCard = roomParentCard(room, cards);
        if (parentCard == null) return;
        List<RoomCastProjection> projections =
                roomCastEvents.get(room.getLogicalObjectId());
        if (projections == null) return;
        for (RoomCastProjection projection : projections) {
            projection.event().setText(
                    playerName(projection.seatId()) + " casts "
                            + roomHalfName(room, cards, projection.half()));
            if (!projection.event().getCards().contains(parentCard)) {
                projection.event().getCards().add(parentCard);
            }
        }
    }

    private boolean isRoomFacet(GameObjectState object) {
        if (object == null) return false;

        String objectType = object.getObjectType();
        if ("GameObjectType_RoomLeft".equals(objectType)
                || "GameObjectType_RoomRight".equals(objectType)) {
            return true;
        }

        /*
         * Some incremental observations omit the object type. The stable
         * relationship is that a Room facet is a Room subtype child of the
         * parent Room card.
         */
        return object.getParentId() >= 0
                && object.getSubtypes().contains("Room");
    }

    private boolean isToken(GameObjectState object) {
        return object.getObjectType() != null && object.getObjectType().contains("Token");
    }

    private void projectOpeningHand(LogMessageInterface source, List<GameEvent> result) {
        if (openingHandEventEmitted || !state.isOpeningHandFinalized()) {
            return;
        }
        List<CardInfo> cards = openingHand();
        if (cards.isEmpty()) {
            return;
        }

        String player = openingHandPlayer();
        int mulligans = state.getMulliganCount();
        String cardNames = cards.stream()
                .map(CardInfo::getName)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.joining(", "));
        String mulliganText = mulligans == 0
                ? "keeps the opening hand"
                : "keeps after " + mulligans + " mulligan" + (mulligans == 1 ? "" : "s");
        GameEvent opening = event(source, player + " " + mulliganText + ": " + cardNames);
        opening.setType(GameEventType.OPENING_HAND);
        opening.getCards().clear();
        opening.getCards().addAll(cards);
        result.add(opening);
        openingHandEventEmitted = true;
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

}
