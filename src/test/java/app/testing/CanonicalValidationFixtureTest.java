package app.testing;

import app.export.MatchAiExporter;
import app.model.event.GameEvent;
import app.model.game.PlayerTurnDelta;
import app.model.game.PlayerTurnSnapshot;
import app.model.session.GameModel;
import app.model.session.MatchSession;
import app.projection.TurnStateDiffer;
import app.projection.TurnStateReconstructor;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CanonicalValidationFixtureTest {
    private static final String MATCH_ID = "f630d9fe-76f7-4f65-ad02-d4b7820abe30";

    @Test
    void validatesMegaStrangeGameAcrossReplayExportAndRoundTripBoundaries() throws Exception {
        ArenaLogReplayHarness.ReplayResult replay = new ArenaLogReplayHarness().replayGzip(fixture());
        GameModel game = replay.requireGame(MATCH_ID, 1);
        MatchSession match = replay.requireMatch(MATCH_ID);

        assertTrue(game.isComplete(), "canonical fixture must reach a projected game result");
        assertTrue(game.rawRecordSnapshot().size() > 1_000, "fixture must remain a substantial raw replay");

        ValidationSummary summary = ValidationSummary.from(game);
        assertTrue(summary.turns() >= 10, summary.toString());
        assertTrue(summary.snapshots() >= 10, summary.toString());
        assertTrue(summary.zoneTransitions() > 0, summary.toString());
        assertTrue(summary.damageEvents() > 0, summary.toString());
        assertTrue(summary.targetEvents() > 0, summary.toString());
        assertTrue(summary.abilityEvents() > 0, summary.toString());
        assertTrue(summary.combatEvents() > 0, summary.toString());

        String firstExport = new MatchAiExporter().export(match);
        String secondExport = new MatchAiExporter().export(match);
        assertEquals(firstExport, secondExport, "canonical export must be deterministic");
        assertTrue(firstExport.contains("MOVE#"));
        assertTrue(firstExport.contains("TARGET#"));
        assertTrue(firstExport.contains("TD#"));
        assertTrue(firstExport.contains("TM#"));
        assertTrue(firstExport.contains("GR#"));

        assertRoundTripsSupportedSnapshotState(game);
    }

    private void assertRoundTripsSupportedSnapshotState(GameModel game) {
        List<List<PlayerTurnSnapshot>> snapshots = game.snapshot().stream()
                .map(GameEvent::getTurnSnapshot)
                .filter(snapshot -> !snapshot.isEmpty())
                .map(ArrayList::new)
                .toList();
        assertFalse(snapshots.isEmpty());

        TurnStateDiffer differ = new TurnStateDiffer();
        TurnStateReconstructor reconstructor = new TurnStateReconstructor();
        for (int index = 1; index < snapshots.size(); index++) {
            List<PlayerTurnSnapshot> before = snapshots.get(index - 1);
            List<PlayerTurnSnapshot> after = snapshots.get(index);
            List<PlayerTurnDelta> deltas = differ.diff(before, after);
            List<PlayerTurnSnapshot> reconstructed = reconstructor.apply(before, deltas);
            assertSupportedStateEquals(after, reconstructed, index);
        }
    }

    private void assertSupportedStateEquals(
            List<PlayerTurnSnapshot> expected,
            List<PlayerTurnSnapshot> actual,
            int snapshotIndex) {
        List<PlayerTurnSnapshot> sortedExpected = expected.stream()
                .sorted(Comparator.comparingInt(PlayerTurnSnapshot::getSeatId))
                .toList();
        List<PlayerTurnSnapshot> sortedActual = actual.stream()
                .sorted(Comparator.comparingInt(PlayerTurnSnapshot::getSeatId))
                .toList();
        assertEquals(sortedExpected.size(), sortedActual.size(), "player count at snapshot " + snapshotIndex);
        for (int playerIndex = 0; playerIndex < sortedExpected.size(); playerIndex++) {
            PlayerTurnSnapshot left = sortedExpected.get(playerIndex);
            PlayerTurnSnapshot right = sortedActual.get(playerIndex);
            assertEquals(left.getSeatId(), right.getSeatId(), "seat at snapshot " + snapshotIndex);
            assertEquals(left.getLifeTotal(), right.getLifeTotal(), "life at snapshot " + snapshotIndex);
            assertEquals(left.getHandSize(), right.getHandSize(), "hand size at snapshot " + snapshotIndex);
            assertEquals(left.getKnownHand(), right.getKnownHand(), "known hand at snapshot " + snapshotIndex);
            assertEquals(left.getBattlefield().stream().map(this::permanentState).toList(),
                    right.getBattlefield().stream().map(this::permanentState).toList(),
                    "battlefield membership and counters at snapshot " + snapshotIndex);
        }
    }

    private String permanentState(app.model.game.BoardPermanentSnapshot permanent) {
        return permanent.getLogicalObjectId() + "|" + permanent.getCounters().stream()
                .map(counter -> counter.getType() + "=" + counter.getCount())
                .sorted().toList();
    }

    private Path fixture() throws URISyntaxException {
        return Path.of(CanonicalValidationFixtureTest.class.getResource(
                "/fixtures/validation/mega-strange-game.log.gz").toURI());
    }

    private record ValidationSummary(
            long turns,
            long snapshots,
            long zoneTransitions,
            long damageEvents,
            long targetEvents,
            long abilityEvents,
            long combatEvents) {
        static ValidationSummary from(GameModel game) {
            List<GameEvent> events = game.snapshot();
            return new ValidationSummary(
                    events.stream().map(GameEvent::getTurnNumber).filter(java.util.Objects::nonNull).distinct().count(),
                    events.stream().filter(event -> !event.getTurnSnapshot().isEmpty()).count(),
                    events.stream().filter(event -> event.getZoneTransition() != null).count(),
                    events.stream().filter(event -> event.getPermanentDamage() != null || event.getPlayerLifeChange() != null).count(),
                    events.stream().filter(event -> event.getTargetObservation() != null).count(),
                    events.stream().filter(event -> event.getAbility() != null).count(),
                    events.stream().filter(event -> !event.getAttackers().isEmpty() || !event.getBlockers().isEmpty()).count());
        }
    }
}
