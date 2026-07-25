package app.replay;

import app.model.card.CardInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ActivatedAbilityParserTest {
    private final ActivatedAbilityParser parser = new ActivatedAbilityParser();

    @Test
    void extractsTapManaAbility() {
        CardInfo card = card("{T}: Add {G} or {W}.");

        ActivatedAbilityParser.Badge badge = parser.parse(card);

        assertNotNull(badge);
        assertTrue(badge.tap());
        assertEquals("", badge.manaCost());
        assertEquals("", badge.textCost());
        assertEquals(List.of("G", "W"), badge.manaOptions());
    }

    @Test
    void extractsManaAndShortTextCosts() {
        CardInfo card = card("{2}{U}, Sac: Draw a card.");

        ActivatedAbilityParser.Badge badge = parser.parse(card);

        assertNotNull(badge);
        assertFalse(badge.tap());
        assertEquals("{2}{U}", badge.manaCost());
        assertEquals("Sac", badge.textCost());
        assertTrue(badge.manaOptions().isEmpty());
    }

    @Test
    void ignoresTriggeredAbilityText() {
        assertNull(parser.parse(card("Whenever this attacks: draw a card.")));
    }

    private CardInfo card(String oracleText) {
        CardInfo card = new CardInfo();
        card.setOracleText(oracleText);
        return card;
    }
}
