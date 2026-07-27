package app.projection;

import app.model.card.CardInfo;
import app.model.game.BoardPermanentSnapshot;
import app.model.game.CounterState;
import app.model.game.PlayerTurnDelta;
import app.model.game.PlayerTurnSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TurnStateDifferTest {

    @Test
    void computesDeterministicResourceZoneBoardAndCounterChanges() {
        PlayerTurnSnapshot before = player(1, "Me", 20, 5);
        BoardPermanentSnapshot bearBefore = permanent(10, "Bear");
        bearBefore.getCounters().add(counter("Shield", 1));
        before.getBattlefield().add(bearBefore);
        before.getKnownHand().add(card("Island", 1));

        PlayerTurnSnapshot after = player(1, "Me", 17, 4);
        BoardPermanentSnapshot bearAfter = permanent(10, "Bear");
        bearAfter.getCounters().add(counter("Shield", 2));
        after.getBattlefield().add(bearAfter);
        after.getBattlefield().add(permanent(11, "Rat"));
        after.getKnownGraveyard().add(card("Island", 1));

        List<PlayerTurnDelta> deltas = new TurnStateDiffer().diff(List.of(before), List.of(after));

        assertEquals(1, deltas.size());
        PlayerTurnDelta delta = deltas.get(0);
        assertEquals(-3, delta.lifeChange());
        assertEquals(-1, delta.handSizeChange());
        assertEquals(List.of(11L), delta.enteredBattlefield().stream().map(BoardPermanentSnapshot::getLogicalObjectId).toList());
        assertEquals(List.of("Island"), delta.leftKnownHand().stream().map(CardInfo::getName).toList());
        assertEquals(List.of("Island"), delta.enteredKnownGraveyard().stream().map(CardInfo::getName).toList());
        assertEquals(1, delta.counterChanges().get(0).change());
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
