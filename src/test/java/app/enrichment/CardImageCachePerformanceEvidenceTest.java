package app.enrichment;

import app.model.card.CardInfo;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CardImageCachePerformanceEvidenceTest {
    @TempDir Path tempDir;

    @Test
    void recordsDiskThenMemoryReuseWithoutNetwork() throws Exception {
        CardInfo card = new GsonBuilder().create().fromJson("""
                {
                  "id":"dp08-image",
                  "name":"DP08 Image",
                  "image_uris":{"normal":"https://example.invalid/dp08.jpg"}
                }
                """, CardInfo.class);
        BufferedImage image = new BufferedImage(20, 28, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "jpg", tempDir.resolve("dp08-image.jpg").toFile());

        CardImageCache cache = new CardImageCache(tempDir);
        assertTrue(cache.get(card).get(2, TimeUnit.SECONDS).isPresent());
        CardImageCache.Stats afterDisk = cache.stats();
        assertEquals(1, afterDisk.diskHits());
        assertEquals(0, afterDisk.networkRequests());
        assertEquals(1, afterDisk.memoryEntries());

        assertTrue(cache.get(card).get(2, TimeUnit.SECONDS).isPresent());
        CardImageCache.Stats afterMemory = cache.stats();
        assertEquals(1, afterMemory.memoryHits());
        assertEquals(1, afterMemory.diskHits());
        assertEquals(0, afterMemory.networkRequests());
        assertEquals(1, afterMemory.memoryEntries());

        System.out.printf("[DP08 PERF] image-cache diskHits=%d memoryHits=%d networkRequests=%d entries=%d%n",
                afterMemory.diskHits(), afterMemory.memoryHits(),
                afterMemory.networkRequests(), afterMemory.memoryEntries());
    }
}
