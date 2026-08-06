package app.deckplanner.collection;

import app.model.log.RawLogEntry;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Strict recognizer for complete GetPlayerCardsV3-style collection maps. */
public final class ArenaCollectionLogParser {
    private static final String EXPLICIT_MARKER = "PlayerInventory.GetPlayerCardsV3";

    public Optional<ArenaCollectionSnapshot> parseComplete(RawLogEntry raw) {
        if (raw == null || raw.getText() == null) return Optional.empty();
        String text = raw.getText().strip();
        ArenaCollectionSnapshot.Source source;
        String json;
        if (text.contains(EXPLICIT_MARKER)) {
            int start = text.indexOf('{', text.indexOf(EXPLICIT_MARKER));
            if (start < 0) return Optional.empty();
            json = text.substring(start);
            source = ArenaCollectionSnapshot.Source.PLAYER_INVENTORY_GET_PLAYER_CARDS_V3;
        } else if (text.startsWith("{")) {
            json = text;
            source = ArenaCollectionSnapshot.Source.BARE_NUMERIC_CARD_MAP;
        } else {
            return Optional.empty();
        }

        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) return Optional.empty();
            JsonObject object = parsed.getAsJsonObject();
            if (object.isEmpty()) return Optional.empty();
            Map<Long, Integer> copies = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                long arenaId = Long.parseLong(entry.getKey());
                JsonElement value = entry.getValue();
                if (arenaId <= 0 || !value.isJsonPrimitive()
                        || !value.getAsJsonPrimitive().isNumber()) return Optional.empty();
                double numeric = value.getAsDouble();
                int quantity = value.getAsInt();
                if (numeric != quantity || quantity <= 0) return Optional.empty();
                copies.put(arenaId, quantity);
            }
            return Optional.of(new ArenaCollectionSnapshot(
                    copies, raw.getTimestamp(), raw.getSequence(), source));
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
    }
}
