package app.projection;

import app.model.card.CardInfo;
import app.model.event.GameEvent;
import app.model.event.GameEventType;
import app.model.game.GameObjectState;
import app.model.game.GameState;
import app.model.game.PermanentDamage;
import app.model.game.PlayerLifeChange;
import app.model.log.LogMessageInterface;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import static app.projection.ArenaJson.*;

/**
 * Projects strategically relevant damage and life changes.
 *
 * <p>Player damage is correlated from Arena's explicit DamageDealt annotation
 * and the resulting player life snapshot. Positive life deltas and unmatched
 * negative deltas are still preserved as life gain or non-damage life loss.</p>
 *
 * <p>Permanent damage is intentionally emitted only for planeswalkers.
 * Nonlethal creature damage is too granular for the replay's coaching purpose;
 * lethal creature outcomes remain represented by existing zone events.</p>
 */
final class DamageProjector {
    private final GameState state;
    private final ObjectIdentityTracker identities;
    private final Function<Integer, String> playerName;
    private final BiFunction<GameObjectState, Map<Long, CardInfo>, String> objectDisplayName;
    private final BiFunction<LogMessageInterface, String, GameEvent> eventFactory;
    private final Predicate<JsonObject> annotationMarker;

    DamageProjector(
            GameState state,
            ObjectIdentityTracker identities,
            Function<Integer, String> playerName,
            BiFunction<GameObjectState, Map<Long, CardInfo>, String> objectDisplayName,
            BiFunction<LogMessageInterface, String, GameEvent> eventFactory,
            Predicate<JsonObject> annotationMarker) {
        this.state = state;
        this.identities = identities;
        this.playerName = playerName;
        this.objectDisplayName = objectDisplayName;
        this.eventFactory = eventFactory;
        this.annotationMarker = annotationMarker;
    }

    void project(
            LogMessageInterface source,
            Map<Integer, Integer> previousLifeTotals,
            JsonArray annotations,
            Map<Long, CardInfo> cards,
            List<GameEvent> result) {
        Map<Integer, DamageSummary> damageByPlayer = new LinkedHashMap<>();
        List<DamageObservation> observations = new ArrayList<>();

        for (JsonElement element : annotations) {
            if (!element.isJsonObject()) continue;
            JsonObject annotation = element.getAsJsonObject();
            if (!hasType(annotation, "AnnotationType_DamageDealt")
                    || !annotationMarker.test(annotation)) {
                continue;
            }

            int amount = Math.max(0, (int) detailLong(annotation, "damage", 0));
            if (amount == 0) continue;

            long sourceId = longAt(annotation, "affectorId", -1);
            String sourceName = displayName(sourceId, cards);
            for (long affectedId : longArray(annotation, "affectedIds")) {
                if (state.getPlayers().containsKey((int) affectedId)) {
                    damageByPlayer.computeIfAbsent((int) affectedId, ignored -> new DamageSummary())
                            .add(amount, sourceId >= 0 ? sourceId : null, sourceName);
                } else {
                    GameObjectState target = identities.findIncludingAliases(affectedId);
                    if (isPlaneswalker(target)) {
                        observations.add(new DamageObservation(
                                target,
                                amount,
                                sourceId >= 0 ? sourceId : null,
                                sourceName));
                    }
                }
            }
        }

        projectPlayerLifeChanges(source, previousLifeTotals, damageByPlayer, result);
        for (DamageObservation observation : observations) {
            GameObjectState target = observation.target();
            String targetName = objectDisplayName.apply(target, cards);
            GameEvent event = eventFactory.apply(source,
                    observation.sourceName() + " deals " + observation.amount()
                            + " damage to " + targetName);
            event.setType(GameEventType.PLANESWALKER_DAMAGE);
            event.setPermanentDamage(new PermanentDamage(
                    target.getLogicalObjectId(),
                    targetName,
                    observation.amount(),
                    observation.sourceInstanceId(),
                    observation.sourceName()));
            result.add(event);
        }
    }

    private void projectPlayerLifeChanges(
            LogMessageInterface source,
            Map<Integer, Integer> previousLifeTotals,
            Map<Integer, DamageSummary> damageByPlayer,
            List<GameEvent> result) {
        for (Map.Entry<Integer, Integer> entry : state.getLifeTotals().entrySet()) {
            int seatId = entry.getKey();
            Integer previous = previousLifeTotals.get(seatId);
            if (previous == null) continue;

            int current = entry.getValue();
            DamageSummary damage = damageByPlayer.get(seatId);
            int afterDamage = previous;
            if (damage != null && damage.amount() > 0) {
                afterDamage = previous - damage.amount();
                addPlayerEvent(source, result, PlayerLifeChange.Kind.DAMAGE,
                        seatId, damage.amount(), previous, afterDamage,
                        damage.singleSourceId(), damage.singleSourceName());
            }

            int residual = current - afterDamage;
            if (residual > 0) {
                addPlayerEvent(source, result, PlayerLifeChange.Kind.LIFE_GAIN,
                        seatId, residual, afterDamage, current, null, null);
            } else if (residual < 0) {
                addPlayerEvent(source, result, PlayerLifeChange.Kind.LIFE_LOSS,
                        seatId, -residual, afterDamage, current, null, null);
            }
        }
    }

    private void addPlayerEvent(
            LogMessageInterface source,
            List<GameEvent> result,
            PlayerLifeChange.Kind kind,
            int seatId,
            int amount,
            int previous,
            int current,
            Long sourceInstanceId,
            String sourceName) {
        String name = playerName.apply(seatId);
        String text = switch (kind) {
            case DAMAGE -> name + " takes " + amount + " damage"
                    + (sourceName == null ? "" : " from " + sourceName)
                    + " (" + current + " life)";
            case LIFE_GAIN -> name + " gains " + amount + " life (" + current + " life)";
            case LIFE_LOSS -> name + " loses " + amount + " life (" + current + " life)";
        };
        GameEvent event = eventFactory.apply(source, text);
        event.setType(GameEventType.PLAYER_LIFE_CHANGE);
        event.setPlayerLifeChange(new PlayerLifeChange(
                kind, seatId, name, amount, previous, current, sourceInstanceId, sourceName));
        result.add(event);
    }

    private String displayName(long instanceId, Map<Long, CardInfo> cards) {
        if (instanceId < 0) return "An effect";
        GameObjectState object = identities.findIncludingAliases(instanceId);
        return object == null ? "Object " + instanceId : objectDisplayName.apply(object, cards);
    }

    private boolean isPlaneswalker(GameObjectState object) {
        return object != null && object.getCardTypes().stream()
                .anyMatch(type -> "Planeswalker".equalsIgnoreCase(type)
                        || type.endsWith("_Planeswalker"));
    }

    private static final class DamageSummary {
        private int amount;
        private Long singleSourceId;
        private String singleSourceName;
        private boolean multipleSources;

        void add(int observedAmount, Long sourceId, String sourceName) {
            amount += observedAmount;
            if (singleSourceName == null) {
                singleSourceId = sourceId;
                singleSourceName = sourceName;
            } else if (!java.util.Objects.equals(singleSourceId, sourceId)) {
                multipleSources = true;
            }
        }

        int amount() {
            return amount;
        }

        Long singleSourceId() {
            return multipleSources ? null : singleSourceId;
        }

        String singleSourceName() {
            return multipleSources ? null : singleSourceName;
        }
    }

    private record DamageObservation(
            GameObjectState target,
            int amount,
            Long sourceInstanceId,
            String sourceName) {
    }
}
