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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Focused raw-log regression for Saga chapter identification and export. */
final class FocusedSagaChapterFixtureTest {
    @Test
    void projectsSecondSagaChapterAndSuppressesArenaSelfTargetNoise() throws Exception {
        ArenaLogReplayHarness.ReplayResult replay = new ArenaLogReplayHarness()
                .withCardMetadata(sagaCard())
                .replay(fixture());
        GameModel game = replay.requireGame("focused-saga", 1);

        List<GameEvent> abilities = game.snapshot().stream()
                .filter(event -> event.getAbility() != null)
                .toList();
        assertEquals(1, abilities.size());

        GameEvent chapter = abilities.getFirst();
        assertEquals("triggered", chapter.getAbility().getKind());
        assertEquals(5000L, chapter.getAbility().getSourceGrpId());
        assertEquals(7002L, chapter.getAbility().getAbilityGrpId());
        assertEquals(2, chapter.getAbility().getChapter());
        assertTrue(chapter.getText().contains("chapter II ability triggers"));

        assertFalse(game.snapshot().stream().anyMatch(event -> event.getTargetObservation() != null),
                "Arena's Saga self-target annotation is implementation noise, not a player target decision");

        String export = new MatchAiExporter().export(replay.requireMatch("focused-saga"));
        assertTrue(export.contains(
                "kind=triggered source=c1@5000 abilityGrp=7002 chapter=2"));
        assertFalse(export.contains("TARGET#"));
    }

    private CardInfo sagaCard() {
        CardInfo card = new CardInfo();
        card.setArenaId(5000L);
        card.setName("The Three Seasons");
        card.setTypeLine("Enchantment — Saga");
        return card;
    }

    private Path fixture() throws URISyntaxException {
        return Path.of(FocusedSagaChapterFixtureTest.class.getResource(
                "/fixtures/validation/focused/saga-chapter.log").toURI());
    }
}
