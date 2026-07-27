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
        GameState state = context.state();
        String localPlayer = state.getLocalPlayerName();
        int openingHandSeat = state.getOpeningHandSeat();
        List<PlayerTurnSnapshot> players = state.getPlayers().entrySet().stream()
                .sorted(Comparator
                        .comparingInt((Map.Entry<Integer, String> player) ->
                                isLocalPlayer(player, localPlayer, openingHandSeat) ? 0 : 1)
                        .thenComparingInt(Map.Entry::getKey))
                .map(player -> playerSnapshot(player, knownCards))
                .toList();
        return new TurnSnapshot(battlefield(knownCards), players);
    }

    private boolean isLocalPlayer(Map.Entry<Integer, String> player,
                                  String localPlayer, int openingHandSeat) {
        if (openingHandSeat >= 0) return player.getKey() == openingHandSeat;
        return localPlayer != null && localPlayer.equals(player.getValue());
    }

    boolean isFirstMainBoundary() {
        GameState state = context.state();
        String phase = state.getPhase() == null ? "" : state.getPhase();
        return phase.contains("Main1") || phase.contains("PrecombatMain");
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
        permanent.setPower(effectivePower(object));
        permanent.setToughness(effectiveToughness(object));
        permanent.setSagaChapter(sagaChapter(object));
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

    private Integer effectivePower(GameObjectState object) {
        return adjustedStat(object, observedOrPrintedStat(object, object.getPower(), true));
    }

    private Integer effectiveToughness(GameObjectState object) {
        return adjustedStat(object, observedOrPrintedStat(object, object.getToughness(), false));
    }

    private Integer observedOrPrintedStat(GameObjectState object, Integer observed, boolean power) {
        if (observed != null) return observed;
        CardInfo card = object.getCard();
        if (card == null) return null;
        String type = card.effectiveTypeLine() == null ? "" : card.effectiveTypeLine();
        if (!type.contains("Creature") && !type.contains("Vehicle")) return null;
        String printed = power ? card.getPower() : card.getToughness();
        try {
            return printed == null ? null : Integer.valueOf(printed);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer sagaChapter(GameObjectState object) {
        CardInfo card = object.getCard();
        String type = card == null || card.effectiveTypeLine() == null
                ? "" : card.effectiveTypeLine();
        if (!type.contains("Saga")) return null;
        return object.getCounters().stream()
                .filter(counter -> counter.getArenaType() == 108
                        || "Lore".equalsIgnoreCase(counter.getType()))
                .map(app.model.game.CounterState::getCount)
                .filter(count -> count > 0)
                .findFirst().orElse(null);
    }

    private Integer adjustedStat(GameObjectState object, Integer base) {
        if (base == null) return null;
        int result = base;
        for (app.model.game.CounterState counter : object.getCounters()) {
            String type = counter.getType() == null ? "" : counter.getType();
            if (type.contains("+1/+1")) result += counter.getCount();
            else if (type.contains("-1/-1") || type.contains("−1/−1")) {
                result -= counter.getCount();
            }
        }
        return result;
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
        ZoneInfo authoritative = context.state().getZones().values().stream()
                .filter(zone -> zone.getOwnerSeatId() != null
                        && zone.getOwnerSeatId() == seat)
                .filter(zone -> zoneName.equals(zone.displayName()))
                .filter(ZoneInfo::isObjectInstancesKnown)
                .findFirst().orElse(null);
        Set<Long> authoritativeIds = authoritative == null
                ? Set.of() : authoritative.getObjectInstanceIds();

        List<CardInfo> cards = context.state().getObjects().values().stream()
                .filter(context::isCurrent)
                .filter(object -> object.getOwnerSeatId() == seat)
                .filter(object -> zoneName.equals(
                        context.zoneType(object.getSemanticZoneId())))
                .filter(object -> authoritative == null
                        || authoritativeIds.contains(object.getInstanceId()))
                .filter(object -> !context.isAbility(object)
                        && !context.isRoomFacet(object))
                .map(GameObjectState::getCard)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                        CardInfo::getName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        if (authoritative != null && authoritative.getObjectCount() >= 0
                && cards.size() > authoritative.getObjectCount()) {
            return cards.subList(0, authoritative.getObjectCount());
        }
        return cards;
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
