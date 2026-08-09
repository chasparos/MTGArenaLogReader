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

    @Test void colorSemanticsExposeVisibleSelectedState() throws Exception {
        DeckPlannerFilterModel model = new DeckPlannerFilterModel("standard");
        AtomicReference<DeckPlannerFilterPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panel.set(new DeckPlannerFilterPanel(model, Set.of())));

        AbstractButton printed = findButton(panel.get(), "Printed");
        AbstractButton inclusive = findButton(panel.get(), "Inclusive");
        assertInstanceOf(JRadioButton.class, printed);
        assertInstanceOf(JRadioButton.class, inclusive);
        assertTrue(printed.isSelected());
        assertTrue(inclusive.isSelected());
        assertNotNull(printed.getIcon());
        assertNotNull(printed.getSelectedIcon());
        SwingUtilities.invokeAndWait(() -> {
            model.setColorSource(ColorSource.COLOR_IDENTITY);
            model.setColorMatchMode(ColorMatchMode.EXACT);
        });
        SwingUtilities.invokeAndWait(() -> {});
        assertTrue(findSelectedButton(panel.get(), "Identity"));
        assertTrue(findSelectedButton(panel.get(), "Exact"));
        assertFalse(findSelectedButton(panel.get(), "Printed"));
        assertFalse(findSelectedButton(panel.get(), "Inclusive"));
    }

    @Test void selectedChipsExposeNonColorStateAndKeepStableGeometry() throws Exception {
        DeckPlannerFilterModel model = new DeckPlannerFilterModel("standard");
        SemanticTag mill = new SemanticTag(TagCategory.ACTION, "mill", "Mill");
        AtomicReference<DeckPlannerFilterPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panel.set(new DeckPlannerFilterPanel(model, Set.of(mill))));
        SwingUtilities.invokeAndWait(() -> {
            AbstractButton blue = findButton(panel.get(), "Blue");
            Dimension before = blue.getPreferredSize();
            blue.doClick();
            assertEquals("Selected", blue.getAccessibleContext().getAccessibleDescription());
            assertEquals(before, blue.getPreferredSize());
            assertTrue(blue.isFocusable());
            assertTrue(blue.getMargin().left >= 18, "chip geometry must reserve visible check-mark space");
            assertNotNull(findButton(panel.get(), "Green"));
            assertNotNull(findButton(panel.get(), "Colorless"));
            assertNotNull(findButton(panel.get(), "Phyrexian"));

            AbstractButton tag = findButton(panel.get(), "Mill");
            String label = tag.getText();
            panel.get().setTagCloud(java.util.Map.of(mill, 12L));
            tag.getModel().setRollover(true);
            tag.getModel().setRollover(false);
            assertEquals(label, tag.getText(), "rollover must not erase the painted count");
        });
        assertEquals(Set.of(CardColor.BLUE), model.state().filters().colors());
        assertTrue(panel.get().getScrollableTracksViewportWidth());
    }

    @Test void tagSearchFiltersLargeCloudWithoutChangingChipGeometry() throws Exception {
        DeckPlannerFilterModel model = new DeckPlannerFilterModel("standard");
        SemanticTag rabbit = new SemanticTag(TagCategory.CONCEPT, "rabbit", "Rabbit");
        SemanticTag dragon = new SemanticTag(TagCategory.CONCEPT, "dragon", "Dragon");
        AtomicReference<DeckPlannerFilterPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panel.set(new DeckPlannerFilterPanel(model, Set.of(rabbit, dragon))));
        SwingUtilities.invokeAndWait(() -> {
            JTextField field = findComponent(panel.get(), JTextField.class);
            AbstractButton rabbitChip = findButton(panel.get(), "Rabbit");
            AbstractButton dragonChip = findButton(panel.get(), "Dragon");
            Dimension rabbitSize = rabbitChip.getPreferredSize();
            field.setText("rabbit");
            assertTrue(rabbitChip.isVisible());
            assertFalse(dragonChip.isVisible());
            assertEquals(rabbitSize, rabbitChip.getPreferredSize());
        });
    }

    @Test void manaRangeControlMapsSevenPlusToTheOpenHighModelRange() throws Exception {
        DeckPlannerFilterModel model = new DeckPlannerFilterModel("standard");
        AtomicReference<DeckPlannerFilterPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panel.set(new DeckPlannerFilterPanel(model, Set.of())));
        SwingUtilities.invokeAndWait(() -> {
            ManaValueRangeControl range = findComponent(panel.get(), ManaValueRangeControl.class);
            assertNotNull(range.getAccessibleContext());
            assertEquals("Mana value range", range.getAccessibleContext().getAccessibleName());
            range.setSize(320, 62);
            int xForThree = 18 + Math.round((320 - 36) * (3f / 7f));
            java.awt.event.MouseEvent press = new java.awt.event.MouseEvent(range,
                    java.awt.event.MouseEvent.MOUSE_PRESSED, 1L, 0, xForThree, 23, 1, false);
            for (java.awt.event.MouseListener listener : range.getMouseListeners()) listener.mousePressed(press);
            ManaValueRange value = model.state().filters().manaValueRange();
            assertNotNull(value);
            assertEquals(3.0, value.minimum());
            assertEquals(30.0, value.maximum());
        });
    }


    @Test void resetFiltersLivesInWorkspaceSectionAndResetsModel() throws Exception {
        DeckPlannerFilterModel model = new DeckPlannerFilterModel("standard");
        AtomicReference<DeckPlannerFilterPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panel.set(new DeckPlannerFilterPanel(model, Set.of())));
        SwingUtilities.invokeAndWait(() -> {
            model.toggleColor(CardColor.BLUE);
            AbstractButton reset = findButton(panel.get(), "Reset filters");
            assertNotNull(reset);
            assertSame(findButton(panel.get(), "Candidates only").getParent(), reset.getParent(),
                    "reset belongs in the always-visible Workspace control group");
            reset.doClick();
        });
        assertEquals(CardFilterState.empty(), model.state().filters());
    }

    private boolean findSelectedButton(Container root, String label) {
        return findButton(root, label).isSelected();
    }

    private <T extends Component> T findComponent(Container root, Class<T> type) {
        for (Component component : root.getComponents()) {
            if (type.isInstance(component)) return type.cast(component);
            if (component instanceof Container container) {
                try { return findComponent(container, type); } catch (AssertionError ignored) { }
            }
        }
        throw new AssertionError("Component not found: " + type.getSimpleName());
    }

    private AbstractButton findButton(Container root, String label) {
        for (Component component : root.getComponents()) {
            if (component instanceof AbstractButton button && java.util.Objects.equals(button.getAccessibleContext().getAccessibleName(), label)) return button;
            if (component instanceof Container container) {
                try { return findButton(container, label); } catch (AssertionError ignored) { }
            }
        }
        throw new AssertionError("Button not found: " + label);
    }
}
