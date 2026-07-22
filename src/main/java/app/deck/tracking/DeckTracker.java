package app.deck.tracking;


import app.deck.model.CachedDeck;
import app.deck.model.DeckEntry;
import app.deck.model.DeckGameState;
import app.deck.model.MatchDeckState;
import app.deck.model.SideboardChange;
import app.deck.parsing.DeckLogParser;
import app.deck.persistence.DeckCache;
import app.model.card.CardInfo;
import app.model.log.LogMessageInterface;
import app.enrichment.CardCache;
import app.enrichment.ScryfallClient;
import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOG = LoggerFactory.getLogger(DeckTracker.class);
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
    private MatchDeckState matchDeckState;
    private CachedDeck currentDeck;
    private CachedDeck pendingGameDeck;
    private final Map<String, Map<Integer, DeckGameState>> gameStates = new LinkedHashMap<>();
    private boolean started;
    private boolean complete;
    private final List<String> matchLog = new ArrayList<>();

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

    public synchronized String matchLogText() {
        return matchLog.isEmpty()
                ? "No deck-level match messages have been observed."
                : String.join(System.lineSeparator(), matchLog);
    }

    public synchronized DeckGameState currentState() {
        return currentMatchId == null || currentDeck == null ? null : snapshot();
    }

    public synchronized DeckGameState stateForGame(String matchId, int gameNumber) {
        Map<Integer, DeckGameState> matchStates = gameStates.get(matchId);
        return matchStates == null ? null : matchStates.get(gameNumber);
    }

    public void accept(LogMessageInterface message) {
        String raw = message.getRawText();
        for (CachedDeck deck : deckParser.parseDecks(raw)) {
            deckCache.put(deck);
            if (currentMatchId != null && complete && matchDeckState != null
                    && matchDeckState.selectedDeck() != null
                    && Objects.equals(matchDeckState.selectedDeck().deckId(), deck.deckId())) {
                pendingGameDeck = deck;
                LOG.info("Observed complete deck snapshot between games: match={}, deck={}, main={}, sideboard={}",
                        currentMatchId, deck.deckId(), deck.mainDeckSize(), deck.sideboard().size());
            } else {
                selectedDeckId = deck.deckId();
            }
            if (deck.eventName() != null && !deck.eventName().isBlank()) selectedEventName = deck.eventName();
        }

        CachedDeck submissionBaseline = matchDeckState == null
                ? deckCache.find(selectedDeckId).orElse(null)
                : matchDeckState.selectedDeck();
        if (submissionBaseline != null) {
            for (CachedDeck submitted : deckParser.parseSubmittedGameDecks(raw, submissionBaseline)) {
                pendingGameDeck = submitted;
                int targetGame = currentMatchId == null ? 1 : currentGameNumber + 1;
                LOG.info("Observed submitted game deck: match={}, game={}, main={}, sideboard={}",
                        currentMatchId == null ? "<pending>" : currentMatchId,
                        targetGame, submitted.mainDeckSize(), submitted.sideboard().size());
                if (currentMatchId != null) {
                    recordMatchMessage("Submitted deck for Game " + targetGame
                            + ": " + submitted.mainDeckSize() + " main, "
                            + submitted.sideboard().stream().mapToInt(DeckEntry::quantity).sum() + " sideboard");
                }
            }
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
            matchLog.clear();
            recordMatchMessage("Match " + matchId + " started");
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

            CachedDeck selectedDeck = deckCache.find(selectedDeckId)
                    .or(() -> deckCache.mostRecentForEvent(!finalEventId.isBlank() ? finalEventId : selectedEventName))
                    .orElse(null);
            matchDeckState = new MatchDeckState(matchId, selectedDeck);
            LOG.info("Started deck tracking for match {} with selected deck {}", matchId,
                    selectedDeck == null ? "<unknown>" : selectedDeck.deckId());
            if (pendingGameDeck != null) {
                matchDeckState.observeDeckForGame(1, pendingGameDeck);
                recordMatchMessage("Submitted deck for Game 1: " + pendingGameDeck.mainDeckSize()
                        + " main, " + pendingGameDeck.sideboard().stream()
                        .mapToInt(DeckEntry::quantity).sum() + " sideboard");
                pendingGameDeck = null;
            }
            currentDeck = matchDeckState.deckForGame(currentGameNumber);
            rememberCurrentState();
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
                if (matchDeckState != null && pendingGameDeck != null) {
                    Optional<SideboardChange> change =
                            matchDeckState.observeDeckForGame(gameNo, pendingGameDeck);
                    currentDeck = matchDeckState.deckForGame(gameNo);
                    pendingGameDeck = null;
                    rememberCurrentState();
                    change.ifPresent(sideboardChange -> {
                        LOG.info("Reconstructed sideboard change for match {} game {}: {} in, {} out ({})",
                                sideboardChange.matchId(), sideboardChange.gameNumber(),
                                sideboardChange.broughtIn().stream().mapToInt(DeckEntry::quantity).sum(),
                                sideboardChange.removed().stream().mapToInt(DeckEntry::quantity).sum(),
                                sideboardChange.confidence());
                        recordMatchMessage("Sideboard change for Game " + sideboardChange.gameNumber()
                                + ": " + formatEntries(sideboardChange.broughtIn()) + " in; "
                                + formatEntries(sideboardChange.removed()) + " out ["
                                + sideboardChange.confidence() + "]");
                        listener.sideboardChanged(sideboardChange);
                    });
                } else {
                    LOG.info("Activating game {} deck without a submitted configuration: match={}", gameNo, currentMatchId);
                    currentDeck = matchDeckState == null ? null : matchDeckState.deckForGame(gameNo);
                    rememberCurrentState();
                }
                complete = false;
                started = false;
                clearGameObjects();
            }

            String stage = string(info, "stage");
            if (stage.contains("GameOver") || stage.contains("Complete")) {
                complete = true;
                rememberCurrentState();
                if (started) listener.gameCompleted(currentMatchId, currentGameNumber);
                continue;
            }

            readZones(gsm);
            readObjects(gsm);

            if (!started && currentDeck != null && currentMatchId != null) {
                started = true;
                DeckGameState state = snapshot();
                remember(state);
                listener.gameStarted(state);
            } else if (started && !complete) {
                DeckGameState state = snapshot();
                remember(state);
                listener.gameUpdated(state);
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
            if (matchDeckState != null) {
                matchDeckState.refreshSelectedDeck(replacement);
                currentDeck = matchDeckState.deckForGame(currentGameNumber);
                rememberCurrentState();
                if (started && !complete) listener.gameUpdated(snapshot());
            }
        });
    }

    private synchronized void rememberCurrentState() {
        if (currentMatchId != null && currentDeck != null) remember(snapshot());
    }

    private synchronized void remember(DeckGameState state) {
        gameStates.computeIfAbsent(state.matchId(), ignored -> new LinkedHashMap<>())
                .put(state.gameNumber(), state);
    }

    private synchronized void recordMatchMessage(String message) {
        matchLog.add(message);
    }

    private String formatEntries(List<DeckEntry> entries) {
        if (entries == null || entries.isEmpty()) return "none";
        return entries.stream()
                .map(entry -> entry.quantity() + "x " + entry.displayName())
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
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
