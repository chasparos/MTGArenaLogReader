package app.testing;

import app.export.MatchAiExporter;
import app.model.card.CardInfo;
import app.model.event.GameEvent;
import app.model.game.BoardPermanentSnapshot;
import app.model.session.GameModel;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Focused raw-log regression for copied permanents represented as token objects. */
final class FocusedCopyFixtureTest {
    private static final String MATCH_ID = "focused-copy";

    @Test
    void preservesOriginalAndCopiedPermanentAsDistinctBattlefieldObjects() throws Exception {
        ArenaLogReplayHarness.ReplayResult replay = new ArenaLogReplayHarness()
                .withCardMetadata(copiedPermanent())
                .replay(fixture());
        GameModel game = replay.requireGame(MATCH_ID, 1);

        GameEvent snapshotEvent = game.snapshot().stream()
                .filter(event -> event.getTurnSnapshot() != null)
                .findFirst()
                .orElseThrow();
        List<BoardPermanentSnapshot> permanents =
                snapshotEvent.getBattlefieldObservation();

        assertEquals(2, permanents.size());
        assertEquals(List.of("Test Double", "Test Double"),
                permanents.stream().map(BoardPermanentSnapshot::getName).toList());
        assertEquals(2, permanents.stream()
                .map(BoardPermanentSnapshot::getLogicalObjectId)
                .distinct()
                .count());

        String export = new MatchAiExporter().export(replay.requireMatch(MATCH_ID));
        assertTrue(export.lines().anyMatch(line ->
                        line.startsWith("S#")
                                && line.contains("board=c1#100[2/2];c1#101[2/2]")),
                export);
    }

    private CardInfo copiedPermanent() {
        CardInfo card = new CardInfo();
        card.setArenaId(5004L);
        card.setName("Test Double");
        card.setTypeLine("Creature");
        card.setPower("2");
        card.setToughness("2");
        return card;
    }

    private Path fixture() throws URISyntaxException {
        return Path.of(FocusedCopyFixtureTest.class.getResource(
                "/fixtures/validation/focused/copy.log").toURI());
    }
}
