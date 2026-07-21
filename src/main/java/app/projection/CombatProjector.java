package app.projection;

import app.model.card.CardInfo;
import app.model.game.CombatAttackAssignment;
import app.model.game.CombatBlockAssignment;
import app.model.event.GameEvent;
import app.model.game.GameObjectState;
import app.model.game.GameState;
import app.model.log.LogMessageInterface;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Projects stable combat declarations from canonical battlefield state.
 *
 * <p>This collaborator owns declaration stability, duplicate suppression, and
 * combat assignment construction. It does not parse Arena messages or mutate
 * object state.</p>
 */
final class CombatProjector {
    private final GameState state;
    private final ObjectIdentityTracker objectIdentityTracker;
    private final Function<Integer, String> zoneType;
    private final Function<Integer, String> playerName;
    private final BiFunction<GameObjectState, Map<Long, CardInfo>, String> objectDisplayName;
    private final BiFunction<Long, Map<Long, CardInfo>, String> targetDisplayName;
    private final BiFunction<LogMessageInterface, String, GameEvent> eventFactory;

    CombatProjector(GameState state,
                    ObjectIdentityTracker objectIdentityTracker,
                    Function<Integer, String> zoneType,
                    Function<Integer, String> playerName,
                    BiFunction<GameObjectState, Map<Long, CardInfo>, String> objectDisplayName,
                    BiFunction<Long, Map<Long, CardInfo>, String> targetDisplayName,
                    BiFunction<LogMessageInterface, String, GameEvent> eventFactory) {
        this.state = state;
        this.objectIdentityTracker = objectIdentityTracker;
        this.zoneType = zoneType;
        this.playerName = playerName;
        this.objectDisplayName = objectDisplayName;
        this.targetDisplayName = targetDisplayName;
        this.eventFactory = eventFactory;
    }

    void projectDeclarations(LogMessageInterface source,
                             Map<Long, CardInfo> cards,
                             List<GameEvent> result) {
        String step = state.getStep() == null ? "" : state.getStep();
        boolean attackersStable = step.contains("DeclareBlock");
        boolean blockersStable = step.contains("DeclareBlock")
                || step.contains("CombatDamage")
                || step.contains("EndCombat");

        List<GameObjectState> battlefield = state.getObjects().values().stream()
                .filter(objectIdentityTracker::isCurrent)
                .filter(this::isOnBattlefield)
                .toList();

        List<GameObjectState> attackers = battlefield.stream()
                .filter(this::isAttacking)
                .filter(a -> state.getActivePlayerSeat() == null
                        || a.getControllerSeatId() == state.getActivePlayerSeat())
                .sorted(Comparator.comparingLong(GameObjectState::getLogicalObjectId))
                .toList();

        if (attackersStable && !attackers.isEmpty()) {
            String signature = state.getTurnNumber() + ":"
                    + attackers.stream()
                    .map(a -> a.getLogicalObjectId() + ">" + a.getAttackTargetId())
                    .collect(Collectors.joining("|"));
            if (!signature.equals(state.getEmittedAttackSignature())) {
                result.add(attackersDeclaredEvent(source, attackers, cards));
                state.setEmittedAttackSignature(signature);
                state.setEmittedBlockSignature("");
            }
        }

        List<GameObjectState> blockers = battlefield.stream()
                .filter(this::isBlocking)
                .sorted(Comparator.comparingLong(GameObjectState::getLogicalObjectId))
                .toList();

        if (blockersStable && !blockers.isEmpty()) {
            String signature = state.getTurnNumber() + ":"
                    + blockers.stream()
                    .map(b -> b.getLogicalObjectId() + ">"
                            + b.getBlockedAttackerIds().stream()
                            .map(objectIdentityTracker::logicalIdOf)
                            .sorted()
                            .map(String::valueOf)
                            .collect(Collectors.joining(",")))
                    .collect(Collectors.joining("|"));
            if (!signature.equals(state.getEmittedBlockSignature())) {
                result.add(blockersDeclaredEvent(source, blockers, cards));
                state.setEmittedBlockSignature(signature);
            }
        }
    }

    private boolean isOnBattlefield(GameObjectState object) {
        return "Battlefield".equals(zoneType.apply(object.getSemanticZoneId()));
    }

