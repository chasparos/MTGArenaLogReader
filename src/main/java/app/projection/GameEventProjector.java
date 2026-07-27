package app.projection;

import app.model.event.AbilityReference;
import app.model.card.CardInfo;
import app.model.event.GameEvent;
import app.model.event.GameEventType;
import app.model.event.ObjectReference;
import app.model.event.ZoneTransitionObservation;
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
    private final AttachmentTracker attachmentTracker = new AttachmentTracker();
    private final CounterProjector counterProjector = new CounterProjector();
    private final TokenResolver tokenResolver = new TokenResolver();
    private final GameObjectProjector gameObjectProjector =
            new GameObjectProjector(
                    state,
                    objectIdentityTracker,
                    counterProjector,
                    tokenResolver,
                    knownRelatedCards,
                    this::isTransientZone);
    private final ObjectLifecycleEvents objectLifecycleEvents =
            new ObjectLifecycleEvents(new ObjectLifecycleEvents.Context() {
                @Override public String zoneType(int zoneId) {
                    return GameEventProjector.this.zoneType(zoneId);
                }
                @Override public String playerName(int seatId) {
                    return GameEventProjector.this.playerName(seatId);
                }
                @Override public String objectName(
                        GameObjectState object, Map<Long, CardInfo> cards) {
                    return objectDisplayName(object, cards);
                }
                @Override public boolean isAbility(GameObjectState object) {
                    return GameEventProjector.this.isAbility(object);
                }
                @Override public boolean isLand(
                        GameObjectState object, Map<Long, CardInfo> cards) {
                    return GameEventProjector.this.isLand(object, cards);
                }
                @Override public String abilityVerb(GameObjectState ability) {
                    return GameEventProjector.this.abilityVerb(ability);
                }
            });
    private final GameResultProjector gameResultProjector =
            new GameResultProjector(state, this::playerName);
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
    private final RoomProjectionSupport rooms = new RoomProjectionSupport(
            state, objectIdentityTracker, this::playerName, this::objectDisplayName);
    private final ZoneTransferProjector zoneTransfers =
            new ZoneTransferProjector(
                    state,
                    rooms,
                    this::markAnnotation,
                    new ZoneTransferProjector.Context() {
                        @Override public String zoneType(int zoneId) {
                            return GameEventProjector.this.zoneType(zoneId);
                        }
                        @Override public PendingCastTracker.PendingCast removePendingCast(
                                long instanceId, GameObjectState object) {
                            return removePendingCastFor(instanceId, object);
                        }
                        @Override public GameEvent cancelledCast(
                                LogMessageInterface source,
                                PendingCastTracker.PendingCast pending) {
                            return cancelledCastEvent(source, pending);
                        }
                        @Override public GameEvent objectEvent(
                                LogMessageInterface source, String text,
                                GameObjectState object) {
                            return GameEventProjector.this.objectEvent(source, text, object);
                        }
                        @Override public GameEvent transitionEvent(
                                LogMessageInterface source, GameObjectState before,
                                GameObjectState object, Map<Long, CardInfo> cards,
                                String category) {
                            return GameEventProjector.this.transitionEvent(
                                    source, before, object, cards, category);
                        }
                    });
    private final PlayerSnapshotProjector playerSnapshotProjector =
            new PlayerSnapshotProjector(new PlayerSnapshotProjector.Context() {
                @Override public GameState state() { return state; }
                @Override public boolean isCurrent(GameObjectState object) {
                    return objectIdentityTracker.isCurrent(object);
                }
                @Override public String zoneType(int zoneId) {
                    return GameEventProjector.this.zoneType(zoneId);
                }
                @Override public boolean isAbility(GameObjectState object) {
                    return GameEventProjector.this.isAbility(object);
                }
                @Override public boolean isRoomFacet(GameObjectState object) {
                    return rooms.isFacet(object);
                }
                @Override public String objectName(
                        GameObjectState object, Map<Long, CardInfo> cards) {
                    return objectDisplayName(object, cards);
                }
                @Override public Long attachedHost(long logicalObjectId) {
                    return attachmentTracker.attachedHostFor(logicalObjectId);
                }
                @Override public List<String> unlockedRoomHalves(
                        GameObjectState object, Map<Long, CardInfo> cards) {
                    return rooms.unlockedHalfNames(object, cards);
                }
                @Override public String playerName(int seatId) {
                    return GameEventProjector.this.playerName(seatId);
                }
            });
    /** Delay turn snapshots until the first-main decision state for that turn. */
    private Integer pendingTurnSnapshot;
    private boolean pendingTurnSnapshotNeedsNextMessage;

    /*
     * A cast is only final once Arena moves the object to the Stack.  Until
     * then it may be in Limbo while targets, modes or payments are selected.
     * Keeping this tiny correlation record lets a Limbo -> Hand rollback
     * become a semantic cancellation instead of a misleading zone movement.
     */
    private final PendingCastTracker pendingCastTracker =
            new PendingCastTracker(objectIdentityTracker);
    private final TargetDecisionTracker targetDecisionTracker =
            new TargetDecisionTracker(this::objectReference, this::referenceDisplayName);
    private final ObjectNameResolver names;
    private final OpeningHandTracker openingHandTracker = new OpeningHandTracker();
    private final LocalPlayerStore localPlayerStore;
    private boolean openingHandEventEmitted;

    public GameEventProjector() { this(null, new LocalPlayerStore()); }

    public GameEventProjector(MatchState matchState) {
        this(matchState, new LocalPlayerStore());
    }

    GameEventProjector(MatchState matchState, LocalPlayerStore localPlayerStore) {
        this.localPlayerStore = java.util.Objects.requireNonNull(localPlayerStore, "localPlayerStore");
        state.setLocalPlayerName(localPlayerStore.load().orElse(null));
        this.names = new ObjectNameResolver(
                state,
                objectIdentityTracker,
                tokenResolver,
                observedCardsByGrpId,
                emittedEvents,
                this::playerName);
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
            names.repairPreviouslyUnknownNames(cards);
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
                    pendingCastTracker.remember(instanceId, grpId, seatId, name);
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
            if (PendingCastTracker.containsCancelAction(payload)) {
                cancelMostRecentPendingCast(source, result);
            }
        }
    }

    private void cancelMostRecentPendingCast(LogMessageInterface source,
                                             List<GameEvent> result) {
        PendingCastTracker.PendingCast pending = pendingCastTracker.removeMostRecent();
        if (pending == null) return;
        result.add(cancelledCastEvent(source, pending));
    }

    private GameEvent cancelledCastEvent(LogMessageInterface source,
                                         PendingCastTracker.PendingCast pending) {
        String actor = pending.seatId() < 0
                ? "Player"
                : playerName(pending.seatId());
        return event(source, actor + " cancels casting " + pending.name());
    }

    private PendingCastTracker.PendingCast removePendingCastFor(
            long instanceId, GameObjectState object) {
        return pendingCastTracker.removeFor(instanceId, object);
    }

    private void projectRoomState(LogMessageInterface message, JsonObject event, List<GameEvent> result) {
        JsonObject config = objectAt(event, "gameRoomInfo", "gameRoomConfig");
        String incomingMatchId = stringAt(config, "matchId");
        if (!incomingMatchId.isBlank() && !incomingMatchId.equals(state.getMatchId())) {
            state.reset(incomingMatchId);
            openingHandEventEmitted = false;
            observedCardsByGrpId.clear();
            emittedEvents.clear();
            names.clearHistory();
            pendingCastTracker.clear();
            targetDecisionTracker.clear();
            rooms.clear();
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
                targetDecisionTracker.observeRequest(greMessage, cards);
            }
        }
    }

    private void projectTargetDecisionResponse(LogMessageInterface source,
                                               JsonObject payload,
                                               List<GameEvent> result) {
        targetDecisionTracker.resolveResponse(payload, knownCards).ifPresent(decision -> {
            GameEvent event = event(source, decision.text());
            event.setType(GameEventType.DECISION);
            event.setDecision(decision.observation());
            decision.references().forEach(reference -> addReference(event, reference));
            result.add(event);
        });
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

    private GameEvent transitionEvent(LogMessageInterface source,
                                      GameObjectState previous,
                                      GameObjectState current,
                                      Map<Long, CardInfo> cards,
                                      String category) {
        ObjectLifecycleEvents.Transition transition =
                objectLifecycleEvents.transition(previous, current, cards, category);
        GameEvent event = objectEvent(source, transition.text(), current);
        ObjectReference subject = objectReference(current.getInstanceId(), cards);
        event.setZoneTransition(new ZoneTransitionObservation(
                transition.fromZone(), transition.toZone(), transition.reason(), subject));
        return event;
    }

    private GameEvent objectEvent(LogMessageInterface source,
                                  String text,
                                  GameObjectState... objects) {
        GameEvent event = event(source, text);
        for (GameObjectState object : objects) {
            if (object == null) continue;
            ObjectReference reference = objectReference(object.getInstanceId(), knownCards);
            if (reference != null) addReference(event, reference);
            CardInfo card = object.getCard() != null
                    ? object.getCard()
                    : names.cardForGrpId(object.getGrpId(), knownCards);
            if (card != null && !event.getCards().contains(card)) {
                event.getCards().add(card);
            }
        }
        return event;
    }

    private void projectGameState(LogMessageInterface message, JsonObject incoming,
                                  Map<Long, CardInfo> cards, List<GameEvent> result) {
        int previousTurn = state.getTurnNumber() == null ? -1 : state.getTurnNumber();
        int messageStartIndex = result.size();
        updateTurnContext(objectAt(incoming, "turnInfo"));
        Map<Integer, Integer> previousLifeTotals = new LinkedHashMap<>(state.getLifeTotals());
        playerSnapshotProjector.observePlayers(arrayAt(incoming, "players"))
                .forEach(text -> result.add(event(message, text)));
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
        openingHandTracker.observe(state, knownCards).ifPresent(this::rememberLocalPlayer);
        projectOpeningHand(message, result);
        boolean turnChanged = state.getTurnNumber() != null && state.getTurnNumber() != previousTurn;
        if (turnChanged && state.getLastSnapshotTurn() != state.getTurnNumber()) {
            pendingTurnSnapshot = state.getTurnNumber();
        }
        if (pendingTurnSnapshot != null
                && pendingTurnSnapshot.equals(state.getTurnNumber())
                && playerSnapshotProjector.isFirstMainBoundary()) {
            result.add(messageStartIndex, turnSnapshotEvent(message));
            state.setLastSnapshotTurn(state.getTurnNumber());
            pendingTurnSnapshot = null;
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
        GameResultProjector.Projection projection =
                gameResultProjector.project(gameInfo, players, preceding);
        GameEvent event = event(source, projection.text());
        event.setType(GameEventType.GAME_RESULT);
        event.setGameResult(projection.result());
        return event;
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
            if (json.has("objectInstanceIds")) {
                JsonArray objects = arrayAt(json, "objectInstanceIds");
                zone.setObjectCount(objects.size());
                zone.getObjectInstanceIds().clear();
                for (JsonElement objectId : objects) {
                    if (objectId.isJsonPrimitive()) {
                        zone.getObjectInstanceIds().add(objectId.getAsLong());
                    }
                }
                zone.setObjectInstancesKnown(true);
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
        GameObjectProjector.Observation observation =
                gameObjectProjector.apply(json, cards);
        if (observation == null) return;
        GameObjectState previous = observation.previous();
        GameObjectState current = observation.current();
        long instanceId = current.getInstanceId();
        int previousSemanticZone = observation.previousSemanticZone();
        int incomingZone = observation.incomingZone();
        rooms.attachCanonicalParentCard(current, cards);
        rooms.repairCastEvents(current, cards);
        if (current.getGrpId() > 0 && !isAbility(current)
                && !rooms.isTransientHalfIdentity(current, cards)) {
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
        names.remember(instanceId, current, cards);

        /*
         * RoomLeft/RoomRight objects are facets of the parent Room permanent,
         * not independent permanents. Retain them in canonical state as Arena
         * evidence, but never project their zone changes as semantic objects.
         */
        if (rooms.isFacet(current)) return;

        if (transferredIds.contains(instanceId)) {
            return; // authoritative annotation handles zone movement and face changes
        } else if (previous != null && previous.getGrpId() > 0 && current.getGrpId() > 0
                && previous.getGrpId() != current.getGrpId()
                && !rooms.isParent(current)
                && "Battlefield".equals(zoneType(current.getSemanticZoneId()))) {
            result.add(objectEvent(source,
                    playerName(current.getControllerSeatId()) + " transforms "
                            + objectDisplayName(previous, cards) + " into "
                            + objectDisplayName(current, cards), previous, current));
        } else if (previous == null) {
            emitNewVisibleObject(source, current, cards, result);
        } else if (current.getSemanticZoneId() >= 0 && previousSemanticZone >= 0
                && current.getSemanticZoneId() != previousSemanticZone) {
            result.add(transitionEvent(source, previous, current, cards, ""));
        }
    }


    private void projectZoneTransfers(LogMessageInterface source, JsonArray annotations,
                                      Map<Long, CardInfo> cards, List<GameEvent> result) {
        zoneTransfers.project(source, annotations, cards, result);
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
            long abilityGrpId = sourceGrpIds.getOrDefault(sourceId, -1L);
            String sourceName = names.sourceName(sourceId, abilityGrpId, cards);

            List<Long> targetIds = entry.getValue().stream().distinct().toList();
            String targets = targetIds.stream()
                    .map(id -> targetDisplayName(id, cards))
                    .collect(Collectors.joining(", "));
            if (!targets.isBlank() && !isSagaSelfTarget(sourceId, abilityGrpId, targetIds, cards)) {
                GameEvent targetEvent = event(source, sourceName + " targets " + targets);
                ObjectReference sourceReference = objectReference(sourceId, cards);
                if (sourceReference != null) addReference(targetEvent, sourceReference);
                addCardForObject(targetEvent, findObjectIncludingAliases(sourceId));
                for (long targetId : targetIds) {
                    ObjectReference targetReference = objectReference(targetId, cards);
                    if (targetReference != null) addReference(targetEvent, targetReference);
                    addCardForObject(targetEvent, findObjectIncludingAliases(targetId));
                }
                result.add(targetEvent);
            }
        }
    }

    private boolean isSagaSelfTarget(long sourceId, long abilityGrpId,
                                     List<Long> targetIds, Map<Long, CardInfo> cards) {
        if (targetIds.size() != 1) return false;
        GameObjectState sourceObject = findObjectIncludingAliases(sourceId);
        if (sourceObject == null || !isAbility(sourceObject)) return false;
        if (sagaChapterForAbility(sourceObject) == null) return false;
        GameObjectState target = findObjectIncludingAliases(targetIds.get(0));
        if (target == null) return false;
        long sourceGrpId = sourceObject.getObjectSourceGrpId();
        if (sourceGrpId <= 0) return false;
        if (target.getGrpId() == sourceGrpId) return true;
        CardInfo sourceCard = names.cardForGrpId(sourceGrpId, cards);
        return sourceCard != null && target.getCard() != null
                && sourceCard.getName() != null
                && sourceCard.getName().equals(target.getCard().getName());
    }

    private void addCardForObject(GameEvent event, GameObjectState object) {
        if (event == null || object == null) return;
        long grpId = isAbility(object)
                ? object.getObjectSourceGrpId() : object.getGrpId();
        CardInfo card = object.getCard() != null && !isAbility(object)
                ? object.getCard() : names.cardForGrpId(grpId, knownCards);
        if (card != null && !event.getCards().contains(card)) {
            event.getCards().add(card);
        }
    }

    private void emitNewVisibleObject(LogMessageInterface source, GameObjectState current,
                                      Map<Long, CardInfo> cards, List<GameEvent> result) {
        ObjectLifecycleEvents.Description description =
                objectLifecycleEvents.newlyVisible(current, cards);
        if (description == null) return;
        result.add(description.ability()
                ? abilityEvent(source, description.text(), current)
                : objectEvent(source, description.text(), current));
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

    private String abilityVerb(GameObjectState ability) {
        if (state.getActivatedAbilityInstances().contains(ability.getInstanceId())) return "activates";
        if (state.getTriggeredAbilityInstances().contains(ability.getInstanceId())) return "triggers ability of";
        // Arena does not always include TriggeringObject before the ability reaches
        // the stack. Unknown non-activated ability objects are still triggers, not
        // player-initiated activations.
        return "triggers ability of";
    }

    private boolean isAbility(GameObjectState object) {
        return "GameObjectType_Ability".equals(object.getObjectType());
    }

    private String objectDisplayName(long instanceId, Map<Long, CardInfo> cards) {
        return names.displayName(instanceId, cards);
    }

    private String targetDisplayName(long id, Map<Long, CardInfo> cards) {
        return names.targetName(id, cards);
    }

    private String objectDisplayName(GameObjectState object, Map<Long, CardInfo> cards) {
        return names.displayName(object, cards);
    }


    private String observedCardDescription(GameObjectState object) {
        return names.observedDescription(object);
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
                    playerSnapshotProjector.applyPlayerCounter(
                                    (int) affectedId, counterType, delta)
                            .ifPresent(text -> result.add(event(source, text)));
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

    private GameObjectState findObjectIncludingAliases(long instanceId) {
        return objectIdentityTracker.findIncludingAliases(instanceId);
    }

    private GameEvent turnSnapshotEvent(LogMessageInterface source) {
        GameEvent event = event(source, "Turn state");
        PlayerSnapshotProjector.TurnSnapshot snapshot =
                playerSnapshotProjector.snapshot(knownCards);
        event.getBattlefieldObservation().addAll(snapshot.battlefield());
        event.getTurnSnapshot().addAll(snapshot.players());
        return event;
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
        String kind = state.getActivatedAbilityInstances().contains(ability.getInstanceId()) ? "activated" :
                state.getTriggeredAbilityInstances().contains(ability.getInstanceId()) ? "triggered" : "unknown";
        CardInfo sourceCard = names.cardForGrpId(
                ability.getObjectSourceGrpId(), knownCards);
        String effectText = AbilityHeuristics.infer(sourceCard, kind);
        Integer sagaChapter = sagaChapterForAbility(ability);
        if (sagaChapter != null) {
            String chapterText = cardName(ability.getObjectSourceGrpId(), knownCards)
                    + " — chapter " + romanNumeral(sagaChapter) + " ability triggers";
            text = effectText.isBlank() ? chapterText : chapterText + ": " + effectText;
        }
        GameEvent event = event(source, text);
        AbilityReference reference = new AbilityReference();
        reference.setAbilityGrpId(ability.getGrpId());
        reference.setSourceGrpId(ability.getObjectSourceGrpId());
        reference.setSourceName(cardName(ability.getObjectSourceGrpId(), knownCards));
        reference.setKind(kind);
        reference.setChapter(sagaChapter);
        reference.setEffectText(effectText);
        reference.setConfidence(effectText.isBlank() ? "UNKNOWN" : "ORACLE_HEURISTIC");
        event.setAbility(reference);
        if (sourceCard != null && !event.getCards().contains(sourceCard)) event.getCards().add(sourceCard);
        return event;
    }

    private Integer sagaChapterForAbility(GameObjectState ability) {
        if (ability == null || ability.getObjectSourceGrpId() <= 0) return null;
        GameObjectState source = state.getObjects().values().stream()
                .filter(object -> object.getGrpId() == ability.getObjectSourceGrpId())
                .filter(object -> object.getSubtypes().contains("Saga")
                        || (object.getCard() != null
                        && object.getCard().effectiveTypeLine() != null
                        && object.getCard().effectiveTypeLine().contains("Saga")))
                .findFirst().orElse(null);
        if (source == null) return null;
        int index = source.getUniqueAbilityGrpIds().indexOf(ability.getGrpId());
        return index < 0 ? null : index + 1;
    }

    private String romanNumeral(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(value);
        };
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
        return names.cardName(grpId, cards);
    }


    private void rememberLocalPlayer(int seatId) {
        String name = state.getPlayers().get(seatId);
        if (name == null || name.isBlank()) return;
        state.setLocalPlayerName(name);
        localPlayerStore.save(name);
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
