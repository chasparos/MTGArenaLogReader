package app.projection;

import app.model.game.CounterState;
import app.model.game.GameObjectState;

/**
 * Applies Arena counter observations to reconstructed permanent state.
 *
 * <p><strong>Architectural role:</strong> This projection collaborator owns
 * counter identity, naming, and count mutation for game objects. It does not
 * interpret annotations, emit events, or decide which object an observation
 * targets; those responsibilities remain with {@link GameEventProjector}.</p>
 */
final class CounterProjector {

    void applyDelta(GameObjectState object, int counterType, int delta) {
        CounterState counter = counterState(object, counterType);
        int current = Math.max(0, counter.getCount() + delta);
        if (current == 0) {
            object.getCounters().remove(counter);
        } else {
            counter.setCount(current);
        }
    }

    void setCount(GameObjectState object, int counterType, int count) {
        CounterState counter = counterState(object, counterType);
        if (count <= 0) {
            object.getCounters().remove(counter);
        } else {
            counter.setCount(count);
        }
    }

    String counterTypeName(int counterType) {
        return switch (counterType) {
            case 1 -> "+1/+1";
            case 2 -> "-1/-1";
            case 3 -> "Poison";
            case 108 -> "Lore";
            default -> "Counter#" + counterType;
        };
    }

    private CounterState counterState(GameObjectState object, int counterType) {
        String name = counterTypeName(counterType);
        for (CounterState counter : object.getCounters()) {
            if (counter.getArenaType() == counterType || name.equals(counter.getType())) {
                counter.setArenaType(counterType);
                counter.setType(name);
                return counter;
            }
        }

        CounterState counter = new CounterState();
        counter.setArenaType(counterType);
        counter.setType(name);
        object.getCounters().add(counter);
        return counter;
    }
}
