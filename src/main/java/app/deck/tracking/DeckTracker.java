package app.deck.tracking;


import app.deck.model.CachedDeck;
import app.deck.model.DeckEntry;
import app.deck.model.DeckGameState;
import app.deck.parsing.DeckLogParser;
import app.deck.persistence.DeckCache;
import app.model.card.CardInfo;
import app.model.log.LogMessageInterface;
import app.enrichment.CardCache;
import app.enrichment.ScryfallClient;
import com.google.gson.*;

import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * Represents or implements DeckTracker in the optional live deck-tracking subsystem.
 *
 * <p>The deck subsystem consumes routed Arena observations alongside cached deck metadata while remaining separate from replay reconstruction.</p>
 *
 * <p>It must not become a second source of canonical game state for the replay pipeline.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the live deck-tracking subsystem, which consumes observations independently of replay reconstruction.</p>
 */
public final class DeckTracker {
    private final DeckCache deckCache;
    private final DeckLogParser deckParser;
    private final DeckTrackerListener listener;
    private final CardCache cardCache;
    private final ScryfallClient scryfallClient;
    private final ExecutorService restExecutor;

    private String selectedDeckId;
    private String selectedEventName;
    private String currentMatchId;
    private int currentGameNumber = 1;
    private int localSeat = 1;
    private CachedDeck currentDeck;
    private boolean started;
    private boolean complete;

    private final Map<Integer,String> zoneTypes = new HashMap<>();
    private final Map<Integer,Integer> zoneOwners = new HashMap<>();
    private final Map<Integer,Long> objectCards = new HashMap<>();
    private final Map<Integer,Integer> objectZones = new HashMap<>();

    public DeckTracker(Gson gson, CardCache cardCache, DeckCache deckCache,
                       ScryfallClient scryfallClient, ExecutorService restExecutor,
                       DeckTrackerListener listener) {
        this.deckCache = deckCache;
        this.cardCache = cardCache;
        this.scryfallClient = scryfallClient;
        this.restExecutor = restExecutor;
        this.deckParser = new DeckLogParser(gson, cardCache);
        this.listener = listener;
    }

    public void accept(LogMessageInterface message) {
        String raw = message.getRawText();
        for (CachedDeck deck : deckParser.parseDecks(raw)) {
            deckCache.put(deck);
            selectedDeckId = deck.deckId();
            if (deck.eventName() != null && !deck.eventName().isBlank()) selectedEventName = deck.eventName();
        }

        JsonObject root = root(raw);
        if (root == null) return;
        readRoom(root);
        readGre(root);
    }

    private void readRoom(JsonObject root) {
        JsonObject room = object(root, "matchGameRoomStateChangedEvent", "gameRoomInfo");
        JsonObject config = object(room, "gameRoomConfig");
        String matchId = string(config, "matchId");
        if (matchId.isBlank()) return;

        if (!matchId.equals(currentMatchId)) {
            currentMatchId = matchId;
            currentGameNumber = 1;
            complete = false;
            started = false;
            clearGameObjects();

            String eventId = "";
            JsonElement players = config.get("reservedPlayers");
            if (players != null && players.isJsonArray()) {
                for (JsonElement e : players.getAsJsonArray()) {
                    if (!e.isJsonObject()) continue;
                    JsonObject p = e.getAsJsonObject();
                    int seat = integer(p, "systemSeatId", -1);
                    if (seat == 1) {
                        localSeat = seat;
                        eventId = string(p, "eventId");
                    }
                }
            }
            final String finalEventId = eventId;

            currentDeck = deckCache.find(selectedDeckId)
                    .or(() -> deckCache.mostRecentForEvent(!finalEventId.isBlank() ? finalEventId : selectedEventName))
                    .orElse(null);
            enrichCurrentDeckAsync();
        }
    }

    private void readGre(JsonObject root) {
        JsonObject gre = object(root, "greToClientEvent");
        JsonElement messages = gre.get("greToClientMessages");
        if (messages == null || !messages.isJsonArray()) return;

        for (JsonElement e : messages.getAsJsonArray()) {
            if (!e.isJsonObject()) continue;
            JsonObject m = e.getAsJsonObject();

            JsonElement seats = m.get("systemSeatIds");
            if (seats != null && seats.isJsonArray() && seats.getAsJsonArray().size() > 0) {
                localSeat = seats.getAsJsonArray().get(0).getAsInt();
            }

            JsonObject gsm = object(m, "gameStateMessage");
            if (gsm.size() == 0) continue;

            JsonObject info = object(gsm, "gameInfo");
            int gameNo = integer(info, "gameNumber", currentGameNumber);
            if (gameNo != currentGameNumber) {
                currentGameNumber = gameNo;
                complete = false;
                started = false;
                clearGameObjects();
            }

            String stage = string(info, "stage");
            if (stage.contains("GameOver") || stage.contains("Complete")) {
                complete = true;
                if (started) listener.gameCompleted(currentMatchId, currentGameNumber);
                continue;
            }

            readZones(gsm);
            readObjects(gsm);

            if (!started && currentDeck != null && currentMatchId != null) {
                started = true;
                listener.gameStarted(snapshot());
            } else if (started && !complete) {
                listener.gameUpdated(snapshot());
            }
        }
    }

