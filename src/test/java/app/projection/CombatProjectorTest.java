package app.projection;

import app.model.card.CardInfo;
import app.model.event.GameEvent;
import app.model.game.GameObjectState;
import app.model.game.GameState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatProjectorTest {

    @Test
    void emitsStableAttackersOnlyOnce() {
        GameState state = new GameState();
        state.setStep("Step_DeclareBlock");
        state.setTurnNumber(4);
        state.setActivePlayerSeat(1);
        GameObjectState attacker = object(1001, 1, "AttackState_Attacking", "BlockState_None");
        attacker.setAttackTargetId(2L);
        state.getObjects().put(attacker.getInstanceId(), attacker);

        CombatProjector projector = projector(state);
        List<GameEvent> events = new ArrayList<>();

        projector.projectDeclarations(null, Map.of(), events);
        projector.projectDeclarations(null, Map.of(), events);

        assertEquals(1, events.size());
        assertEquals(1, events.get(0).getAttackers().size());
        assertEquals(1001L, events.get(0).getAttackers().get(0).attackerLogicalId());
    }

    @Test
    void recordsBlockersAgainstLogicalAttackerIdentity() {
        GameState state = new GameState();
        state.setStep("Step_DeclareBlock");
        state.setTurnNumber(5);
        state.setActivePlayerSeat(1);
        state.getLogicalIds().put(2001L, 1001L);

        GameObjectState attacker = object(2001, 1, "AttackState_Attacking", "BlockState_None");
        attacker.setLogicalObjectId(1001L);
        attacker.setAttackTargetId(2L);
        state.getObjects().put(2001L, attacker);

        GameObjectState blocker = object(3001, 2, "AttackState_None", "BlockState_Blocking");
        blocker.getBlockedAttackerIds().add(2001L);
        state.getObjects().put(3001L, blocker);

        List<GameEvent> events = new ArrayList<>();
        projector(state).projectDeclarations(null, Map.of(), events);

        assertEquals(2, events.size());
        assertEquals(List.of(1001L), events.get(1).getBlockers().get(0).attackerLogicalIds());
    }

    @Test
    void waitsForAStableCombatBoundary() {
        GameState state = new GameState();
        state.setStep("Step_DeclareAttack");
        state.setTurnNumber(2);
        state.setActivePlayerSeat(1);
        GameObjectState attacker = object(1001, 1, "AttackState_Attacking", "BlockState_None");
        attacker.setAttackTargetId(2L);
        state.getObjects().put(1001L, attacker);

        List<GameEvent> events = new ArrayList<>();
        projector(state).projectDeclarations(null, Map.of(), events);

        assertTrue(events.isEmpty());
    }

    private static CombatProjector projector(GameState state) {
        ObjectIdentityTracker identities = new ObjectIdentityTracker(state);
        return new CombatProjector(
                state,
                identities,
                ignored -> "Battlefield",
                seat -> "Player " + seat,
                (object, cards) -> "Object " + object.getInstanceId(),
                (id, cards) -> "Target " + id,
                (source, text) -> {
                    GameEvent event = new GameEvent();
                    event.setText(text);
                    return event;
                });
    }

    private static GameObjectState object(long id,
                                          int controller,
                                          String attackState,
                                          String blockState) {
        GameObjectState object = new GameObjectState();
        object.setInstanceId(id);
        object.setLogicalObjectId(id);
        object.setControllerSeatId(controller);
        object.setSemanticZoneId(1);
        object.setAttackState(attackState);
        object.setBlockState(blockState);
        object.setPower(2);
        object.setToughness(2);
        CardInfo card = new CardInfo();
        card.setName("Card " + id);
        object.setCard(card);
        return object;
    }
}
