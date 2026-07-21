package app.model.game;

/**
 * Structured semantic change to a player's life total.
 *
 * <p>Damage is distinguished from other life loss only when Arena supplies a
 * correlated damage annotation. Positive changes are life gain.</p>
 */
public record PlayerLifeChange(
        Kind kind,
        int seatId,
        String playerName,
        int amount,
        int previousLife,
        int currentLife,
        Long sourceInstanceId,
        String sourceName) {

    public enum Kind {
        DAMAGE,
        LIFE_GAIN,
        LIFE_LOSS
    }
}
