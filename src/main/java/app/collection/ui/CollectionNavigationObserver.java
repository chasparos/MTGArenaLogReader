package app.collection.ui;

import app.model.log.RawLogEntry;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Interprets the small, evidence-backed portion of Arena navigation needed by the collection
 * wizard. It consumes the application's existing raw Player.log stream; it never opens or tails
 * the file itself and has no dependency on collection extraction.
 */
public final class CollectionNavigationObserver implements Consumer<RawLogEntry> {
    public enum Step { DECKS_OPEN, COLLECTION_OPEN }

    private static final String SCENE_CHANGE = "Client.SceneChange";
    private static final String DECKS_TRANSITION =
            "\"fromSceneName\":\"Home\",\"toSceneName\":\"DeckListViewer\"";
    private static final String COLLECTION_TRANSITION =
            "\"fromSceneName\":\"DeckListViewer\",\"toSceneName\":\"DeckBuilder\"";

    private final Consumer<Step> listener;
    private final AtomicReference<Step> latest = new AtomicReference<>();

    public CollectionNavigationObserver(Consumer<Step> listener) {
        this.listener = Objects.requireNonNull(listener);
    }

    @Override public void accept(RawLogEntry entry) {
        if (entry == null || entry.getText() == null) return;
        String text = entry.getText();
        if (!text.contains(SCENE_CHANGE)) return;
        if (text.contains(DECKS_TRANSITION)) publish(Step.DECKS_OPEN);
        else if (text.contains(COLLECTION_TRANSITION) && latest.get() == Step.DECKS_OPEN) {
            publish(Step.COLLECTION_OPEN);
        }
    }

    public Step latest() {
        return latest.get();
    }

    public void reset() {
        latest.set(null);
    }

    private void publish(Step step) {
        Step previous = latest.getAndSet(step);
        if (previous != step) listener.accept(step);
    }
}
