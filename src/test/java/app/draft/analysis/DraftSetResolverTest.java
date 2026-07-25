package app.draft.analysis;

import app.model.card.CardInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DraftSetResolverTest {
    @Test
    void infersTheDominantSetFromMixedPackMetadata() {
        assertEquals("dft", new DraftSetResolver().infer(List.of(
                card("dft"), card("dft"), card("spg"))).orElseThrow());
    }

    private CardInfo card(String set) {
        CardInfo card = new CardInfo();
        card.setSet(set);
        return card;
    }
}
