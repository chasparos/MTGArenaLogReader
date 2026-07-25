package app.draft.analysis;

import app.draft.model.DraftCardCount;
import app.model.card.CardInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DraftDeckAnalysisTest {
    @Test
    void countsCreaturesRemovalPipsAndManaCurveByQuantity() {
        CardInfo creature = card(
                1, "Bear", "{1}{G}", 2, "Creature — Bear", "");
        CardInfo removal = card(
                2, "Banishing", "{1}{W}{W}", 3, "Sorcery",
                "Exile target creature.");
        CardInfo land = card(
                3, "Forest", "", 0, "Basic Land — Forest", "");

        DraftDeckAnalysis.Summary summary = new DraftDeckAnalysis().analyze(
                List.of(
                        new DraftCardCount(1, 2),
                        new DraftCardCount(2, 1),
                        new DraftCardCount(3, 17)),
                Map.of(1L, creature, 2L, removal, 3L, land));

        assertEquals(20, summary.totalCards());
        assertEquals(2, summary.creatures());
        assertEquals(1, summary.removal());
        assertEquals(2, summary.colorPips().get("G"));
        assertEquals(2, summary.colorPips().get("W"));
        assertEquals(2, summary.manaCurve().get(2));
        assertEquals(1, summary.manaCurve().get(3));
        assertEquals(0, summary.manaCurve().get(0));
    }

    private CardInfo card(
            long id, String name, String manaCost, double cmc,
            String type, String oracle) {
        CardInfo card = new CardInfo();
        card.setArenaId(id);
        card.setName(name);
        card.setManaCost(manaCost);
        card.setCmc(cmc);
        card.setTypeLine(type);
        card.setOracleText(oracle);
        return card;
    }
}
