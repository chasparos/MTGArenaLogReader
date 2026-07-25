package app.log;


import app.model.log.LogMessage;
import app.model.log.LogMessageInterface;
import app.model.log.RawLogEntry;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Converts a framed Arena log record into the application's normalized log-message model.
 *
 * <p>This is the synchronous decoding boundary between structural log framing and
 * asynchronous enrichment. It classifies records, creates display summaries, and
 * discovers referenced Arena card identifiers without interpreting game semantics.</p>
 *
 * <p>Thread management, card enrichment, game routing, and canonical state mutation
 * belong to later pipeline stages.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the Player.log ingestion boundary before enrichment, routing, and canonical game-state projection.</p>
 */
public final class LogMessageParser {
    private final Gson gson;

    public LogMessageParser(Gson gson) {
        this.gson = Objects.requireNonNull(gson, "gson");
    }

    public LogMessageInterface parse(RawLogEntry raw) {
        Objects.requireNonNull(raw, "raw");

        LogMessage message = new LogMessage();
        message.setSequence(raw.getSequence());
        message.setTimestamp(raw.getTimestamp());
        message.setRawText(raw.getText());
        message.setCategory(classify(raw.getText()));
        message.setDisplayText(summarize(raw.getText()));
        message.setReferencedCardIds(extractCardIds(raw.getText()));
        return message;
    }

    private String classify(String line) {
        if (line.contains("DETAILED LOGS")) return "SYSTEM";
        if (line.contains("Draft.Notify")
                || line.contains("EventPlayerDraftMakePick")
                || line.contains("EventSetDeckV3")) return "DRAFT";
        if (line.contains("ResultReason_") || line.contains("MatchState_GameComplete")) return "RESULT";
        if (line.contains("Connecting to matchId") || line.contains("matchGameRoomStateChangedEvent")) return "MATCH";
        if (line.contains("GameStateMessage") || line.contains("greToClientEvent")) return "GAME";
        return "RAW";
    }

    private String summarize(String line) {
        String trimmed = line.strip();
        if (!trimmed.startsWith("{")) return trimmed;

        try {
            JsonObject root = JsonParser.parseString(trimmed).getAsJsonObject();
            if (root.has("matchGameRoomStateChangedEvent")) {
                JsonObject event = root.getAsJsonObject("matchGameRoomStateChangedEvent");
                String state = stringAt(event, "stateType");
                String matchId = stringAt(event, "gameRoomInfo", "gameRoomConfig", "matchId");
                return "Match state=" + state + (matchId.isBlank() ? "" : " matchId=" + matchId);
            }
            if (root.has("greToClientEvent")) {
                JsonArray messages = root.getAsJsonObject("greToClientEvent")
                        .getAsJsonArray("greToClientMessages");
                if (messages != null && !messages.isEmpty()) {
                    return "GRE " + stringAt(messages.get(0).getAsJsonObject(), "type");
                }
                return "GRE event";
            }
            return gson.toJson(root);
        } catch (RuntimeException malformed) {
            return trimmed;
        }
    }

    private Set<Long> extractCardIds(String line) {
        Set<Long> result = new LinkedHashSet<>();
        String trimmed = line.stripLeading();
        if (trimmed.startsWith("{")) {
            try {
                collectGrpIds(JsonParser.parseString(trimmed), result);
            } catch (RuntimeException ignored) {
                // Malformed records remain available as raw messages.
            }
        }
        collectDraftIds(line, result);
        return result;
    }

    private void collectDraftIds(String line, Set<Long> target) {
        try {
            if (line.contains("Draft.Notify")) {
                int marker = line.indexOf("Draft.Notify");
                int start = line.indexOf('{', marker);
                JsonObject object = JsonParser.parseString(line.substring(start).strip()).getAsJsonObject();
                String cards = stringAt(object, "PackCards");
                for (String part : cards.split(",")) {
                    if (!part.isBlank()) target.add(Long.parseLong(part.trim()));
                }
                return;
            }
            if (!line.contains("\"request\"")) return;
            if (!line.contains("EventPlayerDraftMakePick") && !line.contains("EventSetDeckV3")) return;

            int marker = line.indexOf("Event");
            int start = line.indexOf('{', marker);
            JsonObject envelope = JsonParser.parseString(line.substring(start).strip()).getAsJsonObject();
            JsonObject request = JsonParser.parseString(stringAt(envelope, "request")).getAsJsonObject();
            collectDraftRequestIds(request, target);
        } catch (RuntimeException ignored) {
            // Draft enrichment is best effort and must not block the observation.
        }
    }

    private void collectDraftRequestIds(JsonElement element, Set<Long> target) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) collectDraftRequestIds(child, target);
            return;
        }
        if (!element.isJsonObject()) return;

        JsonObject object = element.getAsJsonObject();
        if (object.has("cardId") && object.get("cardId").isJsonPrimitive()) {
            long id = object.get("cardId").getAsLong();
            if (id > 0) target.add(id);
        }
        if (object.has("GrpIds") && object.get("GrpIds").isJsonArray()) {
            for (JsonElement child : object.getAsJsonArray("GrpIds")) {
                long id = child.getAsLong();
                if (id > 0) target.add(id);
            }
        }
        for (var entry : object.entrySet()) collectDraftRequestIds(entry.getValue(), target);
    }

    private void collectGrpIds(JsonElement element, Set<Long> target) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) collectGrpIds(child, target);
            return;
        }
        if (!element.isJsonObject()) return;

        JsonObject object = element.getAsJsonObject();
        String objectType = stringAt(object, "type");
        if (object.has("grpId") && object.has("instanceId")
                && !"GameObjectType_Ability".equals(objectType)) {
            JsonElement value = object.get("grpId");
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                long id = value.getAsLong();
                if (id > 0) target.add(id);
            }
        }
        if (object.has("objectSourceGrpId")) {
            JsonElement value = object.get("objectSourceGrpId");
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                long id = value.getAsLong();
                if (id > 0) target.add(id);
            }
        }
        for (var entry : object.entrySet()) collectGrpIds(entry.getValue(), target);
    }

    private String stringAt(JsonObject root, String... path) {
        JsonElement current = root;
        for (String key : path) {
            if (current == null || !current.isJsonObject()) return "";
            current = current.getAsJsonObject().get(key);
        }
        return current == null || current.isJsonNull() ? "" : current.getAsString();
    }
}
