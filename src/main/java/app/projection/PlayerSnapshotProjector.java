package app.projection;

import app.model.card.CardInfo;
import app.model.game.BoardPermanentSnapshot;
import app.model.game.GameObjectState;
import app.model.game.GameState;
import app.model.game.PlayerTurnSnapshot;
import app.model.game.ZoneInfo;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static app.projection.ArenaJson.arrayAt;
import static app.projection.ArenaJson.intAt;
import static app.projection.ArenaJson.stringAt;

/**
 * Owns player observations and immutable start-of-turn snapshot construction.
 * Semantic event creation remains in the parent game projector.
 */
final class PlayerSnapshotProjector {
    record TurnSnapshot(
            List<BoardPermanentSnapshot> battlefield,
            List<PlayerTurnSnapshot> players) {
        TurnSnapshot {
            battlefield = List.copyOf(battlefield);
            players = List.copyOf(players);
        }
    }

    interface Context {
        GameState state();
        boolean isCurrent(GameObjectState object);
        String zoneType(int zoneId);
        boolean isAbility(GameObjectState object);
        boolean isRoomFacet(GameObjectState object);
        String objectName(GameObjectState object, Map<Long, CardInfo> cards);
        Long attachedHost(long logicalObjectId);
        List<String> unlockedRoomHalves(
                GameObjectState object, Map<Long, CardInfo> cards);
        String playerName(int seatId);
    }

    private static final Set<String> BATTLE_RELEVANT_ABILITIES = Set.of(
            "deathtouch", "defender", "double strike", "first strike", "flying",
            "haste", "hexproof", "indestructible", "lifelink", "menace",
            "reach", "trample", "vigilance", "ward");

    private final Context context;

    PlayerSnapshotProjector(Context context) {
        this.context = context;
    }

    Optional<String> applyPlayerCounter(int seatId, int counterType, int delta) {
        if (counterType != 3) return Optional.empty();
        GameState state = context.state();
        int previous = state.getPoisonCounters().getOrDefault(seatId, 0);
        int current = Math.max(0, previous + delta);
        state.getPoisonCounters().put(seatId, current);
        if (current == previous) return Optional.empty();
        return Optional.of(poisonChangeText(seatId, current - previous, current));
    }

    List<String> observePlayers(JsonArray players) {
        List<String> changes = new ArrayList<>();
        GameState state = context.state();
        for (JsonElement element : players) {
            if (!element.isJsonObject()) continue;
            JsonObject player = element.getAsJsonObject();
            int seat = intAt(player, "systemSeatNumber",
                    intAt(player, "systemSeatId", -1));
            if (seat < 0) continue;
            if (player.has("lifeTotal")) {
                state.getLifeTotals().put(seat, intAt(player, "lifeTotal", 0));
            }
            Integer poison = poisonCount(player);
            if (poison == null) continue;
            Integer previous = state.getPoisonCounters().put(seat, poison);
            if (previous != null && previous.intValue() != poison.intValue()) {
                changes.add(poisonChangeText(seat, poison - previous, poison));
            }
        }
        return changes;
    }

    TurnSnapshot snapshot(Map<Long, CardInfo> knownCards) {
        List<PlayerTurnSnapshot> players = context.state().getPlayers().entrySet()
                .stream().sorted(Map.Entry.comparingByKey())
                .map(player -> playerSnapshot(player, knownCards))
                .toList();
        return new TurnSnapshot(battlefield(knownCards), players);
    }

    boolean isPostUntapBoundary() {
        GameState state = context.state();
        String phase = state.getPhase() == null ? "" : state.getPhase();
        String step = state.getStep() == null ? "" : state.getStep();
        if (!phase.contains("Beginning")) return true;
        return step.contains("Upkeep") || step.contains("Draw");
    }

    private PlayerTurnSnapshot playerSnapshot(
            Map.Entry<Integer, String> player, Map<Long, CardInfo> knownCards) {
        int seat = player.getKey();
        GameState state = context.state();
        PlayerTurnSnapshot snapshot = new PlayerTurnSnapshot();
        snapshot.setSeatId(seat);
        snapshot.setPlayerName(player.getValue());
        snapshot.setLifeTotal(state.getLifeTotals().get(seat));
        snapshot.setPoisonCounters(state.getPoisonCounters().getOrDefault(seat, 0));
        snapshot.setHandSize(handSize(seat));
        snapshot.getKnownHand().addAll(knownCardsInZone(seat, "Hand"));
        snapshot.getKnownGraveyard().addAll(knownCardsInZone(seat, "Graveyard"));
        snapshot.getKnownExile().addAll(knownCardsInZone(seat, "Exile"));
        return snapshot;
    }

