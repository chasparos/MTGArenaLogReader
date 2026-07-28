package app.testing;

import app.export.MatchAiExporter;
import app.model.card.CardInfo;
import app.model.event.GameEvent;
import app.model.event.ZoneTransitionReason;
import app.model.session.GameModel;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Focused raw-log regression for permanents returned from the battlefield to hand. */
final class FocusedBounceFixtureTest {
    private static final String MATCH_ID = "focused-bounce";

    @Test
    void classifiesPermanentMovedFromBattlefieldToHandAsBounce() throws Exception {
        ArenaLogReplayHarness.ReplayResult replay = new ArenaLogReplayHarness()
                .withCardMetadata(bouncedPermanent())
                .replay(fixture());
        GameModel game = replay.requireGame(MATCH_ID, 1);

        GameEvent transition = game.snapshot().stream()
                .filter(event -> event.getZoneTransition() != null)
                .findFirst()
                .orElseThrow();

        assertEquals("Battlefield", transition.getZoneTransition().fromZone());
        assertEquals("Hand", transition.getZoneTransition().toZone());
        assertEquals(ZoneTransitionReason.RETURNED_TO_HAND,
                transition.getZoneTransition().reason());
        assertEquals("Test Bear is returned to hand", transition.getText());

        String export = new MatchAiExporter().export(replay.requireMatch(MATCH_ID));
        assertTrue(export.lines().anyMatch(line ->
                        line.startsWith("MOVE#")
                                && line.contains(" B>H reason=RETURNED_TO_HAND ")
                                && line.contains("subject=c1#100@5002")),
                export);
        assertTrue(export.contains("text=\"Test Bear is returned to hand\""), export);
    }

    private CardInfo bouncedPermanent() {
        CardInfo card = new CardInfo();
        card.setArenaId(5002L);
        card.setName("Test Bear");
        card.setTypeLine("Creature");
        return card;
    }

    private Path fixture() throws URISyntaxException {
        return Path.of(FocusedBounceFixtureTest.class.getResource(
                "/fixtures/validation/focused/bounce.log").toURI());
    }
}
