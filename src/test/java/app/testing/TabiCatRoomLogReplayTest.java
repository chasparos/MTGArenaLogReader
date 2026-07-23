package app.testing;

import app.model.event.GameEvent;
import app.model.game.BoardPermanentSnapshot;
import app.model.session.GameModel;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TabiCatRoomLogReplayTest {
    private static final String MATCH_ID = "202d2b4e-725b-4dc9-b663-dbc51bb60ab4";

    @Test
    void roomFacetsDoNotBecomeIndependentPermanentsOrEvents() throws Exception {
        GameModel game = new ArenaLogReplayHarness()
                .replay(fixture("logs/tabi-cat-room.log"))
                .requireGame(MATCH_ID, 1);

        List<String> eventText = game.snapshot().stream()
                .map(GameEvent::getText)
                .toList();

        assertFalse(eventText.stream().anyMatch(text ->
                        text.contains("Arena #92137")),
                "The right Room facet must not be projected as an independent object");

        List<BoardPermanentSnapshot> battlefield = game.snapshot().stream()
                .flatMap(event -> event.getTurnSnapshot().stream())
                .flatMap(player -> player.getBattlefield().stream())
                .toList();

        assertFalse(battlefield.stream().anyMatch(permanent ->
                        permanent.getName() != null
                                && permanent.getName().contains("Arena #92137")),
                "The right Room facet must not appear as an independent battlefield permanent");

        assertTrue(eventText.stream().anyMatch(text ->
                        text.contains("Arena #92135") || text.contains("Arena #92136")),
                "The parent Room observation should still be preserved");
    }

    private Path fixture(String resource) throws URISyntaxException {
        return Path.of(getClass().getClassLoader().getResource(resource).toURI());
    }
}
