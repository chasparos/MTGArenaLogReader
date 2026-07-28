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

/** Focused raw-log regression for countered spells that are exiled instead of buried. */
final class FocusedExileOnCounterFixtureTest {
    private static final String MATCH_ID = "focused-exile-on-counter";

    @Test
    void classifiesCounteredSpellMovedFromStackToExile() throws Exception {
        ArenaLogReplayHarness.ReplayResult replay = new ArenaLogReplayHarness()
                .withCardMetadata(counteredSpell())
                .replay(fixture());
        GameModel game = replay.requireGame(MATCH_ID, 1);

        GameEvent transition = game.snapshot().stream()
                .filter(event -> event.getZoneTransition() != null)
                .findFirst()
                .orElseThrow();

        assertEquals("Stack", transition.getZoneTransition().fromZone());
        assertEquals("Exile", transition.getZoneTransition().toZone());
        assertEquals(ZoneTransitionReason.COUNTERED,
                transition.getZoneTransition().reason());
        assertEquals("Test Insight is countered and exiled", transition.getText());

        String export = new MatchAiExporter().export(replay.requireMatch(MATCH_ID));
        assertTrue(export.contains(
                "MOVE#1 S>X reason=COUNTERED provenance=ARENA_CATEGORY confidence=EXPLICIT subject=c1#100@5001"),
                export);
        assertTrue(export.contains("text=\"Test Insight is countered and exiled\""), export);
    }

    private CardInfo counteredSpell() {
        CardInfo card = new CardInfo();
        card.setArenaId(5001L);
        card.setName("Test Insight");
        card.setTypeLine("Instant");
        return card;
    }

    private Path fixture() throws URISyntaxException {
        return Path.of(FocusedExileOnCounterFixtureTest.class.getResource(
                "/fixtures/validation/focused/exile-on-counter.log").toURI());
    }
}
