package app.deckplanner.collection;

import app.model.log.RawLogEntry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ArenaCollectionLogParserTest {
    private final ArenaCollectionLogParser parser = new ArenaCollectionLogParser();

    @Test
    void parsesCompleteBareNumericMapWithProvenance() throws IOException {
        Instant observed = Instant.parse("2026-08-06T12:00:00Z");
        ArenaCollectionSnapshot snapshot = parser.parseComplete(new RawLogEntry(
                42, observed, fixture("player-cards-v3-complete.json"))).orElseThrow();
        assertEquals(4, snapshot.ownedCopies().get(1001L));
        assertEquals(3, snapshot.ownedCopies().size());
        assertEquals(42, snapshot.sourceSequence());
        assertEquals(observed, snapshot.observedAt());
        assertEquals(ArenaCollectionSnapshot.Source.BARE_NUMERIC_CARD_MAP, snapshot.source());
    }

    @Test
    void parsesExplicitLegacyResponse() {
        String line = "<== PlayerInventory.GetPlayerCardsV3(13): {\"1001\":4}";
        ArenaCollectionSnapshot snapshot = parser.parseComplete(new RawLogEntry(
                7, Instant.EPOCH, line)).orElseThrow();
        assertEquals(ArenaCollectionSnapshot.Source.PLAYER_INVENTORY_GET_PLAYER_CARDS_V3,
                snapshot.source());
    }

    @Test
    void rejectsEmptyIdleInventoryDeckAndMixedMaps() throws IOException {
        assertTrue(parser.parseComplete(raw("{}")).isEmpty());
        assertTrue(parser.parseComplete(raw(fixture("inventory-not-collection.json"))).isEmpty());
        assertTrue(parser.parseComplete(raw(fixture("deck-not-collection.json"))).isEmpty());
        assertTrue(parser.parseComplete(raw("{\"1001\":4,\"name\":2}")).isEmpty());
        assertTrue(parser.parseComplete(raw("{\"1001\":0}")).isEmpty());
        assertTrue(parser.parseComplete(raw("{\"1001\":1.5}")).isEmpty());
    }

    private RawLogEntry raw(String text) {
        return new RawLogEntry(1, Instant.EPOCH, text);
    }

    private String fixture(String name) throws IOException {
        try (var input = getClass().getResourceAsStream("/fixtures/collection/" + name)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
