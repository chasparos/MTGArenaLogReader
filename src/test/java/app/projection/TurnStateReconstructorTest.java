package app.projection;

import app.model.card.CardInfo;
import app.model.game.BoardPermanentSnapshot;
import app.model.game.CounterState;
import app.model.game.PlayerTurnDelta;
import app.model.game.PlayerTurnSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TurnStateReconstructorTest {

    @Test
    void reconstructsTheNextSnapshotFromThePreviousSnapshotAndItsDelta() {
        PlayerTurnSnapshot before = player(1, "Me", 20, 5);
        BoardPermanentSnapshot bearBefore = permanent(10, "Bear");
        bearBefore.getCounters().add(counter("Shield", 1));
        before.getBattlefield().add(bearBefore);
        before.getBattlefield().add(permanent(12, "Scout"));
        before.getKnownHand().add(card("Island", 1));

        PlayerTurnSnapshot after = player(1, "Me", 17, 4);
        BoardPermanentSnapshot bearAfter = permanent(10, "Bear");
        bearAfter.getCounters().add(counter("Shield", 2));
        after.getBattlefield().add(bearAfter);
        after.getBattlefield().add(permanent(11, "Rat"));
        after.getKnownGraveyard().add(card("Island", 1));
        after.getKnownExile().add(card("Charm", 2));

        List<PlayerTurnDelta> deltas = new TurnStateDiffer().diff(List.of(before), List.of(after));
        List<PlayerTurnSnapshot> reconstructed = new TurnStateReconstructor().apply(List.of(before), deltas);

        assertEquals(1, reconstructed.size());
        assertSnapshotEquals(after, reconstructed.get(0));
        assertNotSame(before, reconstructed.get(0));
        assertEquals(20, before.getLifeTotal());
        assertEquals(List.of(10L, 12L), before.getBattlefield().stream()
                .map(BoardPermanentSnapshot::getLogicalObjectId).toList());
    }

    @Test
    void rejectsAContradictoryDeltaInsteadOfFabricatingState() {
        PlayerTurnSnapshot before = player(1, "Me", 20, 5);
        PlayerTurnDelta contradictory = new PlayerTurnDelta(1, "Me", null, null,
                List.of(), List.of(permanent(99, "Missing")),
                List.of(), List.of(), List.of(), List.of(), List.of());

        assertThrows(IllegalArgumentException.class,
                () -> new TurnStateReconstructor().apply(List.of(before), List.of(contradictory)));
    }

    private void assertSnapshotEquals(PlayerTurnSnapshot expected, PlayerTurnSnapshot actual) {
        assertEquals(expected.getSeatId(), actual.getSeatId());
        assertEquals(expected.getPlayerName(), actual.getPlayerName());
        assertEquals(expected.getLifeTotal(), actual.getLifeTotal());
        assertEquals(expected.getPoisonCounters(), actual.getPoisonCounters());
        assertEquals(expected.getHandSize(), actual.getHandSize());
        assertEquals(expected.getBattlefield().stream().map(this::permanentView).toList(),
                actual.getBattlefield().stream().map(this::permanentView).toList());
        assertEquals(expected.getKnownHand().stream().map(this::cardView).sorted().toList(),
                actual.getKnownHand().stream().map(this::cardView).sorted().toList());
        assertEquals(expected.getKnownGraveyard().stream().map(this::cardView).sorted().toList(),
                actual.getKnownGraveyard().stream().map(this::cardView).sorted().toList());
        assertEquals(expected.getKnownExile().stream().map(this::cardView).sorted().toList(),
                actual.getKnownExile().stream().map(this::cardView).sorted().toList());
    }

    private String permanentView(BoardPermanentSnapshot permanent) {
        return permanent.getLogicalObjectId() + "|" + permanent.getName() + "|"
                + permanent.getCounters().stream()
                .map(counter -> counter.getType() + "=" + counter.getCount()).sorted().toList();
    }

    private String cardView(CardInfo card) {
        return card.getArenaId() + "|" + card.getName();
    }

    private PlayerTurnSnapshot player(int seat, String name, int life, int hand) {
        PlayerTurnSnapshot snapshot = new PlayerTurnSnapshot();
        snapshot.setSeatId(seat);
        snapshot.setPlayerName(name);
        snapshot.setLifeTotal(life);
        snapshot.setHandSize(hand);
        return snapshot;
    }

    private BoardPermanentSnapshot permanent(long id, String name) {
        BoardPermanentSnapshot permanent = new BoardPermanentSnapshot();
        permanent.setLogicalObjectId(id);
        permanent.setName(name);
        return permanent;
    }

    private CounterState counter(String type, int count) {
        CounterState counter = new CounterState();
        counter.setType(type);
        counter.setCount(count);
        return counter;
    }

    private CardInfo card(String name, long arenaId) {
        CardInfo card = new CardInfo();
        card.setName(name);
        card.setArenaId(arenaId);
        return card;
    }
}
