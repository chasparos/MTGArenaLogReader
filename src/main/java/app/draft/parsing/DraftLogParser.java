package app.draft.parsing;

import app.draft.model.DraftCardCount;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses only Arena draft observations. It does not infer draft strategy.
 */
public final class DraftLogParser {
    public Optional<DraftLogEvent> parse(String rawText) {
        if (rawText == null || rawText.isBlank()) return Optional.empty();
        try {
            if (rawText.contains("Draft.Notify")) return parseNotify(rawText);
            if (rawText.contains("EventPlayerDraftMakePick") && rawText.contains("\"request\"")) {
                return parsePick(rawText);
            }
            if (rawText.contains("EventSetDeckV3") && rawText.contains("\"request\"")) {
                return parseDeck(rawText);
            }
        } catch (RuntimeException ignored) {
            // Draft records are optional observations; malformed lines must not stop ingestion.
        }
        return Optional.empty();
    }

    private Optional<DraftLogEvent> parseNotify(String text) {
        JsonObject root = objectAfterMarker(text, "Draft.Notify");
        String draftId = string(root, "draftId");
        int pack = integer(root, "SelfPack");
        int pick = integer(root, "SelfPick");
        String cards = string(root, "PackCards");
        if (draftId.isBlank() || pack <= 0 || pick <= 0 || cards.isBlank()) return Optional.empty();
        return Optional.of(new DraftLogEvent.PackOffered(draftId, pack, pick, commaSeparatedIds(cards)));
    }

    private Optional<DraftLogEvent> parsePick(String text) {
        JsonObject envelope = objectAfterMarker(text, "EventPlayerDraftMakePick");
        JsonObject request = JsonParser.parseString(string(envelope, "request")).getAsJsonObject();
        String draftId = string(request, "DraftId");
        int pack = integer(request, "Pack");
        int pick = integer(request, "Pick");
        List<Long> ids = idArray(request.getAsJsonArray("GrpIds"));
        if (draftId.isBlank() || pack <= 0 || pick <= 0 || ids.isEmpty()) return Optional.empty();
        return Optional.of(new DraftLogEvent.PickMade(draftId, pack, pick, ids));
    }

    private Optional<DraftLogEvent> parseDeck(String text) {
        JsonObject envelope = objectAfterMarker(text, "EventSetDeckV3");
        JsonObject request = JsonParser.parseString(string(envelope, "request")).getAsJsonObject();
        JsonObject deck = request.getAsJsonObject("Deck");
        if (deck == null) return Optional.empty();
        return Optional.of(new DraftLogEvent.DeckSubmitted(
                cardCounts(deck.getAsJsonArray("MainDeck")),
                cardCounts(deck.getAsJsonArray("Sideboard"))));
    }

    private JsonObject objectAfterMarker(String text, String marker) {
        int markerIndex = text.indexOf(marker);
        int objectStart = text.indexOf('{', markerIndex + marker.length());
        if (markerIndex < 0 || objectStart < 0) throw new IllegalArgumentException("Missing JSON object");
        return JsonParser.parseString(text.substring(objectStart).strip()).getAsJsonObject();
    }

    private List<Long> commaSeparatedIds(String text) {
        List<Long> result = new ArrayList<>();
        for (String part : text.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) result.add(Long.parseLong(trimmed));
        }
        return result;
    }

    private List<Long> idArray(JsonArray array) {
        if (array == null) return List.of();
        List<Long> result = new ArrayList<>();
        for (JsonElement element : array) {
            long id = element.getAsLong();
            if (id > 0) result.add(id);
        }
        return result;
    }

    private List<DraftCardCount> cardCounts(JsonArray array) {
        if (array == null) return List.of();
        List<DraftCardCount> result = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject entry = element.getAsJsonObject();
            long id = entry.get("cardId").getAsLong();
            int quantity = entry.get("quantity").getAsInt();
            if (id > 0 && quantity > 0) result.add(new DraftCardCount(id, quantity));
        }
        return result;
    }

    private String string(JsonObject object, String member) {
        JsonElement value = object.get(member);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private int integer(JsonObject object, String member) {
        JsonElement value = object.get(member);
        return value == null || value.isJsonNull() ? 0 : value.getAsInt();
    }
}
