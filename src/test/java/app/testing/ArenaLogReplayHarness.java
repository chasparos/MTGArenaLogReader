package app.testing;

import app.model.game.GameKey;
import app.model.session.GameModel;
import app.model.InformationBundle;
import app.model.log.LogMessageInterface;
import app.model.log.RawLogEntry;
import app.projection.GameEventProjector;
import app.routing.GameMessageRouter;
import app.log.LogMessageParser;
import app.log.LogRecordFramer;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Replays a finite Arena log through the production framing, decoding, routing,
 * state-projection, and per-game model stages without starting Swing or network services.
 *
 * <p>This test fixture is the end-to-end boundary for deterministic regression tests.
 * Card enrichment is deliberately completed with an empty bundle so tests never depend
 * on Scryfall availability or a developer's persistent cache.</p>
 */
public final class ArenaLogReplayHarness {
    private LogRecordFramer framer = new LogRecordFramer();
    private final LogMessageParser parser =
            new LogMessageParser(new GsonBuilder().disableHtmlEscaping().create());
    private GameMessageRouter router = new GameMessageRouter();
    private final Map<GameKey, Session> sessions = new LinkedHashMap<>();
    private long sequence;

    public ReplayResult replay(Path logPath) throws IOException {
        reset();
        try (BufferedReader reader = Files.newBufferedReader(logPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                for (String record : framer.accept(line)) acceptRecord(record);
            }
        }
        return snapshot();
    }

    public ReplayResult replayLines(List<String> lines) {
        reset();
        for (String line : lines) {
            for (String record : framer.accept(line)) acceptRecord(record);
        }
        return snapshot();
    }

    private void reset() {
        framer = new LogRecordFramer();
        router = new GameMessageRouter();
        sessions.clear();
        sequence = 0;
    }

    private void acceptRecord(String record) {
        LogMessageInterface message = parser.parse(
                new RawLogEntry(++sequence, Instant.EPOCH.plusMillis(sequence), record));
        message.getModelFuture().complete(new InformationBundle());

        router.route(message).ifPresent(key -> {
            Session session = sessions.computeIfAbsent(key, ignored -> createSession(key));
            session.model().addRawRecord(message.getRawText());
            session.model().addEvents(
                    session.projector().project(message, message.getModelFuture().join()));
            session.model().setOpeningHand(
                    session.projector().openingHandPlayer(),
                    session.projector().mulliganCount(),
                    session.projector().openingHand());
        });
    }

    private Session createSession(GameKey key) {
        GameModel model = new GameModel();
        model.setMatchId(key.getMatchId());
        model.setGameNumber(key.getGameNumber());
        return new Session(model, new GameEventProjector());
    }

    private ReplayResult snapshot() {
        Map<GameKey, GameModel> games = new LinkedHashMap<>();
        sessions.forEach((key, session) -> games.put(key, session.model()));
        return new ReplayResult(Collections.unmodifiableMap(games));
    }

    private record Session(GameModel model, GameEventProjector projector) {}

    public record ReplayResult(Map<GameKey, GameModel> games) {
        public GameModel requireGame(String matchId, int gameNumber) {
            GameModel game = games.get(new GameKey(matchId, gameNumber));
            if (game == null) {
                throw new AssertionError("Missing game " + matchId + " #" + gameNumber
                        + "; available=" + games.keySet());
            }
            return game;
        }
    }
}
