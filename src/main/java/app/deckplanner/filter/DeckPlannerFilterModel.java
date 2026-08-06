package app.deckplanner.filter;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** Widget-independent mutable interaction model around immutable planner filter state. */
public final class DeckPlannerFilterModel {
    public record State(String format, CardFilterState filters) {
        public State {
            format = normalizeFormat(format);
            filters = filters == null ? CardFilterState.empty() : filters;
        }
    }

    private final List<Consumer<State>> listeners = new ArrayList<>();
    private State state;

    public DeckPlannerFilterModel(String format) {
        this(new State(format, CardFilterState.empty()));
    }

    public DeckPlannerFilterModel(State initialState) {
        state = Objects.requireNonNull(initialState);
    }

    public State state() {
        return state;
    }

    public void addListener(Consumer<State> listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    public void removeListener(Consumer<State> listener) {
        listeners.remove(listener);
    }

    public void setFormat(String format) {
        replace(new State(format, state.filters()));
    }

    public void toggleColor(CardColor color) {
        Objects.requireNonNull(color);
        EnumSet<CardColor> colors = copyColors();
        if (!colors.remove(color)) colors.add(color);
        setFilters(copy(colors, state.filters().includeColorless(), state.filters().colorSource(),
                state.filters().colorMatchMode(), state.filters().baseTypes(),
                state.filters().manaValueRange(), state.filters().selectedTags()));
    }

    public void setIncludeColorless(boolean include) {
        CardFilterState f = state.filters();
        setFilters(copy(f.colors(), include, f.colorSource(), f.colorMatchMode(),
                f.baseTypes(), f.manaValueRange(), f.selectedTags()));
    }

    public void setColorSource(ColorSource source) {
        CardFilterState f = state.filters();
        setFilters(copy(f.colors(), f.includeColorless(), source, f.colorMatchMode(),
                f.baseTypes(), f.manaValueRange(), f.selectedTags()));
    }

    public void setColorMatchMode(ColorMatchMode mode) {
        CardFilterState f = state.filters();
        setFilters(copy(f.colors(), f.includeColorless(), f.colorSource(), mode,
                f.baseTypes(), f.manaValueRange(), f.selectedTags()));
    }

    public void toggleBaseType(BaseCardType type) {
        Objects.requireNonNull(type);
        EnumSet<BaseCardType> types = state.filters().baseTypes().isEmpty()
                ? EnumSet.noneOf(BaseCardType.class)
                : EnumSet.copyOf(state.filters().baseTypes());
        if (!types.remove(type)) types.add(type);
        CardFilterState f = state.filters();
        setFilters(copy(f.colors(), f.includeColorless(), f.colorSource(), f.colorMatchMode(),
                types, f.manaValueRange(), f.selectedTags()));
    }

    public void setManaValueRange(ManaValueRange range) {
        CardFilterState f = state.filters();
        setFilters(copy(f.colors(), f.includeColorless(), f.colorSource(), f.colorMatchMode(),
                f.baseTypes(), range, f.selectedTags()));
    }

    public void toggleTag(SemanticTag tag) {
        Objects.requireNonNull(tag);
        LinkedHashSet<SemanticTag> tags = new LinkedHashSet<>(state.filters().selectedTags());
        if (!tags.remove(tag)) tags.add(tag);
        CardFilterState f = state.filters();
        setFilters(copy(f.colors(), f.includeColorless(), f.colorSource(), f.colorMatchMode(),
                f.baseTypes(), f.manaValueRange(), tags));
    }

    public void resetFilters() {
        replace(new State(state.format(), CardFilterState.empty()));
    }

    public void replace(State next) {
        State normalized = Objects.requireNonNull(next);
        if (state.equals(normalized)) return;
        state = normalized;
        List.copyOf(listeners).forEach(listener -> listener.accept(state));
    }

    private void setFilters(CardFilterState filters) {
        replace(new State(state.format(), filters));
    }

    private EnumSet<CardColor> copyColors() {
        return state.filters().colors().isEmpty()
                ? EnumSet.noneOf(CardColor.class)
                : EnumSet.copyOf(state.filters().colors());
    }

    private static CardFilterState copy(Set<CardColor> colors, boolean includeColorless,
                                        ColorSource source, ColorMatchMode mode,
                                        Set<BaseCardType> baseTypes, ManaValueRange range,
                                        Set<SemanticTag> tags) {
        return new CardFilterState(colors, includeColorless, source, mode, baseTypes, range, tags);
    }

    private static String normalizeFormat(String format) {
        if (format == null || format.isBlank()) return "standard";
        return format.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
