package app.model.game;

import java.util.List;

/** Objective Arena combat assignment for one blocker.
 * <p><strong>Architectural role:</strong> This type belongs to the canonical per-game state model used by projection before semantic events are presented.</p>
 */
public record CombatBlockAssignment(
        long blockerLogicalId,
        long blockerInstanceId,
        String blockerName,
        int controllerSeatId,
        List<Long> attackerLogicalIds,
        List<String> attackerNames
) {
    public CombatBlockAssignment {
        attackerLogicalIds = List.copyOf(attackerLogicalIds);
        attackerNames = List.copyOf(attackerNames);
    }
}
