package app.projection;

import app.model.card.CardInfo;
import app.model.card.CardRelatedPart;
import app.model.game.GameObjectState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TokenResolverTest {
    private final TokenResolver resolver = new TokenResolver();

    @Test
    void resolvesAnUnambiguousEnrichedRelatedToken() {
        GameObjectState token = token(2, 2, "Goblin");
        token.getColors().add("R");

        CardInfo source = sourceWith(
                related("goblin-token", "Goblin", "Token Creature — Goblin"));

        CardInfo enriched = new CardInfo();
        enriched.setId("goblin-token");
        enriched.setName("Goblin");
        enriched.setTypeLine("Token Creature — Goblin");
        enriched.setPower("2");
        enriched.setToughness("2");
        enriched.setColors(List.of("R"));

        CardInfo resolved = resolver.resolve(
                token,
                Map.of(42L, source),
                Map.of("goblin-token", enriched));

        assertSame(enriched, resolved);
    }

    @Test
    void rejectsAmbiguousTokenCandidates() {
        GameObjectState token = token(null, null, "Soldier");

        CardInfo source = sourceWith(
                related("soldier-a", "Soldier", "Token Creature — Soldier"),
                related("soldier-b", "Soldier", "Token Creature — Soldier"));

        assertNull(resolver.resolve(token, Map.of(42L, source), Map.of()));
    }

    @Test
    void buildsDeterministicFallbackDescriptionFromObservedFacts() {
        GameObjectState token = token(1, 1, "Human", "Soldier");
        token.getColors().addAll(List.of("W", "U"));

        assertEquals("1/1 w/u Human Soldier token", resolver.descriptiveName(token));
    }

    private static GameObjectState token(Integer power, Integer toughness, String... subtypes) {
        GameObjectState token = new GameObjectState();
        token.setObjectSourceGrpId(42L);
        token.setPower(power);
        token.setToughness(toughness);
        token.getSubtypes().addAll(List.of(subtypes));
        return token;
    }

    private static CardInfo sourceWith(CardRelatedPart... parts) {
        CardInfo source = new CardInfo();
        source.setAllParts(List.of(parts));
        return source;
    }

    private static CardRelatedPart related(String id, String name, String typeLine) {
        CardRelatedPart part = new CardRelatedPart();
        part.setId(id);
        part.setComponent("token");
        part.setName(name);
        part.setTypeLine(typeLine);
        return part;
    }
}
