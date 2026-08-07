package app.deckplanner.consideration;

import app.deckplanner.catalog.FormatCatalogRepository;
import app.deckplanner.filter.CatalogFilterIndex;
import app.model.card.CardInfo;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeckListImporterTest {
    @Test void importsArenaDeckSectionsByNameAndCollapsesAlternatePrintings() {
        CardInfo optA = card("opt", "Opt", "opt-a");
        CardInfo optB = card("opt", "Opt", "opt-b");
        CardInfo island = card("island", "Island", "island-a");
        CatalogFilterIndex index = index(optA, optB, island);

        DeckListImporter.Result result = DeckListImporter.resolve("""
                Deck
                4 Opt (STA) 19
                20 Island (MOM) 278

                Sideboard
                1 Opt (ELD) 59
                2 Missing Card (ABC) 1
                """, index);

        assertEquals(4, result.parsedCardLines());
        assertEquals(List.of("oracle:opt", "oracle:island"), result.identities());
        assertEquals(List.of("Missing Card"), result.unresolvedNames());
    }

    private static CatalogFilterIndex index(CardInfo... cards) {
        List<FormatCatalogRepository.CardOutcome> outcomes = Arrays.stream(cards)
                .map(card -> new FormatCatalogRepository.CardOutcome(card, "SUCCESS", null)).toList();
        return new CatalogFilterIndex(new FormatCatalogRepository.Snapshot(
                "run", "standard", 1, Instant.EPOCH, Instant.EPOCH, outcomes));
    }

    private static CardInfo card(String oracleId, String name, String scryfallId) {
        CardInfo card = new CardInfo();
        card.setId(scryfallId);
        card.setOracleId(oracleId);
        card.setName(name);
        card.setArenaId((long) Math.abs(scryfallId.hashCode()) + 1);
        card.setColors(List.of("U"));
        card.setColorIdentity(List.of("U"));
        card.setTypeLine("Instant");
        card.setCmc(1.0);
        card.setOracleText("Rules");
        card.setKeywords(List.of());
        return card;
    }
}
