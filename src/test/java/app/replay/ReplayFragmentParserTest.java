package app.replay;

import app.model.card.CardInfo;
import app.model.event.GameEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReplayFragmentParserTest {
    private final ReplayFragmentParser parser = new ReplayFragmentParser();

    @Test
    void separatesCardsManaPowerToughnessAndKeywords() {
        CardInfo card = card("Llanowar Elves", 692);
        GameEvent event = event(
                "Llanowar Elves is 3/3 and gains flying. Add {G}.",
                card);

        List<ReplayFragment> fragments = parser.parse(event);

        assertTrue(fragments.stream().anyMatch(fragment ->
                fragment instanceof CardFragment cardFragment
                        && cardFragment.card() == card));
        assertTrue(fragments.stream().anyMatch(fragment ->
                fragment instanceof PowerToughnessFragment(
                        String power, String toughness)
                        && "3".equals(power) && "3".equals(toughness)));
        assertTrue(fragments.stream().anyMatch(fragment ->
                fragment instanceof KeywordFragment keyword
                        && "flying".equals(keyword.keyword())));
        assertTrue(fragments.stream().anyMatch(fragment ->
                fragment instanceof ManaFragment mana
                        && "G".equals(mana.symbol())));
    }

    @Test
    void prefersTheLongestCardNameAtTheSamePosition() {
        CardInfo shortName = card("Hope", 1);
        CardInfo longName = card("Hope of Ghirapur", 2);
        GameEvent event = event("Hope of Ghirapur attacks.", shortName, longName);

        CardFragment firstCard = parser.parse(event).stream()
                .filter(CardFragment.class::isInstance)
                .map(CardFragment.class::cast)
                .findFirst()
                .orElseThrow();

        assertSame(longName, firstCard.card());
        assertEquals("Hope of Ghirapur", firstCard.label());
    }

    @Test
    void keepsAnEmptyTextFragmentForEventsWithoutText() {
        GameEvent event = new GameEvent();

        assertEquals(List.of(new TextFragment("")), parser.parse(event));
    }

    private GameEvent event(String text, CardInfo... cards) {
        GameEvent event = new GameEvent();
        event.setText(text);
        event.getCards().addAll(List.of(cards));
        return event;
    }

    private CardInfo card(String name, long arenaId) {
        CardInfo card = new CardInfo();
        card.setName(name);
        card.setArenaId(arenaId);
        return card;
    }
}
