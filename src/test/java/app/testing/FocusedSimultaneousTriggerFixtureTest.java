package app.testing;

import app.export.MatchAiExporter;
import app.model.card.CardInfo;
import app.model.event.GameEvent;
import app.model.session.GameModel;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Focused raw-log regression for multiple triggered abilities observed in one Arena batch. */
final class FocusedSimultaneousTriggerFixtureTest {
    private static final String MATCH_ID = "focused-simultaneous-triggers";

    @Test
    void preservesBothSimultaneousTriggersInObservedStackOrder() throws Exception {
        ArenaLogReplayHarness.ReplayResult replay = new ArenaLogReplayHarness()
                .withCardMetadata(firstSource(), secondSource())
                .replay(fixture());
        GameModel game = replay.requireGame(MATCH_ID, 1);

        List<GameEvent> abilities = game.snapshot().stream()
                .filter(event -> event.getAbility() != null)
                .toList();

        assertEquals(2, abilities.size());
        assertEquals(List.of(7001L, 7002L), abilities.stream()
                .map(event -> event.getAbility().getAbilityGrpId())
                .toList());
        assertEquals(List.of(5001L, 5002L), abilities.stream()
                .map(event -> event.getAbility().getSourceGrpId())
                .toList());
        assertTrue(abilities.stream().allMatch(event ->
                "triggered".equals(event.getAbility().getKind())));

        String export = new MatchAiExporter().export(replay.requireMatch(MATCH_ID));
        String first = "kind=triggered source=c1@5001 abilityGrp=7001";
        String second = "kind=triggered source=c2@5002 abilityGrp=7002";
        assertTrue(export.contains(first), export);
        assertTrue(export.contains(second), export);
        assertTrue(export.indexOf(first) < export.indexOf(second), export);
    }

    private CardInfo firstSource() {
        CardInfo card = new CardInfo();
        card.setArenaId(5001L);
        card.setName("Test Bell");
        card.setOracleText("Whenever another creature enters, you gain 1 life.");
        return card;
    }

    private CardInfo secondSource() {
        CardInfo card = new CardInfo();
        card.setArenaId(5002L);
        card.setName("Test Drum");
        card.setOracleText("Whenever another creature enters, draw a card.");
        return card;
    }

    private Path fixture() throws URISyntaxException {
        return Path.of(FocusedSimultaneousTriggerFixtureTest.class.getResource(
                "/fixtures/validation/focused/simultaneous-triggers.log").toURI());
    }
}
