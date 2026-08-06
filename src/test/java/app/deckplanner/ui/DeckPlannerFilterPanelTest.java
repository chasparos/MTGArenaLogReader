package app.deckplanner.ui;

import app.deckplanner.filter.*;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DeckPlannerFilterPanelTest {
    @Test void controlsReflectExternalModelChangesOnEdt() throws Exception {
        DeckPlannerFilterModel model = new DeckPlannerFilterModel("standard");
        SemanticTag tag = new SemanticTag(TagCategory.ACTION, "mill", "Mill");
        AtomicReference<DeckPlannerFilterPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panel.set(new DeckPlannerFilterPanel(model, Set.of(tag))));

        model.toggleColor(CardColor.BLUE);
        model.toggleTag(tag);
        SwingUtilities.invokeAndWait(() -> {});

        assertTrue(findSelectedButton(panel.get(), "Blue"));
        assertTrue(findSelectedButton(panel.get(), "Mill"));
    }

    @Test void selectedChipsExposeNonColorStateAndKeyboardFocus() throws Exception {
        DeckPlannerFilterModel model = new DeckPlannerFilterModel("standard");
        AtomicReference<DeckPlannerFilterPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panel.set(new DeckPlannerFilterPanel(model, Set.of())));
        SwingUtilities.invokeAndWait(() -> {
            AbstractButton blue = findButton(panel.get(), "Blue");
            blue.doClick();
            assertTrue(blue.getText().startsWith("✓ "));
            assertTrue(blue.isFocusable());
        });
        assertEquals(Set.of(CardColor.BLUE), model.state().filters().colors());
    }

    private boolean findSelectedButton(Container root, String label) {
        return findButton(root, label).isSelected();
    }

    private AbstractButton findButton(Container root, String label) {
        for (Component component : root.getComponents()) {
            if (component instanceof AbstractButton button && button.getText().replace("✓ ", "").equals(label)) return button;
            if (component instanceof Container container) {
                try { return findButton(container, label); } catch (AssertionError ignored) { }
            }
        }
        throw new AssertionError("Button not found: " + label);
    }
}
