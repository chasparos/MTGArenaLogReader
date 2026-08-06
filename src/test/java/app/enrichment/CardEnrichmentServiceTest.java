package app.enrichment;

import app.model.card.CardInfo;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CardEnrichmentServiceTest {
    @TempDir Path temporary;

    @Test
    void catalogCardFeedsSharedArenaCache() {
        Gson gson = new Gson();
        CardInfo card = new CardInfo();
        card.setId("scryfall-id");
        card.setArenaId(321L);
        card.setName("Catalog card");
        try (ScryfallClient client = new ScryfallClient(gson);
             CardCache cache = new CardCache(gson, temporary.resolve("cards"))) {
            CardEnrichmentService enrichment = new CardEnrichmentService(
                    client, cache, Duration.ZERO);
            enrichment.acceptCatalogCard(card);

            assertEquals("Catalog card", cache.find(321L).orElseThrow()
                    .card().orElseThrow().getName());
            assertEquals("Catalog card", enrichment.resolveArenaCard(321L)
                    .orElseThrow().getName());
        }
    }
}