    private List<BoardPermanentSnapshot> battlefield(Map<Long, CardInfo> knownCards) {
        return context.state().getObjects().values().stream()
                .filter(context::isCurrent)
                .filter(object -> "Battlefield".equals(
                        context.zoneType(object.getSemanticZoneId())))
                .filter(object -> !context.isAbility(object))
                .filter(object -> !context.isRoomFacet(object))
                .sorted(Comparator
                        .comparingInt(GameObjectState::getControllerSeatId)
                        .thenComparingLong(GameObjectState::getLogicalObjectId))
                .map(object -> permanentSnapshot(object, knownCards))
                .toList();
    }

    private BoardPermanentSnapshot permanentSnapshot(
            GameObjectState object, Map<Long, CardInfo> knownCards) {
        BoardPermanentSnapshot permanent = new BoardPermanentSnapshot();
        permanent.setLogicalObjectId(object.getLogicalObjectId());
        permanent.setOwnerSeatId(object.getOwnerSeatId());
        permanent.setControllerSeatId(object.getControllerSeatId());
        permanent.setName(context.objectName(object, knownCards));
        permanent.setCard(object.getCard());
        permanent.setTapped(object.getTapped());
        permanent.setPower(object.getPower());
        permanent.setToughness(object.getToughness());
        permanent.setAttachedToLogicalObjectId(
                context.attachedHost(object.getLogicalObjectId()));
        object.getCounters().forEach(counter ->
                permanent.getCounters().add(counter.copy()));
        context.unlockedRoomHalves(object, knownCards)
                .forEach(permanent.getUnlockedRoomHalves()::add);
        observedEvergreenAbilities(object)
                .forEach(permanent.getEvergreenAbilities()::add);
        return permanent;
    }

    private List<String> observedEvergreenAbilities(GameObjectState object) {
        LinkedHashSet<String> abilities = new LinkedHashSet<>();
        if (object.getCard() != null && object.getCard().getKeywords() != null) {
            object.getCard().getKeywords().stream()
                    .map(keyword -> keyword == null
                            ? "" : keyword.trim().toLowerCase(Locale.ROOT))
                    .filter(BATTLE_RELEVANT_ABILITIES::contains)
                    .forEach(abilities::add);
        }
        object.getCounters().stream()
                .map(counter -> counter.getType() == null ? "" : counter.getType())
                .map(type -> type.toLowerCase(Locale.ROOT)
                        .replace("countertype_", "")
                        .replace("counter_", "")
                        .replace(" counter", "")
                        .replace('_', ' ')
                        .trim())
                .filter(type -> BATTLE_RELEVANT_ABILITIES.contains(type)
                        || "shield".equals(type))
                .forEach(abilities::add);
        return List.copyOf(abilities);
    }

    private Integer handSize(int seat) {
        return context.state().getZones().values().stream()
                .filter(zone -> zone.getOwnerSeatId() != null
                        && zone.getOwnerSeatId() == seat)
                .filter(zone -> "Hand".equals(zone.displayName()))
                .map(ZoneInfo::getObjectCount)
                .filter(count -> count >= 0)
                .findFirst().orElse(null);
    }

    private List<CardInfo> knownCardsInZone(int seat, String zoneName) {
        return context.state().getObjects().values().stream()
                .filter(context::isCurrent)
                .filter(object -> object.getOwnerSeatId() == seat)
                .filter(object -> zoneName.equals(
                        context.zoneType(object.getSemanticZoneId())))
                .filter(object -> !context.isAbility(object)
                        && !context.isRoomFacet(object))
                .map(GameObjectState::getCard)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                        CardInfo::getName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    private Integer poisonCount(JsonObject player) {
        for (String key : List.of(
                "poisonCount", "poisonCounter", "poisonCounters")) {
            if (player.has(key) && player.get(key).isJsonPrimitive()) {
                return player.get(key).getAsInt();
            }
        }
        for (JsonElement element : arrayAt(player, "counters")) {
            if (!element.isJsonObject()) continue;
            JsonObject counter = element.getAsJsonObject();
            String type = stringAt(counter, "type");
            if (type.toLowerCase(Locale.ROOT).contains("poison")) {
                return intAt(counter, "count", intAt(counter, "value", 0));
            }
        }
        return null;
    }

    private String poisonChangeText(int seatId, int delta, int total) {
        String verb = delta > 0
                ? "gets " + delta + " poison counter" + (delta == 1 ? "" : "s")
                : "loses " + Math.abs(delta) + " poison counter"
                        + (delta == -1 ? "" : "s");
        return context.playerName(seatId) + " " + verb
                + " (" + total + " total)";
    }
}
