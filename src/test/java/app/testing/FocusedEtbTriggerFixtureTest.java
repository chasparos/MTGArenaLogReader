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

final class FocusedEtbTriggerFixtureTest {
    private static final String MATCH_ID = "focused-etb-trigger";

    @Test
    void entersBattlefieldBeforeOwnTriggeredAbilityAndPreservesItInExport() throws Exception {
        ArenaLogReplayHarness.ReplayResult replay = new ArenaLogReplayHarness()
                .withCardMetadata(etbCreature())
                .replay(fixture("fixtures/validation/focused/etb-trigger.log"));
        GameModel game = replay.requireGame(MATCH_ID, 1);
        List<GameEvent> events = game.snapshot();

        int entryIndex = indexOf(events, "Test Adept resolves and enters the battlefield");
        int abilityIndex = indexOfAbility(events, 5000L, 7000L);

        assertTrue(entryIndex >= 0, "Expected the resolved permanent to enter the battlefield");
        assertTrue(abilityIndex >= 0, "Expected the entering permanent's triggered ability");
        assertTrue(entryIndex < abilityIndex,
                "The battlefield entry must precede its own ETB trigger");
        assertEquals("triggered", events.get(abilityIndex).getAbility().getKind());

        String export = new MatchAiExporter().export(replay.requireMatch(MATCH_ID));
        assertTrue(export.contains(
                "kind=triggered source=c1@5000 abilityGrp=7000"), export);
        assertTrue(export.indexOf("MOVE#") < export.indexOf("A#"), export);
    }

    private CardInfo etbCreature() {
        CardInfo card = new CardInfo();
        card.setArenaId(5000L);
        card.setName("Test Adept");
        card.setOracleText("When Test Adept enters the battlefield, draw a card.");
        return card;
    }

    private int indexOf(List<GameEvent> events, String text) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).getText() != null && events.get(i).getText().contains(text)) return i;
        }
        return -1;
    }

    private int indexOfAbility(List<GameEvent> events, long sourceGrpId, long abilityGrpId) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).getAbility() != null
                    && events.get(i).getAbility().getSourceGrpId() == sourceGrpId
                    && events.get(i).getAbility().getAbilityGrpId() == abilityGrpId) {
                return i;
            }
        }
        return -1;
    }

    private Path fixture(String resource) throws URISyntaxException {
        return Path.of(getClass().getClassLoader().getResource(resource).toURI());
    }
}
