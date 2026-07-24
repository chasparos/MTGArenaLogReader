package app.snapshot;

import app.model.game.BoardPermanentSnapshot;
import app.model.event.GameEvent;
import app.model.game.PlayerTurnSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Passive semantic monitor. It listens to projected GameEvents and attaches the
 * latest canonical battlefield observation to start-of-turn snapshots.
 * <p><strong>Architectural role:</strong> This type belongs to the snapshot projection boundary and derives stable board summaries from canonical game state.</p>
 */
public final class BoardStateMonitor {
    private final List<BoardPermanentSnapshot> current = new ArrayList<>();

    public void accept(List<GameEvent> events) {
        for (GameEvent event : events) {
            if (!event.getBattlefieldObservation().isEmpty()) {
                current.clear();
                current.addAll(copy(event.getBattlefieldObservation()));
            }
            if (!event.getTurnSnapshot().isEmpty()) {
                for (PlayerTurnSnapshot player : event.getTurnSnapshot()) {
                    player.getBattlefield().clear();
                    /*
                     * Build the player's board from roots they control, then
                     * include every attachment whose host is already present.
                     * Auras such as Pacifism are controlled by the opponent,
                     * so filtering by controller before resolving attachment
                     * relationships incorrectly displayed them as opponent
                     * roots instead of beneath the enchanted creature.
                     */
                    List<BoardPermanentSnapshot> playerBoard =
                            battlefieldForController(player.getSeatId());
                    playerBoard.stream()
                            .map(this::copy)
                            .forEach(player.getBattlefield()::add);
                }
            }
        }
    }

    private List<BoardPermanentSnapshot> battlefieldForController(int controllerSeatId) {
        java.util.Set<Long> included = new java.util.LinkedHashSet<>();

        current.stream()
                .filter(permanent -> permanent.getAttachedToLogicalObjectId() == null)
                .filter(permanent -> permanent.getControllerSeatId() == controllerSeatId)
                .map(BoardPermanentSnapshot::getLogicalObjectId)
                .forEach(included::add);

        boolean changed;
        do {
            changed = false;
            for (BoardPermanentSnapshot permanent : current) {
                Long hostId = permanent.getAttachedToLogicalObjectId();
                if (hostId != null
                        && included.contains(hostId)
                        && included.add(permanent.getLogicalObjectId())) {
                    changed = true;
                }
            }
        } while (changed);

        return current.stream()
                .filter(permanent -> included.contains(permanent.getLogicalObjectId()))
                .toList();
    }

    public void reset() {
        current.clear();
    }

    private List<BoardPermanentSnapshot> copy(List<BoardPermanentSnapshot> source) {
        return source.stream().map(this::copy).toList();
    }

    private BoardPermanentSnapshot copy(BoardPermanentSnapshot source) {
        BoardPermanentSnapshot copy = new BoardPermanentSnapshot();
        copy.setLogicalObjectId(source.getLogicalObjectId());
        copy.setOwnerSeatId(source.getOwnerSeatId());
        copy.setControllerSeatId(source.getControllerSeatId());
        copy.setName(source.getName());
        copy.setCard(source.getCard());
        copy.setTapped(source.getTapped());
        copy.setPower(source.getPower());
        copy.setToughness(source.getToughness());
        copy.setAttachedToLogicalObjectId(source.getAttachedToLogicalObjectId());
        source.getCounters().forEach(counter -> copy.getCounters().add(counter.copy()));
        copy.getUnlockedRoomHalves().addAll(source.getUnlockedRoomHalves());
        copy.getEvergreenAbilities().addAll(source.getEvergreenAbilities());
        return copy;
    }
}
