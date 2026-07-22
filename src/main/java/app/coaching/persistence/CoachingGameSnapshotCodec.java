package app.coaching.persistence;

import app.model.card.CardInfo;
import app.model.event.GameEvent;
import app.model.session.GameModel;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Serializes the semantic game state needed by the persisted coaching replay.
 *
 * <p>This is deliberately separate from {@link GameModel}: persistence owns
 * the storage shape, while the replay model remains unaware of the database.</p>
 */
public final class CoachingGameSnapshotCodec {
    public static final String SCHEMA = "COACHING_GAME_V1";

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new InstantAdapter())
            .create();

    public String encode(GameModel game) {
        Objects.requireNonNull(game, "game");
        PersistedGame persisted = new PersistedGame(
                SCHEMA,
                game.getMatchId(),
                game.getGameNumber(),
                game.getOpeningHandPlayer(),
                game.getMulliganCount(),
                game.openingHandSnapshot(),
                game.snapshot());
        return gson.toJson(persisted);
    }

    public GameModel decode(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("rich game snapshot is empty");
        }
        PersistedGame persisted = gson.fromJson(json, PersistedGame.class);
        if (persisted == null || !SCHEMA.equals(persisted.schema())) {
            throw new IllegalArgumentException("Unsupported coaching game snapshot schema");
        }

        GameModel game = new GameModel();
        game.setMatchId(persisted.matchId());
        game.setGameNumber(persisted.gameNumber());
        game.setOpeningHand(
                persisted.openingHandPlayer(),
                persisted.mulliganCount(),
                persisted.openingHand() == null ? List.of() : persisted.openingHand());
        game.addEvents(persisted.events() == null ? List.of() : persisted.events());
        return game;
    }

    private record PersistedGame(
            String schema,
            String matchId,
            int gameNumber,
            String openingHandPlayer,
            int mulliganCount,
            List<CardInfo> openingHand,
            List<GameEvent> events) {
    }

    private static final class InstantAdapter extends TypeAdapter<Instant> {
        @Override
        public void write(JsonWriter out, Instant value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        @Override
        public Instant read(JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return Instant.parse(in.nextString());
        }
    }
}
