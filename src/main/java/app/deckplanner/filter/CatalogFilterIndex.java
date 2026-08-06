package app.deckplanner.filter;

import app.deckplanner.catalog.FormatCatalogRepository;
import app.model.card.CardFaceInfo;
import app.model.card.CardInfo;

import java.util.*;
import java.util.stream.Collectors;

/** Immutable searchable projection of one completed format catalog snapshot. */
public final class CatalogFilterIndex {
    private final List<IndexedCatalogCard> cards;

    public CatalogFilterIndex(FormatCatalogRepository.Snapshot snapshot) {
        this(snapshot == null ? List.of() : snapshot.cardGroups(), new CardTagRules());
    }
    CatalogFilterIndex(List<FormatCatalogRepository.CardGroup> groups, CardTagRules tagRules) {
        Objects.requireNonNull(groups); Objects.requireNonNull(tagRules);
        cards = groups.stream().map(group -> index(group, tagRules)).toList();
    }
    public List<IndexedCatalogCard> cards() { return cards; }
    public List<IndexedCatalogCard> filter(CardFilterState state) {
        CardFilterState effective = state == null ? CardFilterState.empty() : state;
        return cards.stream().filter(card -> matchesStructured(card, effective))
                .filter(card -> card.tags().containsAll(effective.selectedTags())).toList();
    }
    public Map<SemanticTag, Long> tagCloud(CardFilterState state) {
        CardFilterState effective = state == null ? CardFilterState.empty() : state;
        return cards.stream().filter(card -> matchesStructured(card, effective))
                .filter(card -> card.tags().containsAll(effective.selectedTags()))
                .flatMap(card -> card.tags().stream()).collect(Collectors.groupingBy(
                        tag -> tag, TreeMap::new, Collectors.counting()));
    }
    private boolean matchesStructured(IndexedCatalogCard card, CardFilterState state) {
        if (!matchesColors(card, state)) return false;
        if (!state.baseTypes().isEmpty() && Collections.disjoint(card.baseTypes(), state.baseTypes())) return false;
        return state.manaValueRange() == null || state.manaValueRange().contains(card.manaValue());
    }
    private boolean matchesColors(IndexedCatalogCard card, CardFilterState state) {
        if (state.colors().isEmpty() && !state.includeColorless()) return true;
        Set<CardColor> actual = state.colorSource() == ColorSource.COLOR_IDENTITY ? card.colorIdentity() : card.colors();
        if (actual.isEmpty()) return state.includeColorless();
        if (state.colors().isEmpty()) return false;
        return state.colorMatchMode() == ColorMatchMode.EXACT
                ? actual.equals(state.colors()) : state.colors().containsAll(actual);
    }
    private IndexedCatalogCard index(FormatCatalogRepository.CardGroup group, CardTagRules rules) {
        CardInfo card = group.preferredPrinting();
        return new IndexedCatalogCard(group, colors(card.getColors()), colors(card.getColorIdentity()),
                baseTypes(card), manaValue(card), rules.tags(card));
    }
    private Set<CardColor> colors(List<String> symbols) {
        if (symbols == null) return Set.of();
        EnumSet<CardColor> result = EnumSet.noneOf(CardColor.class);
        for (String symbol : symbols) for (CardColor color : CardColor.values()) if (color.symbol().equals(symbol)) result.add(color);
        return Set.copyOf(result);
    }
    private Set<BaseCardType> baseTypes(CardInfo card) {
        EnumSet<BaseCardType> result = EnumSet.noneOf(BaseCardType.class);
        collectTypes(result, card.getTypeLine());
        if (card.getCardFaces() != null) for (CardFaceInfo face : card.getCardFaces()) if (face != null) collectTypes(result, face.getTypeLine());
        return Set.copyOf(result);
    }
    private void collectTypes(Set<BaseCardType> result, String typeLine) {
        if (typeLine == null) return;
        String left = typeLine.split("[—-]", 2)[0].toUpperCase(Locale.ROOT);
        for (BaseCardType type : BaseCardType.values()) if (PatternWord.contains(left, type.name())) result.add(type);
    }
    /**
     * Uses Scryfall's top-level {@code cmc} exactly as the planning mana value.
     *
     * <p>That field already applies Scryfall's layout-aware rules for split, adventure, transform,
     * and modal cards. Fractional values are retained. Missing or invalid values fall back to zero,
     * which is also the correct value for lands whose payload omitted {@code cmc}.</p>
     */
    private double manaValue(CardInfo card) {
        Double cmc = card.getCmc();
        return cmc != null && Double.isFinite(cmc) && cmc >= 0d ? cmc : 0d;
    }
    private static final class PatternWord {
        static boolean contains(String text, String word) { return Arrays.asList(text.split("\\s+")).contains(word); }
    }
}
