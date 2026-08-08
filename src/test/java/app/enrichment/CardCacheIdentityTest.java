package app.enrichment;

import app.model.card.CardInfo;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CardCacheIdentityTest {
    @TempDir Path temporary;

    @Test void resolvesCachedCardByLogicalOracleIdentity() {
        CardInfo card = new CardInfo();
        card.setArenaId(17L);
        card.setId("printing");
        card.setOracleId("oracle-key");
        card.setName("Off-format card");
        try (CardCache cache = new CardCache(new Gson(), temporary.resolve("cards"))) {
            cache.put(17L, Optional.of(card));
            assertEquals("Off-format card",
                    cache.findByCatalogIdentity("oracle:oracle-key").orElseThrow().getName());
        }
    }
}
