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

    public synchronized boolean previousPack() {
        return movePack(-1);
    }

    public synchronized boolean nextPack() {
        return movePack(1);
    }

    public synchronized boolean hasPreviousPack() {
        return adjacentPack(-1) >= 0;
    }

    public synchronized boolean hasNextPack() {
        return adjacentPack(1) >= 0;
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

    private boolean movePack(int direction) {
        int targetPack = adjacentPack(direction);
        if (targetPack < 0) return false;
        DraftPickState current = picks.get(selectedIndex);
        int bestIndex = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int index = 0; index < picks.size(); index++) {
            DraftPickState candidate = picks.get(index);
            if (!candidate.draftId().equals(current.draftId())
                    || candidate.packNumber() != targetPack) continue;
            int distance = Math.abs(
                    candidate.pickNumber() - current.pickNumber());
            if (distance < bestDistance) {
                bestIndex = index;
                bestDistance = distance;
            }
        }
        if (bestIndex < 0) return false;
        selectedIndex = bestIndex;
        notifyListeners();
        return true;
    }

    private int adjacentPack(int direction) {
        if (selectedIndex < 0 || selectedIndex >= picks.size()) return -1;
        DraftPickState current = picks.get(selectedIndex);
        int result = direction < 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (DraftPickState candidate : picks) {
            if (!candidate.draftId().equals(current.draftId())) continue;
            int pack = candidate.packNumber();
            if (direction < 0 && pack < current.packNumber()) {
                result = Math.max(result, pack);
            } else if (direction > 0 && pack > current.packNumber()) {
                result = Math.min(result, pack);
            }
        }
        return direction < 0
                ? result == Integer.MIN_VALUE ? -1 : result
                : result == Integer.MAX_VALUE ? -1 : result;
    }

    private void notifyListeners() {
        List<Consumer<DraftUiModel>> copy = List.copyOf(listeners);
        for (Consumer<DraftUiModel> listener : copy) listener.accept(this);
    }
}
