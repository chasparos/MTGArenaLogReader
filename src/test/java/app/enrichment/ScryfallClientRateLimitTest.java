package app.enrichment;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScryfallClientRateLimitTest {
    @Test
    void retryAfterSecondsOverridesExponentialFallback() {
        assertEquals(Duration.ofSeconds(3),
                ScryfallClient.retryDelay("3", 0, Instant.EPOCH));
    }

    @Test
    void retryAfterHttpDateIsHonoredAndInvalidHeaderFallsBack() {
        Instant now = Instant.parse("2026-08-08T05:00:00Z");
        assertEquals(Duration.ofSeconds(5),
                ScryfallClient.retryDelay(
                        "Sat, 8 Aug 2026 05:00:05 GMT", 2, now));
        assertEquals(Duration.ofMillis(1000),
                ScryfallClient.retryDelay("not-a-delay", 1, now));
    }
}
