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

        List<String> roomFacetArenaIds = List.of(
                "92136", "92137",
                "92132", "92133",
                "92334", "92335");

        for (String arenaId : roomFacetArenaIds) {
            assertFalse(eventText.stream().anyMatch(text ->
                            text.contains("Arena #" + arenaId)),
                    "Room facet Arena #" + arenaId
                            + " must not be projected as an independent object");
        }

        List<BoardPermanentSnapshot> battlefield = game.snapshot().stream()
                .flatMap(event -> event.getTurnSnapshot().stream())
                .flatMap(player -> player.getBattlefield().stream())
                .toList();

        for (String arenaId : roomFacetArenaIds) {
            assertFalse(battlefield.stream().anyMatch(permanent ->
                            permanent.getName() != null
                                    && permanent.getName().contains("Arena #" + arenaId)),
                    "Room facet Arena #" + arenaId
                            + " must not appear as an independent battlefield permanent");
        }

        assertTrue(eventText.stream().anyMatch(text ->
                        text.contains("casts Mirror Room")),
                "The cast event should identify the selected Room half");
        assertTrue(eventText.stream().anyMatch(text ->
                        text.contains("casts Meat Locker")),
                "Each Room cast should identify its selected half");
        assertTrue(eventText.stream().anyMatch(text ->
                        text.contains("casts Misty Salon")),
                "The right Room half should be identified when that half was cast");

        assertTrue(battlefield.stream().anyMatch(permanent ->
                        "Mirror Room // Fractured Realm".equals(permanent.getName())
                                && permanent.getUnlockedRoomHalves().contains("Mirror Room")),
                "The parent Room permanent should retain its unlocked half");
        assertTrue(battlefield.stream().anyMatch(permanent ->
                        "Meat Locker // Drowned Diner".equals(permanent.getName())
                                && permanent.getUnlockedRoomHalves().contains("Meat Locker")),
                "Unlocked state belongs to the parent Room permanent");
        assertTrue(battlefield.stream().anyMatch(permanent ->
                        "Smoky Lounge // Misty Salon".equals(permanent.getName())
                                && permanent.getUnlockedRoomHalves().contains("Misty Salon")),
                "The board snapshot should reflect a right-half unlock");
    }


    @Test
    void sourceEntersBattlefieldBeforeItsEtbAbilityIsProjected() throws Exception {
        List<GameEvent> events = new ArenaLogReplayHarness()
                .replay(fixture("logs/tabi-cat-room.log"))
                .requireGame(MATCH_ID, 1)
                .snapshot();

        int firstDeathcapEntry = indexOf(events,
                "Deathcap Marionette resolves and enters the battlefield");
        int firstDeathcapAbility = indexOf(events,
                "triggers ability of Deathcap Marionette");

        assertTrue(firstDeathcapEntry >= 0, "Expected Deathcap Marionette battlefield entry");
        assertTrue(firstDeathcapAbility >= 0, "Expected Deathcap Marionette ETB ability");
        assertTrue(firstDeathcapEntry < firstDeathcapAbility,
                "A permanent must enter the battlefield before its ETB ability is put on the stack");
    }

    private int indexOf(List<GameEvent> events, String text) {
        for (int i = 0; i < events.size(); i++) {
            String eventText = events.get(i).getText();
            if (eventText != null && eventText.contains(text)) return i;
        }
        return -1;
    }

    private Path fixture(String resource) throws URISyntaxException {
        return Path.of(getClass().getClassLoader().getResource(resource).toURI());
    }
}
