package app.deckplanner.candidate;

import app.deckplanner.catalog.FormatCatalogRepository;
import app.deckplanner.filter.CatalogFilterIndex;
import app.model.card.CardInfo;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CandidateIllegalResolutionTest {
    @Test void cachedOffFormatCardIsResolvedButMarkedIllegalInsteadOfStale() {
        CandidateModel model = new CandidateModel(List.of("oracle:off-format"), ignored -> { });
        CatalogFilterIndex empty = new CatalogFilterIndex(new FormatCatalogRepository.Snapshot(
                "run", "standard", 1, Instant.EPOCH, Instant.EPOCH, List.of()));
        CardInfo card = new CardInfo();
        card.setId("printing");
        card.setOracleId("off-format");
        card.setName("Off Format");

        CandidateModel.Entry entry = model.resolve(empty,
                identity -> Optional.of(card)).getFirst();

        assertFalse(entry.legal());
        assertFalse(entry.stale());
        assertEquals("Off Format", entry.resolvedCard().orElseThrow().getName());
    }
}