    private boolean isAttacking(GameObjectState object) {
        return object.getAttackState() != null
                && (object.getAttackState().endsWith("_Attacking")
                || object.getAttackState().endsWith("_Declared"))
                && object.getAttackTargetId() != null;
    }

    private boolean isBlocking(GameObjectState object) {
        return object.getBlockState() != null
                && object.getBlockState().endsWith("_Blocking")
                && !object.getBlockedAttackerIds().isEmpty();
    }

    private GameEvent attackersDeclaredEvent(LogMessageInterface source,
                                             List<GameObjectState> attackers,
                                             Map<Long, CardInfo> cards) {
        int attackingSeat = attackers.get(0).getControllerSeatId();
        Map<Long, List<GameObjectState>> byTarget = new LinkedHashMap<>();
        for (GameObjectState attacker : attackers) {
            byTarget.computeIfAbsent(attacker.getAttackTargetId(), ignored -> new ArrayList<>())
                    .add(attacker);
        }

        String groups = byTarget.entrySet().stream().map(entry -> {
            String target = targetDisplayName.apply(entry.getKey(), cards);
            String names = entry.getValue().stream()
                    .map(a -> combatDisplayName(a, cards))
                    .collect(Collectors.joining(", "));
            return target + " with " + names;
        }).collect(Collectors.joining("; "));

        GameEvent event = eventFactory.apply(source, playerName.apply(attackingSeat) + " attacks " + groups);
        event.setPhase("Phase_Combat");
        event.setStep("Step_DeclareAttack");
        for (GameObjectState attacker : attackers) {
            long targetId = attacker.getAttackTargetId();
            event.getAttackers().add(new CombatAttackAssignment(
                    attacker.getLogicalObjectId(),
                    attacker.getInstanceId(),
                    combatDisplayName(attacker, cards),
                    attacker.getControllerSeatId(),
                    targetId,
                    targetDisplayName.apply(targetId, cards)));
            if (attacker.getCard() != null && !event.getCards().contains(attacker.getCard())) {
                event.getCards().add(attacker.getCard());
            }
        }
        return event;
    }

    private GameEvent blockersDeclaredEvent(LogMessageInterface source,
                                            List<GameObjectState> blockers,
                                            Map<Long, CardInfo> cards) {
        int defendingSeat = blockers.get(0).getControllerSeatId();
        List<String> clauses = new ArrayList<>();
        GameEvent event = eventFactory.apply(source, "");
        event.setPhase("Phase_Combat");
        event.setStep("Step_DeclareBlock");

        for (GameObjectState blocker : blockers) {
            List<Long> logicalIds = new ArrayList<>();
            List<String> attackerNames = new ArrayList<>();
            for (long attackerInstanceId : blocker.getBlockedAttackerIds()) {
                GameObjectState attacker = state.getObjects().get(attackerInstanceId);
                long logicalId = objectIdentityTracker.logicalIdOf(attackerInstanceId);
                logicalIds.add(logicalId);
                attackerNames.add(attacker == null
                        ? targetDisplayName.apply(attackerInstanceId, cards)
                        : objectDisplayName.apply(attacker, cards));
            }
            String blockerName = objectDisplayName.apply(blocker, cards);
            clauses.add(blockerName + " blocks " + String.join(", ", attackerNames));
            event.getBlockers().add(new CombatBlockAssignment(
                    blocker.getLogicalObjectId(),
                    blocker.getInstanceId(),
                    blockerName,
                    blocker.getControllerSeatId(),
                    logicalIds,
                    attackerNames));
            if (blocker.getCard() != null && !event.getCards().contains(blocker.getCard())) {
                event.getCards().add(blocker.getCard());
            }
        }

        event.setText(playerName.apply(defendingSeat) + " blocks: " + String.join("; ", clauses));
        return event;
    }

    private String combatDisplayName(GameObjectState object, Map<Long, CardInfo> cards) {
        String name = objectDisplayName.apply(object, cards);
        if (object.getPower() != null && object.getToughness() != null) {
            return name + " (" + object.getPower() + "/" + object.getToughness() + ")";
        }
        return name;
    }
}
