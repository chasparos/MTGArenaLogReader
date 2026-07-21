package app.projection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZoneEventProjectorTest {
    private final ZoneEventProjector projector = new ZoneEventProjector();

    @Test
    void formatsAResolvedPermanentWithoutReadingGameState() {
        assertEquals(
                "Runeclaw Bear resolves and enters the battlefield tapped",
                projector.describe(
                        ZoneTransitionClassifier.Kind.RESOLVE_TO_BATTLEFIELD,
                        "Stack",
                        "Battlefield",
                        "Alice",
                        "Runeclaw Bear",
                        "activates",
                        " tapped"));
    }

    @Test
    void formatsAbilityTransitionsUsingTheProvidedVerb() {
        assertEquals(
                "Alice triggers Soul Warden",
                projector.describe(
                        ZoneTransitionClassifier.Kind.ABILITY_ON_STACK,
                        "Battlefield",
                        "Stack",
                        "Alice",
                        "Soul Warden",
                        "triggers",
                        ""));
    }

    @Test
    void preservesGenericTransitionWording() {
        assertEquals(
                "Alice: Mystery Card moved Exile → Library",
                projector.describe(
                        ZoneTransitionClassifier.Kind.GENERIC,
                        "Exile",
                        "Library",
                        "Alice",
                        "Mystery Card",
                        "activates",
                        ""));
    }
}
