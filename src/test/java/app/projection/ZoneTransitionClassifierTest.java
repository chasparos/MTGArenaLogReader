package app.projection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZoneTransitionClassifierTest {

    private final ZoneTransitionClassifier classifier = new ZoneTransitionClassifier();

    @Test
    void explicitCategoryTakesPrecedenceOverOrdinaryZonePair() {
        assertEquals(
                ZoneTransitionClassifier.Kind.PLAY_LAND,
                classifier.classify("Hand", "Battlefield", "PlayLand", false, false));
    }

    @Test
    void classifiesCommonImplicitTransitions() {
        assertEquals(
                ZoneTransitionClassifier.Kind.DRAW,
                classifier.classify("Library", "Hand", "", false, false));
        assertEquals(
                ZoneTransitionClassifier.Kind.RESOLVE_TO_BATTLEFIELD,
                classifier.classify("Stack", "Battlefield", "", false, false));
        assertEquals(
                ZoneTransitionClassifier.Kind.BATTLEFIELD_TO_GRAVEYARD,
                classifier.classify("Battlefield", "Graveyard", "", false, false));
    }

    @Test
    void abilityClassificationOverridesCardTransitionRules() {
        assertEquals(
                ZoneTransitionClassifier.Kind.ABILITY_ON_STACK,
                classifier.classify("Hand", "Stack", "CastSpell", true, false));
        assertEquals(
                ZoneTransitionClassifier.Kind.ABILITY_FINISHED,
                classifier.classify("Stack", "Graveyard", "", true, false));
    }
}
