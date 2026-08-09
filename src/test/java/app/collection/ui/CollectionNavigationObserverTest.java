package app.collection.ui;

import app.model.log.RawLogEntry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CollectionNavigationObserverTest {
    @Test
    void recognizesTheObservedDecksThenCollectionSequence() {
        List<CollectionNavigationObserver.Step> steps = new ArrayList<>();
        CollectionNavigationObserver observer = new CollectionNavigationObserver(steps::add);

        observer.accept(raw("unrelated DeckBuilder text"));
        observer.accept(raw("[UnityCrossThreadLogger]Client.SceneChange "
                + "{\"fromSceneName\":\"Home\",\"toSceneName\":\"DeckListViewer\","
                + "\"initiator\":\"System\",\"context\":\"Navigate to Deck Manager\"}"));
        observer.accept(raw("[UnityCrossThreadLogger]Client.SceneChange "
                + "{\"fromSceneName\":\"DeckListViewer\",\"toSceneName\":\"DeckBuilder\","
                + "\"initiator\":\"System\",\"context\":\"deck builder\"}"));

        assertEquals(List.of(CollectionNavigationObserver.Step.DECKS_OPEN,
                CollectionNavigationObserver.Step.COLLECTION_OPEN), steps);
    }

    @Test
    void doesNotCallAnOutOfOrderDeckBuilderTransitionACollection() {
        List<CollectionNavigationObserver.Step> steps = new ArrayList<>();
        CollectionNavigationObserver observer = new CollectionNavigationObserver(steps::add);
        observer.accept(raw("[UnityCrossThreadLogger]Client.SceneChange "
                + "{\"fromSceneName\":\"DeckListViewer\",\"toSceneName\":\"DeckBuilder\","
                + "\"initiator\":\"System\",\"context\":\"deck builder\"}"));
        assertEquals(List.of(), steps);
    }

    private static RawLogEntry raw(String text) {
        return new RawLogEntry(1, Instant.EPOCH, text);
    }
}
