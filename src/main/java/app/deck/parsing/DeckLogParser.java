package app.deck.parsing;


import app.deck.model.CachedDeck;
import app.deck.model.DeckEntry;
import app.model.card.CardInfo;
import app.enrichment.CardCache;
import com.google.gson.*;

import java.time.Instant;
import java.util.*;

/**
 * Represents or implements DeckLogParser in the optional live deck-tracking subsystem.
 *
 * <p>The deck subsystem consumes routed Arena observations alongside cached deck metadata while remaining separate from replay reconstruction.</p>
 *
 * <p>It must not become a second source of canonical game state for the replay pipeline.</p>
 * <p><strong>Architectural role:</strong> This type belongs to deck-specific Arena observation parsing and does not replace the main replay projection pipeline.</p>
 */
final public class DeckLogParser {
    private final Gson gson;
    private final CardCache cardCache;

    public DeckLogParser(Gson gson, CardCache cardCache) {
        this.gson = gson;
        this.cardCache = cardCache;
    }

    public List<CachedDeck> parseDecks(String raw) {
        List<CachedDeck> out = new ArrayList<>();
        JsonElement root = parseRoot(raw);
        if (root == null) return out;
        walk(root, "", out);
        return out;
    }


    /**
     * Parses the local player's complete deck submission made between games.
     *
     * <p>Arena logs this separately from course deck snapshots as
     * {@code ClientMessageType_SubmitDeckResp}. The submitted card arrays are
     * complete game-deck observations and may therefore seed the next game's
     * configuration.</p>
     */
    public List<CachedDeck> parseSubmittedGameDecks(String raw, CachedDeck template) {
        if (template == null) return List.of();

        JsonElement root = parseRoot(raw);
        if (root == null) return List.of();

        List<CachedDeck> out = new ArrayList<>();
        walkSubmittedDecks(root, template, out);
        return List.copyOf(out);
    }

    private void walkSubmittedDecks(JsonElement element, CachedDeck template, List<CachedDeck> out) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if ("ClientMessageType_SubmitDeckResp".equals(string(object, "type"))) {
                JsonObject deck = object(object(object, "submitDeckResp"), "deck");
                if (deck.size() > 0) {
                    out.add(new CachedDeck(
                            template.deckId(),
                            template.name(),
                            template.format(),
                            template.eventName(),
                            Instant.now(),
                            submittedEntries(deck, "deckCards"),
                            submittedEntries(deck, "sideboardCards"),
                            template.commandZone(),
                            template.companions()));
                }
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                walkSubmittedDecks(entry.getValue(), template, out);
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                walkSubmittedDecks(child, template, out);
            }
        }
    }

    private List<DeckEntry> submittedEntries(JsonObject deck, String key) {
        JsonElement value = deck.get(key);
        if (value == null || !value.isJsonArray()) return List.of();

        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (JsonElement element : value.getAsJsonArray()) {
            if (!element.isJsonPrimitive()) continue;
            try {
                long arenaId = element.getAsLong();
                if (arenaId > 0) quantities.merge(arenaId, 1, Integer::sum);
            } catch (RuntimeException ignored) {
                // Ignore malformed card identifiers without discarding the submission.
            }
        }

        List<DeckEntry> entries = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            CardInfo card = cardCache.find(entry.getKey())
                    .flatMap(CardCache.CachedCard::card)
                    .orElse(null);
            entries.add(new DeckEntry(entry.getKey(), entry.getValue(), card));
        }
        return List.copyOf(entries);
    }

    private JsonObject object(JsonObject parent, String key) {
        if (parent == null) return new JsonObject();
        JsonElement value = parent.get(key);
        return value != null && value.isJsonObject()
                ? value.getAsJsonObject()
                : new JsonObject();
    }

    private void walk(JsonElement element, String eventName, List<CachedDeck> out) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonObject()) {
            JsonObject o = element.getAsJsonObject();
            String nextEvent = firstString(o, "InternalEventName", "EventName");
            if (nextEvent.isBlank()) nextEvent = eventName;

            if (o.has("CourseDeckSummary") && o.has("CourseDeck")) {
                addDeck(o.getAsJsonObject("CourseDeckSummary"), o.getAsJsonObject("CourseDeck"), nextEvent, out);
            }
            if (o.has("Summary") && o.has("Deck") && o.get("Summary").isJsonObject() && o.get("Deck").isJsonObject()) {
                addDeck(o.getAsJsonObject("Summary"), o.getAsJsonObject("Deck"), nextEvent, out);
            }
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                if ("request".equals(e.getKey()) && e.getValue().isJsonPrimitive()) {
                    JsonElement nested = parseJsonString(e.getValue().getAsString());
                    if (nested != null) walk(nested, nextEvent, out);
                } else {
                    walk(e.getValue(), nextEvent, out);
                }
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) walk(child, eventName, out);
        }
    }

    private void addDeck(JsonObject summary, JsonObject deck, String eventName, List<CachedDeck> out) {
        String id = string(summary, "DeckId");
        if (id.isBlank()) return;
        String name = string(summary, "Name");
        String format = attribute(summary, "Format");
        out.add(new CachedDeck(id, name, format, eventName, Instant.now(),
                entries(deck, "MainDeck"), entries(deck, "Sideboard"),
                entries(deck, "CommandZone"), entries(deck, "Companions")));
    }

    private List<DeckEntry> entries(JsonObject deck, String key) {
        JsonElement value = deck.get(key);
        if (value == null || !value.isJsonArray()) return List.of();
        List<DeckEntry> out = new ArrayList<>();
        for (JsonElement e : value.getAsJsonArray()) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            long id = longValue(o, "cardId", 0);
            int quantity = (int) longValue(o, "quantity", 0);
            if (id <= 0 || quantity <= 0) continue;
            CardInfo card = cardCache.find(id).flatMap(CardCache.CachedCard::card).orElse(null);
            out.add(new DeckEntry(id, quantity, card));
        }
        return List.copyOf(out);
    }

    private JsonElement parseRoot(String raw) {
        if (raw == null) return null;
        int first = raw.indexOf('{');
        if (first < 0) return null;
        try { return JsonParser.parseString(raw.substring(first)); }
        catch (RuntimeException ignored) { return null; }
    }

    private JsonElement parseJsonString(String text) {
        try { return JsonParser.parseString(text); }
        catch (RuntimeException ignored) { return null; }
    }

    private String attribute(JsonObject summary, String wanted) {
        JsonElement attrs = summary.get("Attributes");
        if (attrs == null || !attrs.isJsonArray()) return "";
        for (JsonElement e : attrs.getAsJsonArray()) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            if (wanted.equalsIgnoreCase(string(o, "name"))) return string(o, "value").replace("\"", "");
        }
        return "";
    }

    private String firstString(JsonObject o, String... keys) {
        for (String key : keys) {
            String value = string(o, key);
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    private long longValue(JsonObject o, String key, long fallback) {
        try {
            JsonElement e = o.get(key);
            return e != null && e.isJsonPrimitive() ? e.getAsLong() : fallback;
        } catch (RuntimeException ex) { return fallback; }
    }
}
