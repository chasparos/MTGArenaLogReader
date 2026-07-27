package app.projection;

import app.model.card.CardInfo;
import app.model.event.DecisionObservation;
import app.model.event.ObjectReference;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static app.projection.ArenaJson.arrayAt;
import static app.projection.ArenaJson.intAt;
import static app.projection.ArenaJson.longAt;
import static app.projection.ArenaJson.objectAt;
import static app.projection.ArenaJson.stringAt;

/**
 * Correlates explicit target-selection requests with their later responses.
 * It retains decision-scoped state and returns structured observations without
 * owning event creation or canonical game state.
 */
final class TargetDecisionTracker {
    record ResolvedDecision(
            String text,
            DecisionObservation observation,
            List<ObjectReference> references) {
        ResolvedDecision {
            references = List.copyOf(references);
        }
    }

    private record PendingDecision(
            ObjectReference source,
            List<ObjectReference> legalTargets,
            int minimumSelections,
            int maximumSelections) {
        private PendingDecision {
            legalTargets = List.copyOf(legalTargets);
        }
    }

    private final BiFunction<Long, Map<Long, CardInfo>, ObjectReference> resolver;
    private final Function<ObjectReference, String> displayName;
    private final Map<Long, PendingDecision> pending = new LinkedHashMap<>();

    TargetDecisionTracker(
            BiFunction<Long, Map<Long, CardInfo>, ObjectReference> resolver,
            Function<ObjectReference, String> displayName) {
        this.resolver = resolver;
        this.displayName = displayName;
    }

    void observeRequest(JsonObject greMessage, Map<Long, CardInfo> cards) {
        long messageId = longAt(greMessage, "msgId", -1);
        JsonObject request = objectAt(greMessage, "selectTargetsReq");
        if (messageId < 0 || request.size() == 0) return;

        ObjectReference source = resolver.apply(longAt(request, "sourceId", -1), cards);
        List<ObjectReference> legalTargets = new ArrayList<>();
        int minimumSelections = 0;
        int maximumSelections = 0;

        for (JsonElement groupElement : arrayAt(request, "targets")) {
            if (!groupElement.isJsonObject()) continue;
            JsonObject group = groupElement.getAsJsonObject();
            minimumSelections += Math.max(0, intAt(group, "minTargets", 0));
            maximumSelections += Math.max(0, intAt(group, "maxTargets", 0));
            collectLegalTargets(group, cards, legalTargets);
        }

        if (!legalTargets.isEmpty()) {
            pending.put(messageId, new PendingDecision(
                    source, legalTargets, minimumSelections, maximumSelections));
        }
    }

    Optional<ResolvedDecision> resolveResponse(
            JsonObject payload, Map<Long, CardInfo> cards) {
        PendingDecision decision = pending.remove(longAt(payload, "respId", -1));
        if (decision == null) return Optional.empty();

        List<ObjectReference> selected = new ArrayList<>();
        JsonObject response = objectAt(payload, "selectTargetsResp");
        collectSelectedTargets(objectAt(response, "target"), cards, selected);
        for (JsonElement targetElement : arrayAt(response, "targets")) {
            if (targetElement.isJsonObject()) {
                collectSelectedTargets(
                        targetElement.getAsJsonObject(), cards, selected);
            }
        }

        List<ObjectReference> alternatives = decision.legalTargets().stream()
                .filter(candidate -> !containsReference(selected, candidate))
                .toList();
        String chosen = selected.isEmpty()
                ? "no target"
                : selected.stream().map(displayName)
                        .collect(Collectors.joining(", "));
        String sourceName = decision.source() == null
                ? "Unknown spell or ability"
                : displayName.apply(decision.source());

        DecisionObservation observation = new DecisionObservation(
                DecisionObservation.Kind.TARGET,
                decision.source(),
                selected,
                alternatives,
                decision.minimumSelections(),
                decision.maximumSelections(),
                DecisionObservation.Confidence.EXPLICIT);
        List<ObjectReference> references = new ArrayList<>();
        addReference(references, decision.source());
        selected.forEach(reference -> addReference(references, reference));
        alternatives.forEach(reference -> addReference(references, reference));
        return Optional.of(new ResolvedDecision(
                sourceName + " chooses " + chosen, observation, references));
    }

    void clear() {
        pending.clear();
    }

    private void collectLegalTargets(JsonObject group, Map<Long, CardInfo> cards,
                                     List<ObjectReference> targets) {
        for (JsonElement targetElement : arrayAt(group, "targets")) {
            if (!targetElement.isJsonObject()) continue;
            JsonObject target = targetElement.getAsJsonObject();
            String legalAction = stringAt(target, "legalAction");
            if (!legalAction.isBlank() && !legalAction.contains("Select")) continue;
            addResolvedTarget(target, cards, targets);
        }
    }

    private void collectSelectedTargets(JsonObject target,
                                        Map<Long, CardInfo> cards,
                                        List<ObjectReference> selected) {
        if (target.size() == 0) return;
        for (JsonElement selectedElement : arrayAt(target, "targets")) {
            if (!selectedElement.isJsonObject()) continue;
            addResolvedTarget(selectedElement.getAsJsonObject(), cards, selected);
        }
    }

    private void addResolvedTarget(JsonObject target, Map<Long, CardInfo> cards,
                                   List<ObjectReference> references) {
        long targetId = longAt(target, "targetInstanceId",
                longAt(target, "targetPlayerId", -1));
        ObjectReference reference = resolver.apply(targetId, cards);
        addReference(references, reference);
    }

    private static void addReference(List<ObjectReference> references,
                                     ObjectReference candidate) {
        if (candidate != null && !containsReference(references, candidate)) {
            references.add(candidate);
        }
    }

    private static boolean containsReference(List<ObjectReference> references,
                                             ObjectReference candidate) {
        return references.stream().anyMatch(existing -> sameReference(existing, candidate));
    }

    private static boolean sameReference(ObjectReference left, ObjectReference right) {
        if (left.isPlayer() || right.isPlayer()) {
            return left.playerSeat() != null
                    && left.playerSeat().equals(right.playerSeat());
        }
        if (left.logicalObjectId() > 0 && right.logicalObjectId() > 0) {
            return left.logicalObjectId() == right.logicalObjectId();
        }
        return left.arenaInstanceId() == right.arenaInstanceId();
    }
}
