package app.testing;

import app.export.MatchAiExporter;
import app.model.card.CardInfo;
import app.model.event.GameEvent;
import app.model.game.BoardPermanentSnapshot;
import app.model.game.CounterState;
import app.model.session.GameModel;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Focused raw-log regression for permanent counter reconstruction and export. */
final class FocusedCounterFixtureTest {
    private static final String MATCH_ID = "focused-counter";

    @Test
    void projectsPermanentCounterTransactionIntoTurnStateAndExport() throws Exception {
        ArenaLogReplayHarness.ReplayResult replay = new ArenaLogReplayHarness()
                .withCardMetadata(counterCreature())
                .replay(fixture());
        GameModel game = replay.requireGame(MATCH_ID, 1);

        BoardPermanentSnapshot permanent = game.snapshot().stream()
                .filter(event -> !event.getTurnSnapshot().isEmpty())
                .reduce((first, second) -> second)
                .map(GameEvent::getBattlefieldObservation)
                .orElseThrow()
                .stream()
                .filter(candidate -> candidate.getLogicalObjectId() == 100L)
                .findFirst()
                .orElseThrow();

        assertEquals(1, permanent.getCounters().size());
        CounterState counter = permanent.getCounters().getFirst();
        assertEquals(1, counter.getArenaType());
        assertEquals("+1/+1", counter.getType());
        assertEquals(2, counter.getCount());

        String export = new MatchAiExporter().export(replay.requireMatch(MATCH_ID));
        assertTrue(export.contains("Test Initiate#100[+1/+1=2]"), export);
    }

    private CardInfo counterCreature() {
        CardInfo card = new CardInfo();
        card.setArenaId(5000L);
        card.setName("Test Initiate");
        card.setTypeLine("Creature — Human");
        return card;
    }

    private Path fixture() throws URISyntaxException {
        return Path.of(FocusedCounterFixtureTest.class.getResource(
                "/fixtures/validation/focused/counter.log").toURI());
    }
}
