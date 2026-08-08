package app.ui;

import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CardCollectionSurfaceTest {
    @Test void ownsComponentSelectionAndProjectScrollSurface() throws Exception {
        AtomicReference<CardCollectionSurface> surfaceRef = new AtomicReference<>();
        AtomicReference<Optional<String>> selection = new AtomicReference<>(Optional.empty());

        SwingUtilities.invokeAndWait(() -> {
            CardCollectionSurface surface = new CardCollectionSurface();
            surface.setSelectionListener(selection::set);
            surface.setRows(List.of(row("a"), row("b")));
            surfaceRef.set(surface);

            JComponent first = surface.rowComponents().getFirst();
            first.dispatchEvent(new MouseEvent(first, MouseEvent.MOUSE_PRESSED,
                    System.currentTimeMillis(), 0, 3, 3, 1, false));
        });

        assertEquals(Optional.of("a"), selection.get());
        assertEquals(Optional.of("a"), surfaceRef.get().selectedIdentity());

        AtomicReference<JScrollPane> scroll = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> scroll.set(surfaceRef.get().createScrollPane()));
        assertInstanceOf(AppScrollBarUI.class, scroll.get().getVerticalScrollBar().getUI());
    }


    @Test void groupedSurfaceKeepsProjectOwnedRowsAndTracksViewportWidth() throws Exception {
        AtomicReference<CardCollectionSurface> surfaceRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            CardCollectionSurface surface = new CardCollectionSurface();
            surface.setGroups(List.of(
                    new CardCollectionSurface.Group(
                            "creatures", "Creatures", List.of(row("a"), row("b"))),
                    new CardCollectionSurface.Group(
                            "lands", "Nonbasic Lands", List.of(row("c")))));
            surfaceRef.set(surface);
        });

        CardCollectionSurface surface = surfaceRef.get();
        assertEquals(List.of("a", "b", "c"), surface.identities());
        assertTrue(surface.getScrollableTracksViewportWidth());
        assertEquals(3, surface.rowComponents().size());
        assertNotNull(findNamed(surface, "card-collection-group-creatures"));
        assertNotNull(findNamed(surface, "card-collection-group-lands"));
    }

    private static CardCollectionSurface.Row row(String identity) {
        JPanel component = new JPanel();
        return new CardCollectionSurface.Row() {
            @Override public String identity() { return identity; }
            @Override public JComponent component() { return component; }
            @Override public void setSelected(boolean selected) {
                component.putClientProperty("selected", selected);
            }
        };
    }
    private static JComponent findNamed(Container root, String name) {
        for (java.awt.Component child : root.getComponents()) {
            if (child instanceof JComponent swing && name.equals(swing.getName())) return swing;
            if (child instanceof Container container) {
                JComponent nested = findNamed(container, name);
                if (nested != null) return nested;
            }
        }
        return null;
    }

}
