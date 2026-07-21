package app.model.game;

/**
 * Arena-observed damage to a permanent that is relevant to replay analysis.
 *
 * <p>The current projector emits this only for planeswalkers. Creature damage
 * is intentionally omitted unless it becomes lethal through an existing zone
 * transition event.</p>
 */
public record PermanentDamage(
        long targetLogicalObjectId,
        String targetName,
        int amount,
        Long sourceInstanceId,
        String sourceName) {
}
