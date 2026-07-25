package app.enrichment;

import app.model.card.CardInfo;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CardCacheSetTest {
    @TempDir
    Path temporary;

    @Test
    void completeSetAlsoFeedsPerArenaCardCache() {
        CardInfo card = new CardInfo();
        card.setArenaId(123L);
        card.setName("Cached draft card");
        card.setSet("tst");

        try (CardCache cache = new CardCache(
                new Gson(), temporary.resolve("cards"))) {
            cache.putSet("TST", List.of(card));

            List<CardInfo> set = cache.findSet(
                    "tst", Duration.ofDays(1)).orElseThrow();
            assertEquals(1, set.size());
            assertEquals("Cached draft card", set.getFirst().getName());
            assertEquals("Cached draft card", cache.find(123)
                    .orElseThrow().card().orElseThrow().getName());
        }
    }
}
