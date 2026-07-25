package app.draft.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Owns the immutable pick-state timeline used by the draft browser.
 */
public final class DraftUiModel {
    private final List<DraftPickState> picks = new ArrayList<>();
    private final List<Consumer<DraftUiModel>> listeners = new ArrayList<>();
    private int selectedIndex = -1;

    public synchronized void replaceTimeline(List<DraftPickState> states, boolean selectLatest) {
        picks.clear();
        picks.addAll(states);
        if (picks.isEmpty()) {
            selectedIndex = -1;
        } else if (selectLatest || selectedIndex < 0) {
            selectedIndex = picks.size() - 1;
        } else {
            selectedIndex = Math.min(selectedIndex, picks.size() - 1);
        }
        notifyListeners();
    }

    public synchronized void clear() {
        picks.clear();
        selectedIndex = -1;
        notifyListeners();
    }

    public synchronized boolean previous() {
        if (selectedIndex <= 0) return false;
        selectedIndex--;
        notifyListeners();
        return true;
    }

    public synchronized boolean next() {
        if (selectedIndex < 0 || selectedIndex >= picks.size() - 1) return false;
        selectedIndex++;
        notifyListeners();
        return true;
    }

    public synchronized DraftPickState selected() {
        return selectedIndex < 0 ? null : picks.get(selectedIndex);
    }

    public synchronized int selectedIndex() {
        return selectedIndex;
    }

    public synchronized int size() {
        return picks.size();
    }

    public synchronized List<DraftPickState> picks() {
        return List.copyOf(picks);
    }

    public synchronized void addListener(Consumer<DraftUiModel> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    private void notifyListeners() {
        List<Consumer<DraftUiModel>> copy = List.copyOf(listeners);
        for (Consumer<DraftUiModel> listener : copy) listener.accept(this);
    }
}
