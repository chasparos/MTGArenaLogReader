package app.model.session;

import app.model.match.MatchState;
import app.projection.GameEventProjector;
import app.projection.MatchProjector;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns match-lifetime reconstruction knowledge and creates isolated game sessions.
 */
public final class MatchSession {
    private final String matchId;
    private final MatchState matchState;
    private final MatchProjector matchProjector;
    private final Map<Integer, GameSession> games = new LinkedHashMap<>();

    public MatchSession(String matchId) {
        this.matchId = matchId;
        this.matchState = new MatchState(matchId);
        this.matchProjector = new MatchProjector(matchState);
    }

    public synchronized GameSession game(int gameNumber) {
        return games.computeIfAbsent(gameNumber, this::createGame);
    }

    public MatchState matchState() {
        return matchState;
    }

    /**
     * Returns the games currently known for this match in game-number order.
     */
    public synchronized List<GameModel> gameSnapshot() {
        return games.values().stream()
                .map(GameSession::model)
                .toList();
    }

    private GameSession createGame(int gameNumber) {
        GameModel model = new GameModel();
        model.setMatchId(matchId);
        model.setGameNumber(gameNumber);
        return new GameSession(gameNumber, model,
                new GameEventProjector(matchState), matchProjector);
    }
}
