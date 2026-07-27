package app.replay;

import java.util.Collection;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Owns turn-selection semantics independently of Swing hit testing and menus.
 */
final class ReplayTurnSelection {
    private final NavigableSet<Integer> selected = new TreeSet<>();
    private Integer anchor;

    Set<Integer> snapshot() {
        return Set.copyOf(selected);
    }

    boolean isEmpty() {
        return selected.isEmpty();
    }

    boolean contains(int turn) {
        return selected.contains(turn);
    }

    int size() {
        return selected.size();
    }

    void clear() {
        selected.clear();
        anchor = null;
    }

    void selectOnly(int turn) {
        selected.clear();
        selected.add(turn);
        anchor = turn;
    }

    void selectFromPointer(int turn, boolean extendRange, boolean toggle) {
        if (extendRange && anchor != null) {
            int from = Math.min(anchor, turn);
            int to = Math.max(anchor, turn);
            if (!toggle) selected.clear();
            for (int value = from; value <= to; value++) selected.add(value);
            return;
        }
        if (toggle) {
            if (!selected.remove(turn)) selected.add(turn);
            anchor = turn;
            return;
        }
        selectOnly(turn);
    }

    String compactLabel() {
        return compact(selected);
    }

    static String compact(Collection<Integer> turns) {
        if (turns.isEmpty()) return "";
        NavigableSet<Integer> ordered = new TreeSet<>(turns);
        if (ordered.size() == 1) return Integer.toString(ordered.first());
        return ordered.first() + "\u2013" + ordered.last();
    }
}
