package app.projection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZoneEventProjectorTest {
    private final ZoneEventProjector projector =
            new ZoneEventProjector(new ZoneTransitionClassifier());

    @Test
    void formatsAResolvedPermanentWithoutReadingGameState() {
        assertEquals(
                "Runeclaw Bear resolves and enters the battlefield tapped",
                projector.describe(
                        "Stack",
                        "Battlefield",
                        "",
                        false,
                        false,
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
                        "Battlefield",
                        "Stack",
                        "",
                        true,
                        false,
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
                        "Exile",
                        "Library",
                        "",
                        false,
                        false,
                        "Alice",
                        "Mystery Card",
                        "activates",
                        ""));
    }
}
