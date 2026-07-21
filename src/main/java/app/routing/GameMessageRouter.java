package app.routing;

import app.model.game.GameKey;
import app.model.log.LogMessageInterface;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Optional;

/**
 * Maintains the current Arena match/game context while records are consumed in
 * log order. Historical replay and realtime tailing therefore use exactly the
 * same routing logic.
 * <p><strong>Architectural role:</strong> This type belongs to the session-routing boundary that isolates messages and state by match and game.</p>
 */
public final class GameMessageRouter {
    private String currentMatchId;
    private int currentGameNumber = 1;

    public Optional<GameKey> route(LogMessageInterface message) {
        String raw = message.getRawText().strip();
        if (!raw.startsWith("{")) return Optional.empty();

        try {
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            readMatchContext(root);
            readGameContext(root);

            if (currentMatchId == null || currentMatchId.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new GameKey(currentMatchId, currentGameNumber));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private void readMatchContext(JsonObject root) {
        JsonObject roomEvent = object(root, "matchGameRoomStateChangedEvent");
        JsonObject config = object(roomEvent, "gameRoomInfo", "gameRoomConfig");
        String matchId = string(config, "matchId");
        if (!matchId.isBlank() && !matchId.equals(currentMatchId)) {
            currentMatchId = matchId;
            currentGameNumber = 1;
        }
    }

    private void readGameContext(JsonObject root) {
        JsonObject greEvent = object(root, "greToClientEvent");
        JsonElement messages = greEvent.get("greToClientMessages");
        if (messages == null || !messages.isJsonArray()) return;

        for (JsonElement element : messages.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject message = element.getAsJsonObject();
            JsonObject gameInfo = object(message, "gameStateMessage", "gameInfo");
            int gameNumber = integer(gameInfo, "gameNumber", -1);
            if (gameNumber > 0) currentGameNumber = gameNumber;
        }
    }

    private JsonObject object(JsonObject root, String... path) {
        JsonElement current = root;
        for (String key : path) {
            if (current == null || !current.isJsonObject()) return new JsonObject();
            current = current.getAsJsonObject().get(key);
        }
        return current != null && current.isJsonObject()
                ? current.getAsJsonObject() : new JsonObject();
    }

    private String string(JsonObject root, String key) {
        JsonElement value = root.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private int integer(JsonObject root, String key, int fallback) {
        JsonElement value = root.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
    }
}
