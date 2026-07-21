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
