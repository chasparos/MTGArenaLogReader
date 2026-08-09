package app.collection.ui;

import app.collection.CollectionUpdate;
import app.replay.ReplayCardChip;
import app.model.card.CardInfo;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CollectionSyncPanelTest {
    @Test
    void presentsProtocolCardsInApplicationLanguage() throws Exception {
        CollectionSyncPanel[] panel = new CollectionSyncPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new CollectionSyncPanel(observer -> new CollectionUpdate.Session() {
                @Override public void respond(CollectionUpdate.Response response) {
                    observer.onEvent(new CollectionUpdate.CardsRequired(
                            "Choose cards you own", 2, 5,
                            List.of(new CollectionUpdate.CardOption(
                                    67692, "Ajani's Welcome", "M19", "Core Set 2019", "6"))));
                }
                @Override public void cancel() { }
            });
            findButton(panel[0], "Let’s get started").doClick();
            JTextField search = find(panel[0], JTextField.class);
            search.setText("Ajani");
            search.getActionMap().get("autocomplete.accept").actionPerformed(null);
        });

        String text = allText(panel[0]);
        ReplayCardChip chip = find(panel[0], ReplayCardChip.class);
        assertEquals("Ajani's Welcome", chip.card().getName());
        assertEquals("Core Set 2019", chip.card().getSetName());
        assertTrue(text.contains("Find a card"));
        assertTrue(text.contains("Start typing"));
        assertFalse(text.contains("Add card"));
        assertFalse(text.toLowerCase().contains("anchor"));
        assertFalse(text.toLowerCase().contains("memory region"));
    }

    @Test
    void completionReplacesProgressAndExplainsDistinctCards() throws Exception {
        CollectionSyncPanel[] panel = new CollectionSyncPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new CollectionSyncPanel(observer -> new CollectionUpdate.Session() {
                private int continues;
                @Override public void respond(CollectionUpdate.Response response) {
                    if (response instanceof CollectionUpdate.Continue && continues++ == 0) {
                        observer.onEvent(new CollectionUpdate.Completed(true,
                                new CollectionUpdate.Summary(3322, 150, 347,
                                        java.util.Map.of(), java.util.Map.of(), java.util.Map.of()), "Done"));
                    }
                }
                @Override public void cancel() { }
            });
            findButton(panel[0], "Let’s get started").doClick();
        });

        String text = allText(panel[0]);
        assertTrue(text.contains("Out of 3322 Arena cards"));
        assertTrue(text.contains("150 different cards"));
        assertTrue(text.contains("347 copies altogether"));
        assertTrue(text.contains("real cardboard"));
        assertTrue(text.contains("625 g"));
        assertTrue(text.contains("Your collection is ready!"));
    }

    @Test
    void textFieldOwnsSuggestionKeyboardNavigation() throws Exception {
        CollectionSyncPanel[] panel = new CollectionSyncPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new CollectionSyncPanel(observer -> new CollectionUpdate.Session() {
                @Override public void respond(CollectionUpdate.Response response) {
                    observer.onEvent(new CollectionUpdate.CardsRequired("Choose cards", 2, 5, List.of(
                            new CollectionUpdate.CardOption(104942, "Bruce Banner", "MSH", "Marvel", "49"),
                            new CollectionUpdate.CardOption(92156, "Balemurk Leech", "DSK", "Duskmourn", "84"))));
                }
                @Override public void cancel() { }
            });
            findButton(panel[0], "Let’s get started").doClick();
            JTextField search = find(panel[0], JTextField.class);
            search.setText("b");
            assertTrue(search.getActionMap().get("autocomplete.down") != null);
            search.getActionMap().get("autocomplete.down").actionPerformed(null);
            search.getActionMap().get("autocomplete.accept").actionPerformed(null);
        });

        assertEquals("Balemurk Leech", find(panel[0], ReplayCardChip.class).card().getName());
    }

    @Test
    void selectedRowUsesApplicationSuppliedCachedCardPresentation() throws Exception {
        CardInfo cached = new CardInfo();
        cached.setArenaId(67692L);
        cached.setName("Ajani's Welcome");
        cached.setManaCost("{W}");
        CollectionSyncPanel[] panel = new CollectionSyncPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            CollectionUpdate update = observer -> new CollectionUpdate.Session() {
                @Override public void respond(CollectionUpdate.Response response) {
                    observer.onEvent(new CollectionUpdate.CardsRequired("Choose cards", 2, 5, List.of(
                            new CollectionUpdate.CardOption(67692, "Ajani's Welcome", "M19",
                                    "Core Set 2019", "6"))));
                }
                @Override public void cancel() { }
            };
            panel[0] = new CollectionSyncPanel(update,
                    CollectionSyncPanel.CardArtworkSource.none(), option -> java.util.Optional.of(cached));
            findButton(panel[0], "Let’s get started").doClick();
            JTextField search = find(panel[0], JTextField.class);
            search.setText("Ajani");
            search.getActionMap().get("autocomplete.accept").actionPerformed(null);
        });
        assertSame(cached, find(panel[0], ReplayCardChip.class).card());
    }

    private static JButton findButton(Container root, String text) {
        for (Component component : root.getComponents()) {
            if (component instanceof JButton button && text.equals(button.getText())) return button;
            if (component instanceof Container child) {
                JButton found = findButtonOrNull(child, text);
                if (found != null) return found;
            }
        }
        throw new AssertionError("Button not found: " + text);
    }

    private static JButton findButtonOrNull(Container root, String text) {
        for (Component component : root.getComponents()) {
            if (component instanceof JButton button && text.equals(button.getText())) return button;
            if (component instanceof Container child) {
                JButton found = findButtonOrNull(child, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static String allText(Container root) {
        StringBuilder text = new StringBuilder();
        for (Component component : root.getComponents()) {
            if (component instanceof JLabel label) text.append(label.getText()).append('\n');
            if (component instanceof JTextArea area) text.append(area.getText()).append('\n');
            if (component instanceof AbstractButton button) text.append(button.getText()).append('\n');
            if (component instanceof Container child) text.append(allText(child));
        }
        return text.toString();
    }

    private static <T extends Component> T find(Container root, Class<T> type) {
        for (Component component : root.getComponents()) {
            if (type.isInstance(component)) return type.cast(component);
            if (component instanceof Container child) {
                try { return find(child, type); } catch (AssertionError ignored) { }
            }
        }
        throw new AssertionError("Component not found: " + type.getSimpleName());
    }
}
