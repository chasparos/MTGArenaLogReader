package app.model.session;

import app.model.event.GameEvent;
import app.model.log.LogMessageInterface;
import app.model.log.ModelObject;
import app.projection.GameEventProjector;
import app.projection.MatchProjector;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Couples one per-game model with its per-game projector and the owning match projection.
 */
public final class GameSession {
    private static final Logger LOG = LoggerFactory.getLogger(GameSession.class);
    private final int gameNumber;
    private final GameModel model;
    private final GameEventProjector projector;
    private final MatchProjector matchProjector;

    public GameSession(int gameNumber, GameModel model,
                       GameEventProjector projector, MatchProjector matchProjector) {
        this.gameNumber = gameNumber;
        this.model = model;
        this.projector = projector;
        this.matchProjector = matchProjector;
    }

    public GameModel model() {
        return model;
    }

    public GameEventProjector projector() {
        return projector;
    }

    public List<GameEvent> project(LogMessageInterface message, ModelObject modelObject) {
        List<GameEvent> gameEvents = projector.project(message, modelObject);
        List<GameEvent> events = matchProjector.project(gameNumber, gameEvents);
        if (!events.isEmpty()) {
            LOG.info("Projected {} event(s) for match {} game {} from log sequence {}",
                    events.size(), model.getMatchId(), gameNumber, message.getSequence());
        }
        return events;
    }
}
