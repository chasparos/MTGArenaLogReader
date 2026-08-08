package app.deckplanner.candidate;

import app.deckplanner.catalog.FormatCatalogRepository;
import app.deckplanner.filter.CatalogFilterIndex;
import app.model.card.CardInfo;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CandidateWorkspaceStateTest {
    @Test void removingCategoryMovesItsCardsToPersistentUncategorizedState() {
        CardInfo creature = card("creature", "Creature", "Creature — Elf");
        CandidateModel model = new CandidateModel(List.of("oracle:creature"), ignored -> { });
        CandidateModel.Entry entry = model.resolve(index(creature)).getFirst();
        CandidateWorkspaceState state = CandidateWorkspaceState.transientState();

        assertEquals(CandidateWorkspaceState.CREATURES, state.categoryFor(entry));
        state.removeCategory(CandidateWorkspaceState.CREATURES, List.of(entry));

        assertEquals(CandidateWorkspaceState.UNCATEGORIZED, state.categoryFor(entry));
        assertEquals(CandidateWorkspaceState.UNCATEGORIZED,
                state.assignments().get("oracle:creature"));
        assertFalse(state.categories().stream()
                .anyMatch(category -> category.id().equals(CandidateWorkspaceState.CREATURES)));
    }

    @Test void categoriesCanBeAddedAndReorderedWithoutChangingCandidateMembership() {
        CandidateWorkspaceState state = CandidateWorkspaceState.transientState();
        CandidateWorkspaceState.Category custom = state.addCategory("Win Cons");
        assertEquals("win-cons", custom.id());

        state.moveCategory(custom.id(), -1);
        state.moveCategory(custom.id(), -1);
        state.moveCategory(custom.id(), -1);

        assertEquals(custom.id(), state.categories().getFirst().id());
    }

    private static CatalogFilterIndex index(CardInfo... cards) {
        List<FormatCatalogRepository.CardOutcome> outcomes = java.util.Arrays.stream(cards)
                .map(card -> new FormatCatalogRepository.CardOutcome(card, "SUCCESS", null)).toList();
        return new CatalogFilterIndex(new FormatCatalogRepository.Snapshot(
                "run", "standard", 1, Instant.EPOCH, Instant.EPOCH, outcomes));
    }

    private static CardInfo card(String oracleId, String name, String typeLine) {
        CardInfo card = new CardInfo();
        card.setId("printing-" + oracleId);
        card.setOracleId(oracleId);
        card.setName(name);
        card.setArenaId(123L);
        card.setColors(List.of("G"));
        card.setColorIdentity(List.of("G"));
        card.setTypeLine(typeLine);
        card.setCmc(2.0);
        card.setOracleText("Rules");
        card.setKeywords(List.of());
        return card;
    }
}
