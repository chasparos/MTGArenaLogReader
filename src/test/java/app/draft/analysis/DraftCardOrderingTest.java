package app.draft.analysis;

import app.draft.model.DraftCardCount;
import app.model.card.CardInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DraftCardOrderingTest {
    @Test
    void ordersByTypeThenManaValueThenColor() {
        CardInfo redCreature = card(1, "Red creature", "Creature", "R", 3);
        CardInfo whiteCreature = card(2, "White creature", "Creature", "W", 4);
        CardInfo blueInstant = card(3, "Blue instant", "Instant", "U", 1);

        List<DraftCardCount> sorted = new DraftCardOrdering().sort(
                List.of(
                        new DraftCardCount(3, 1),
                        new DraftCardCount(1, 1),
                        new DraftCardCount(2, 1)),
                Map.of(1L, redCreature, 2L, whiteCreature, 3L, blueInstant));

        assertEquals(List.of(1L, 2L, 3L), sorted.stream()
                .map(DraftCardCount::arenaId).toList());
    }

    private CardInfo card(
            long id, String name, String type, String color, double cmc) {
        CardInfo card = new CardInfo();
        card.setArenaId(id);
        card.setName(name);
        card.setTypeLine(type);
        card.setColors(List.of(color));
        card.setColorIdentity(List.of(color));
        card.setCmc(cmc);
        return card;
    }
}
