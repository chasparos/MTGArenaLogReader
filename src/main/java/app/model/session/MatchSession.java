package app.model.session;

import app.model.match.MatchState;
import app.projection.AbilityNameStore;
import app.projection.GameEventProjector;
import app.projection.MatchProjector;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Owns match-lifetime reconstruction knowledge and creates isolated game sessions.
 */
public final class MatchSession {
    private final String matchId;
    private final MatchState matchState;
    private final AbilityNameStore abilityNames;
    private final MatchProjector matchProjector;
    private final Map<Integer, GameSession> games = new LinkedHashMap<>();

    public MatchSession(String matchId, AbilityNameStore abilityNames) {
        this.matchId = matchId;
        this.matchState = new MatchState(matchId);
        this.abilityNames = abilityNames;
        this.matchProjector = new MatchProjector(matchState);
    }

    public synchronized GameSession game(int gameNumber) {
        return games.computeIfAbsent(gameNumber, this::createGame);
    }

    public MatchState matchState() {
        return matchState;
    }

    private GameSession createGame(int gameNumber) {
        GameModel model = new GameModel();
        model.setMatchId(matchId);
        model.setGameNumber(gameNumber);
        return new GameSession(gameNumber, model,
                new GameEventProjector(abilityNames, matchState), matchProjector);
    }
}
