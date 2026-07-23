package app.coaching.application;

import app.replay.GameView;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ManualCoachingPromptBuilderTest {
    private static final String MATCH = """
            MTGA_MATCH_V4
            K G=game T=turn S=state E=event
            CARD c1=Example@1
            match=m1

            G1 complete
            H player=p1 mull=0 cards=c1
            T1 active=p1
            S#1 p1 life=20 hand=7 board=-
            E#2 text=p1 plays c1
            T2 active=p2
            S#3 p1 life=20 hand=6 board=c1#1
            E#4 text=p2 passes
            G2
            T1 active=p2
            E#5 text=p2 plays c1
            """;

    private final ManualCoachingPromptBuilder builder = new ManualCoachingPromptBuilder();

    @Test
    void gameContextKeepsDictionaryAndOnlySelectedGame() {
        String prompt = builder.build(
                MATCH, GameView.CoachingScope.GAME, 1, Set.of(), "Review this game");

        assertTrue(prompt.contains("CARD c1=Example@1"));
        assertTrue(prompt.contains("G1 complete"));
        assertTrue(prompt.contains("E#4 text=p2 passes"));
        assertFalse(prompt.contains("\nG2\n"));
        assertFalse(prompt.contains("E#5"));
    }

    @Test
    void turnContextKeepsOpeningAndOnlySelectedTurns() {
        String prompt = builder.build(
                MATCH, GameView.CoachingScope.TURN, 1, Set.of(2), "Was this turn correct?");

        assertTrue(prompt.contains("H player=p1"));
        assertTrue(prompt.contains("T2 active=p2"));
        assertTrue(prompt.contains("S#3"));
        assertFalse(prompt.contains("T1 active=p1"));
        assertFalse(prompt.contains("E#2"));
        assertTrue(prompt.contains("The supplied reconstruction is authoritative."));
        assertTrue(prompt.contains("Was this turn correct?"));
        assertFalse(prompt.contains("${question}"));
        assertFalse(prompt.contains("${context}"));
    }
    @Test
    void rejectsTemplateWithoutRequiredProperties() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new ManualCoachingPromptBuilder("QUESTION ${question}"));
    }
}
