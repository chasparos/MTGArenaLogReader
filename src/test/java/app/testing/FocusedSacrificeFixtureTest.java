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

/** Focused raw-log regression for sacrificed permanents. */
final class FocusedSacrificeFixtureTest {
    private static final String MATCH_ID = "focused-sacrifice";

    @Test
    void classifiesSacrificedPermanentMovedFromBattlefieldToGraveyard() throws Exception {
        ArenaLogReplayHarness.ReplayResult replay = new ArenaLogReplayHarness()
                .withCardMetadata(sacrificedPermanent())
                .replay(fixture());
        GameModel game = replay.requireGame(MATCH_ID, 1);

        GameEvent transition = game.snapshot().stream()
                .filter(event -> event.getZoneTransition() != null)
                .findFirst()
                .orElseThrow();

        assertEquals("Battlefield", transition.getZoneTransition().fromZone());
        assertEquals("Graveyard", transition.getZoneTransition().toZone());
        assertEquals(ZoneTransitionReason.SACRIFICED,
                transition.getZoneTransition().reason());
        assertEquals("Test Offering is sacrificed", transition.getText());

        String export = new MatchAiExporter().export(replay.requireMatch(MATCH_ID));
        assertTrue(export.lines().anyMatch(line ->
                        line.startsWith("MOVE#")
                                && line.contains(" B>G reason=SACRIFICED ")
                                && line.contains("subject=c1#100@5003")),
                export);
        assertTrue(export.contains("text=\"Test Offering is sacrificed\""), export);
    }

    private CardInfo sacrificedPermanent() {
        CardInfo card = new CardInfo();
        card.setArenaId(5003L);
        card.setName("Test Offering");
        card.setTypeLine("Creature");
        return card;
    }

    private Path fixture() throws URISyntaxException {
        return Path.of(FocusedSacrificeFixtureTest.class.getResource(
                "/fixtures/validation/focused/sacrifice.log").toURI());
    }
}