    private void readZones(JsonObject gsm) {
        JsonElement zones = gsm.get("zones");
        if (zones == null || !zones.isJsonArray()) return;
        for (JsonElement e : zones.getAsJsonArray()) {
            if (!e.isJsonObject()) continue;
            JsonObject z = e.getAsJsonObject();
            int id = integer(z, "zoneId", -1);
            if (id < 0) continue;
            String type = string(z, "type");
            int owner = integer(z, "ownerSeatId", integer(z, "visibility", -1));
            if (!type.isBlank()) zoneTypes.put(id, type);
            if (owner >= 0) zoneOwners.put(id, owner);
            JsonElement ids = z.get("objectInstanceIds");
            if (ids != null && ids.isJsonArray()) {
                Set<Integer> present = new HashSet<>();
                for (JsonElement i : ids.getAsJsonArray()) {
                    int objectId = i.getAsInt();
                    present.add(objectId);
                    objectZones.put(objectId, id);
                }
                objectZones.entrySet().removeIf(en -> en.getValue() == id && !present.contains(en.getKey()));
            }
        }
    }

    private void readObjects(JsonObject gsm) {
        JsonElement objects = gsm.get("gameObjects");
        if (objects == null || !objects.isJsonArray()) return;
        for (JsonElement e : objects.getAsJsonArray()) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            int instance = integer(o, "instanceId", -1);
            long grp = longValue(o, "grpId", 0);
            int zone = integer(o, "zoneId", -1);
            if (instance < 0) continue;
            if (grp > 0) objectCards.put(instance, grp);
            if (zone >= 0) objectZones.put(instance, zone);
        }
    }

    private DeckGameState snapshot() {
        int library = 0, graveyard = 0, exile = 0;
        Map<Long,Integer> outside = new HashMap<>();

        for (Map.Entry<Integer,Integer> e : objectZones.entrySet()) {
            int zoneId = e.getValue();
            if (zoneOwners.getOrDefault(zoneId, localSeat) != localSeat) continue;
            String type = zoneTypes.getOrDefault(zoneId, "");
            if (type.contains("Library")) library++;
            else {
                if (type.contains("Graveyard")) graveyard++;
                if (type.contains("Exile")) exile++;
                Long card = objectCards.get(e.getKey());
                if (card != null && card > 0) outside.merge(card, 1, Integer::sum);
            }
        }

        if (library == 0 && currentDeck != null) {
            int knownOutside = outside.values().stream().mapToInt(Integer::intValue).sum();
            library = Math.max(0, currentDeck.mainDeckSize() - knownOutside);
        }

        return new DeckGameState(currentMatchId, currentGameNumber, currentDeck,
                library, graveyard, exile, outside, complete);
    }

    private void enrichCurrentDeckAsync() {
        CachedDeck deck = currentDeck;
        if (deck == null) return;
        boolean missing = deck.mainDeck().stream().anyMatch(e -> e.card() == null);
        if (!missing) return;

        restExecutor.submit(() -> {
            List<DeckEntry> enriched = new ArrayList<>();
            for (DeckEntry entry : deck.mainDeck()) {
                app.model.card.CardInfo card = entry.card();
                if (card == null) {
                    card = cardCache.find(entry.arenaId())
                            .flatMap(CardCache.CachedCard::card)
                            .orElseGet(() -> {
                                try {
                                    Optional<app.model.card.CardInfo> found = scryfallClient.findByArenaId(entry.arenaId());
                                    cardCache.put(entry.arenaId(), found);
                                    Thread.sleep(110);
                                    return found.orElse(null);
                                } catch (Exception error) {
                                    return null;
                                }
                            });
                }
                enriched.add(new DeckEntry(entry.arenaId(), entry.quantity(), card));
            }
            CachedDeck replacement = new CachedDeck(deck.deckId(), deck.name(), deck.format(), deck.eventName(),
                    java.time.Instant.now(), List.copyOf(enriched), deck.sideboard(), deck.commandZone(), deck.companions());
            deckCache.put(replacement);
            if (currentDeck != null && replacement.deckId().equals(currentDeck.deckId())) {
                currentDeck = replacement;
                if (started && !complete) listener.gameUpdated(snapshot());
            }
        });
    }

    private void clearGameObjects() {
        zoneTypes.clear(); zoneOwners.clear(); objectCards.clear(); objectZones.clear();
    }

    private JsonObject root(String raw) {
        int first = raw == null ? -1 : raw.indexOf('{');
        if (first < 0) return null;
        try {
            JsonElement e = JsonParser.parseString(raw.substring(first));
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (RuntimeException ignored) { return null; }
    }

    private JsonObject object(JsonObject root, String... path) {
        JsonElement current = root;
        for (String key : path) {
            if (current == null || !current.isJsonObject()) return new JsonObject();
            current = current.getAsJsonObject().get(key);
        }
        return current != null && current.isJsonObject() ? current.getAsJsonObject() : new JsonObject();
    }

    private String string(JsonObject o, String key) {
        JsonElement e=o.get(key); return e!=null && e.isJsonPrimitive() ? e.getAsString() : "";
    }
    private int integer(JsonObject o,String key,int fallback) {
        try { JsonElement e=o.get(key); return e!=null&&e.isJsonPrimitive()?e.getAsInt():fallback; }
        catch(RuntimeException ex){ return fallback; }
    }
    private long longValue(JsonObject o,String key,long fallback) {
        try { JsonElement e=o.get(key); return e!=null&&e.isJsonPrimitive()?e.getAsLong():fallback; }
        catch(RuntimeException ex){ return fallback; }
    }
}
