package app.deckplanner.consideration;

import app.deckplanner.catalog.FormatCatalogRepository;
import app.deckplanner.filter.CatalogFilterIndex;
import app.model.card.CardInfo;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UnderConsiderationModelTest {
    @Test void preservesOrderDeduplicatesAndReordersLogicalIdentities() {
        List<List<String>> saved = new ArrayList<>();
        UnderConsiderationModel model = new UnderConsiderationModel(
                List.of("oracle:a"), identities -> saved.add(List.copyOf(identities)));

        model.add(List.of("oracle:b", "oracle:a", "oracle:c"));
        assertEquals(List.of("oracle:a", "oracle:b", "oracle:c"), model.identities());

        model.move("oracle:c", -1);
        assertEquals(List.of("oracle:a", "oracle:c", "oracle:b"), model.identities());

        model.remove("oracle:a");
        assertEquals(List.of("oracle:c", "oracle:b"), model.identities());
        assertEquals(model.identities(), saved.getLast());
    }

    @Test void catalogRefreshKeepsMissingCandidatesRecoverableAndResolvesReturningIdentity() {
        UnderConsiderationModel model = new UnderConsiderationModel(
                List.of("oracle:keep", "oracle:missing"), ignored -> { });

        List<UnderConsiderationModel.Entry> first = model.resolve(index(card("keep", "Keep")));
        assertFalse(first.get(0).stale());
        assertTrue(first.get(1).stale());

        List<UnderConsiderationModel.Entry> refreshed = model.resolve(index(
                card("missing", "Returned"), card("keep", "New printing")));
        assertEquals(List.of("oracle:keep", "oracle:missing"),
                refreshed.stream().map(UnderConsiderationModel.Entry::identity).toList());
        assertTrue(refreshed.stream().noneMatch(UnderConsiderationModel.Entry::stale));
        assertEquals("New printing",
                refreshed.get(0).card().orElseThrow().group().preferredPrinting().getName());
    }

    @Test void alternatePrintingsShareOneConsiderationIdentity() {
        CardInfo first = card("same", "First printing");
        first.setId("printing-1");
        CardInfo second = card("same", "Second printing");
        second.setId("printing-2");

        CatalogFilterIndex index = index(first, second);
        assertEquals(1, index.cards().size(), "oracle identity should group alternate printings");

        String logicalIdentity = index.cards().getFirst().group().identity();
        UnderConsiderationModel model = new UnderConsiderationModel(List.of(), ignored -> { });
        model.add(List.of(logicalIdentity, logicalIdentity));
        assertEquals(List.of(logicalIdentity), model.identities());
    }

    @Test void arbitraryInsertionMovePersistsDragDropOrder() {
        List<List<String>> saved = new ArrayList<>();
        UnderConsiderationModel model = new UnderConsiderationModel(
                List.of("oracle:a", "oracle:b", "oracle:c", "oracle:d"),
                identities -> saved.add(List.copyOf(identities)));

        model.moveToIndex("oracle:a", 4);
        assertEquals(List.of("oracle:b", "oracle:c", "oracle:d", "oracle:a"), model.identities());

        model.moveToIndex("oracle:d", 0);
        assertEquals(List.of("oracle:d", "oracle:b", "oracle:c", "oracle:a"), model.identities());
        assertEquals(model.identities(), saved.getLast());
    }

    @Test void normalMagicSortUsesSharedTypeManaColorNameOrderAndKeepsStaleLast() {
        CardInfo redCreature = card("red", "Red creature");
        redCreature.setColors(List.of("R"));
        redCreature.setColorIdentity(List.of("R"));
        redCreature.setCmc(3.0);
        CardInfo whiteCreature = card("white", "White creature");
        whiteCreature.setColors(List.of("W"));
        whiteCreature.setColorIdentity(List.of("W"));
        whiteCreature.setCmc(4.0);
        CardInfo blueInstant = card("blue", "Blue instant");
        blueInstant.setColors(List.of("U"));
        blueInstant.setColorIdentity(List.of("U"));
        blueInstant.setTypeLine("Instant");
        blueInstant.setCmc(1.0);

        UnderConsiderationModel model = new UnderConsiderationModel(
                List.of("oracle:missing", "oracle:blue", "oracle:white", "oracle:red"), ignored -> { });
        model.sortByMagic(index(blueInstant, whiteCreature, redCreature));

        assertEquals(List.of("oracle:red", "oracle:white", "oracle:blue", "oracle:missing"),
                model.identities());
    }

    private static CatalogFilterIndex index(CardInfo... cards) {
        List<FormatCatalogRepository.CardOutcome> outcomes = java.util.Arrays.stream(cards)
                .map(card -> new FormatCatalogRepository.CardOutcome(card, "SUCCESS", null)).toList();
        return new CatalogFilterIndex(new FormatCatalogRepository.Snapshot(
                "run", "standard", 1, Instant.EPOCH, Instant.EPOCH, outcomes));
    }

    private static CardInfo card(String oracleId, String name) {
        CardInfo card = new CardInfo();
        card.setId(name.toLowerCase().replace(' ', '-'));
        card.setOracleId(oracleId);
        card.setName(name);
        card.setArenaId((long) Math.abs(name.hashCode()) + 1);
        card.setColors(List.of("U"));
        card.setColorIdentity(List.of("U"));
        card.setTypeLine("Creature");
        card.setCmc(2.0);
        card.setOracleText("Rules");
        card.setKeywords(List.of());
        return card;
    }
}
