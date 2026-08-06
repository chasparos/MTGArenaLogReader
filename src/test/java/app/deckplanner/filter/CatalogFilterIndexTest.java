package app.deckplanner.filter;

import app.deckplanner.catalog.FormatCatalogRepository;
import app.model.card.CardFaceInfo;
import app.model.card.CardInfo;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CatalogFilterIndexTest {
    @Test void structuredFiltersCoverColorSemanticsTypesFacesAndManaValues() {
        CardInfo monoWhite = card("w", List.of("W"), List.of("W"), "Legendary Creature — Human", 2.0,
                "Target creature gets +1/+1.", List.of("Flying"));
        CardInfo azorius = card("wu", List.of("W", "U"), List.of("W", "U"), "Instant", 2.5,
                "Counter target spell.", List.of());
        CardInfo land = card("land", List.of(), List.of("U"), "Land — Island", null,
                "{T}: Add {U}.", List.of());
        CardInfo modal = card("modal", List.of("B"), List.of("B"), null, 3.0,
                null, List.of());
        CardFaceInfo creatureFace = new CardFaceInfo(); creatureFace.setTypeLine("Creature — Vampire");
        creatureFace.setOracleText("When this enters, mill two cards.");
        CardFaceInfo sorceryFace = new CardFaceInfo(); sorceryFace.setTypeLine("Sorcery");
        modal.setCardFaces(List.of(creatureFace, sorceryFace));

        CatalogFilterIndex index = index(monoWhite, azorius, land, modal);
        assertEquals(2, index.filter(new CardFilterState(Set.of(CardColor.WHITE, CardColor.BLUE), false,
                ColorSource.CARD_COLORS, ColorMatchMode.INCLUSIVE, Set.of(), null, Set.of())).size());
        assertEquals(List.of("wu"), names(index.filter(new CardFilterState(Set.of(CardColor.WHITE, CardColor.BLUE), false,
                ColorSource.CARD_COLORS, ColorMatchMode.EXACT, Set.of(), null, Set.of()))));
        assertEquals(List.of("land"), names(index.filter(new CardFilterState(Set.of(CardColor.BLUE), false,
                ColorSource.COLOR_IDENTITY, ColorMatchMode.EXACT, Set.of(BaseCardType.LAND), new ManaValueRange(0, 0), Set.of()))));
        assertEquals(List.of("modal"), names(index.filter(new CardFilterState(Set.of(), false,
                ColorSource.CARD_COLORS, ColorMatchMode.INCLUSIVE, Set.of(BaseCardType.SORCERY), new ManaValueRange(2.75, 3.25), Set.of()))));
    }

    @Test void tagsAreVersionedDeterministicAndAvoidSubstringFalsePositives() {
        CardInfo tagged = card("tagged", List.of("B"), List.of("B"), "Sorcery", 4.0,
                "All creatures get -1/-1. Sacrifice a creature. Mill three cards, then return a card from your graveyard to your hand.",
                List.of("Flashback"));
        CardInfo falsePositive = card("false", List.of("U"), List.of("U"), "Creature — Homarid", 2.0,
                "This creature has a handlike claw and a battlefield promotion.", List.of());
        CatalogFilterIndex index = index(tagged, falsePositive);
        Map<SemanticTag, Long> cloud = index.tagCloud(CardFilterState.empty());
        assertEquals(1, CardTagRules.VERSION);
        assertEquals(1L, cloud.get(new SemanticTag(TagCategory.ACTION, "mill", "Mill")));
        assertEquals(1L, cloud.get(new SemanticTag(TagCategory.ACTION, "sacrifice", "Sacrifice")));
        assertEquals(1L, cloud.get(new SemanticTag(TagCategory.CONCEPT, "all-creatures", "All creatures")));
        assertEquals(1L, cloud.get(new SemanticTag(TagCategory.ZONE, "graveyard", "Graveyard")));
        assertEquals(1L, cloud.get(new SemanticTag(TagCategory.KEYWORD, "flashback", "Flashback")));
        assertFalse(index.cards().get(1).tags().stream().anyMatch(tag -> tag.key().equals("hand")));
    }

    @Test void selectedTagsAreAnAndLayerButCloudIgnoresTheTagLayer() {
        CardInfo both = card("both", List.of("B"), List.of("B"), "Sorcery", 2.0,
                "Target player mills two cards.", List.of());
        CardInfo millOnly = card("mill", List.of("B"), List.of("B"), "Sorcery", 2.0,
                "Mill two cards.", List.of());
        CatalogFilterIndex index = index(both, millOnly);
        SemanticTag mill = new SemanticTag(TagCategory.ACTION, "mill", "Mill");
        SemanticTag target = new SemanticTag(TagCategory.ACTION, "target", "Target");
        CardFilterState state = new CardFilterState(Set.of(CardColor.BLACK), false, ColorSource.CARD_COLORS,
                ColorMatchMode.EXACT, Set.of(), null, Set.of(mill, target));
        assertEquals(List.of("both"), names(index.filter(state)));
        assertEquals(2L, index.tagCloud(state).get(mill));
        assertEquals(1L, index.tagCloud(state).get(target));
    }

    private CatalogFilterIndex index(CardInfo... cards) {
        List<FormatCatalogRepository.CardOutcome> outcomes = java.util.Arrays.stream(cards)
                .map(card -> new FormatCatalogRepository.CardOutcome(card, "SUCCESS", null)).toList();
        return new CatalogFilterIndex(new FormatCatalogRepository.Snapshot("run", "standard", 1,
                Instant.EPOCH, Instant.EPOCH, outcomes));
    }
    private CardInfo card(String name, List<String> colors, List<String> identity, String type, Double cmc,
                          String oracle, List<String> keywords) {
        CardInfo card = new CardInfo(); card.setId(name); card.setOracleId(name); card.setName(name);
        card.setArenaId((long) Math.abs(name.hashCode()) + 1); card.setColors(colors); card.setColorIdentity(identity);
        card.setTypeLine(type); card.setCmc(cmc); card.setOracleText(oracle); card.setKeywords(keywords); return card;
    }
    private List<String> names(List<IndexedCatalogCard> cards) {
        return cards.stream().map(card -> card.group().preferredPrinting().getName()).toList();
    }
}
