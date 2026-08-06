package app.deckplanner.filter;

import java.util.Set;

public record CardFilterState(Set<CardColor> colors, boolean includeColorless,
                              ColorSource colorSource, ColorMatchMode colorMatchMode,
                              Set<BaseCardType> baseTypes, ManaValueRange manaValueRange,
                              Set<SemanticTag> selectedTags) {
    public CardFilterState {
        colors = colors == null ? Set.of() : Set.copyOf(colors);
        colorSource = colorSource == null ? ColorSource.CARD_COLORS : colorSource;
        colorMatchMode = colorMatchMode == null ? ColorMatchMode.INCLUSIVE : colorMatchMode;
        baseTypes = baseTypes == null ? Set.of() : Set.copyOf(baseTypes);
        selectedTags = selectedTags == null ? Set.of() : Set.copyOf(selectedTags);
    }
    public static CardFilterState empty() {
        return new CardFilterState(Set.of(), false, ColorSource.CARD_COLORS,
                ColorMatchMode.INCLUSIVE, Set.of(), null, Set.of());
    }
    public CardFilterState withoutTags() {
        return new CardFilterState(colors, includeColorless, colorSource, colorMatchMode,
                baseTypes, manaValueRange, Set.of());
    }
}
