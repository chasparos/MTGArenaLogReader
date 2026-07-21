package app.deck.parsing;

import app.deck.model.CachedDeck;
import app.deck.model.DeckEntry;
import app.enrichment.CardCache;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeckLogParserTest {
    @TempDir
    Path tempDir;

    @Test
    void parsesBetweenGameSubmitDeckResponseAsCompleteConfiguration() {
        Gson gson = new Gson();
        try (CardCache cache = new CardCache(gson, tempDir.resolve("cards"))) {
            DeckLogParser parser = new DeckLogParser(gson, cache);
            CachedDeck template = new CachedDeck(
                    "deck-1", "Selected", "Standard", "Constructed_BestOf3",
                    Instant.parse("2026-01-01T00:00:00Z"),
                    List.of(new DeckEntry(100, 4, null)),
                    List.of(new DeckEntry(200, 2, null)),
                    List.of(), List.of());

            String raw = """
                    {
                      "payload": {
                        "type": "ClientMessageType_SubmitDeckResp",
                        "submitDeckResp": {
                          "deck": {
                            "deckCards": [100, 100, 100, 200],
                            "sideboardCards": [100, 200]
                          }
                        }
                      }
                    }
                    """;

            CachedDeck submitted = parser.parseSubmittedGameDecks(raw, template)
                    .stream()
                    .findFirst()
                    .orElseThrow();

            assertEquals("deck-1", submitted.deckId());
            assertEquals(List.of(
                    new DeckEntry(100, 3, null),
                    new DeckEntry(200, 1, null)), submitted.mainDeck());
            assertEquals(List.of(
                    new DeckEntry(100, 1, null),
                    new DeckEntry(200, 1, null)), submitted.sideboard());
        }
    }

    @Test
    void ignoresUnrelatedPayloads() {
        Gson gson = new Gson();
        try (CardCache cache = new CardCache(gson, tempDir.resolve("cards-empty"))) {
            DeckLogParser parser = new DeckLogParser(gson, cache);
            CachedDeck template = new CachedDeck(
                    "deck-1", "Selected", "Standard", "Constructed_BestOf3",
                    Instant.parse("2026-01-01T00:00:00Z"),
                    List.of(), List.of(), List.of(), List.of());

            assertEquals(List.of(), parser.parseSubmittedGameDecks(
                    "{\"payload\":{\"type\":\"ClientMessageType_EnterSideboardingReq\"}}",
                    template));
        }
    }
}
