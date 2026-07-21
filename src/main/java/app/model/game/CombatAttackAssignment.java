package app.model.game;

/** Objective Arena combat assignment for one attacker.
 * <p><strong>Architectural role:</strong> This type belongs to the canonical per-game state model used by projection before semantic events are presented.</p>
 */
public record CombatAttackAssignment(
        long attackerLogicalId,
        long attackerInstanceId,
        String attackerName,
        int controllerSeatId,
        long targetId,
        String targetName
) {}
