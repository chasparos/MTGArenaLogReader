package app.model.event;

/**
 * Stable semantic reference to an Arena game object or player.
 *
 * <p>The logical object id survives Arena instance-id replacement within one
 * game. A negative logical id means that no stable object identity was
 * available. Player references use {@code playerSeat} instead.</p>
 */
public record ObjectReference(
        long logicalObjectId,
        long arenaInstanceId,
        long arenaGrpId,
        String name,
        Integer playerSeat,
        String playerName) {

    public boolean isPlayer() {
        return playerSeat != null;
    }
}
