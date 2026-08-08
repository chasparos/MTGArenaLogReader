package app.deckplanner.filter;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DeckPlannerFilterModelTest {
    @Test void appliesDeterministicStructuredAndTagTransitions() {
        DeckPlannerFilterModel model = new DeckPlannerFilterModel(" Standard ");
        SemanticTag mill = new SemanticTag(TagCategory.ACTION, "mill", "Mill");
        List<DeckPlannerFilterModel.State> events = new ArrayList<>();
        model.addListener(events::add);

        model.toggleColor(CardColor.BLUE);
        model.setIncludeColorless(true);
        model.setColorSource(ColorSource.COLOR_IDENTITY);
        model.setColorMatchMode(ColorMatchMode.EXACT);
        model.toggleBaseType(BaseCardType.CREATURE);
        model.setManaValueRange(new ManaValueRange(2, 5));
        model.toggleTag(mill);

        CardFilterState filters = model.state().filters();
        assertEquals("standard", model.state().format());
        assertEquals(Set.of(CardColor.BLUE), filters.colors());
        assertTrue(filters.includeColorless());
        assertEquals(ColorSource.COLOR_IDENTITY, filters.colorSource());
        assertEquals(ColorMatchMode.EXACT, filters.colorMatchMode());
        assertEquals(Set.of(BaseCardType.CREATURE), filters.baseTypes());
        assertEquals(new ManaValueRange(2, 5), filters.manaValueRange());
        assertEquals(Set.of(mill), filters.selectedTags());
        assertEquals(7, events.size());
    }

    @Test void resetKeepsFormatAndClearsEveryFilter() {
        DeckPlannerFilterModel model = new DeckPlannerFilterModel("historic");
        model.toggleColor(CardColor.RED);
        model.toggleBaseType(BaseCardType.INSTANT);
        model.resetFilters();
        assertEquals("historic", model.state().format());
        assertEquals(CardFilterState.empty(), model.state().filters());
    }


    @Test void candidateLayerIsIndependentFromNormalFilters() {
        DeckPlannerFilterModel model = new DeckPlannerFilterModel("standard");
        model.toggleColor(CardColor.BLUE);
        CardFilterState normal = model.state().filters();

        model.setCandidateOnly(true);
        assertTrue(model.state().candidateOnly());
        assertEquals(normal, model.state().filters());

        model.setCandidateOnly(false);
        assertFalse(model.state().candidateOnly());
        assertEquals(normal, model.state().filters());
    }

    @Test void noOpReplacementDoesNotNotifyListeners() {
        DeckPlannerFilterModel model = new DeckPlannerFilterModel("standard");
        List<DeckPlannerFilterModel.State> events = new ArrayList<>();
        model.addListener(events::add);
        model.setFormat("STANDARD");
        assertTrue(events.isEmpty());
    }
}
