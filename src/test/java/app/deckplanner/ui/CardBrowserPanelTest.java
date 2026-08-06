package app.deckplanner.ui;

import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CardBrowserPanelTest {
    @Test
    void requestsOnlyViewportWindowAndRepaintsCompletedImageOnEdt() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        CompletableFuture<Optional<BufferedImage>> first = new CompletableFuture<>();
        CardBrowserPanel[] holder = new CardBrowserPanel[1];

        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new CardBrowserPanel(
                    new CardGridLayout(100, 160, 10, 10, 10),
                    new ViewportImageWindow(20),
                    card -> {
                        requests.incrementAndGet();
                        return first;
                    });
            holder[0].setSize(120, 500);
            holder[0].setCards(List.of(
                    new CardBrowserPanel.BrowserCard("a", "Alpha"),
                    new CardBrowserPanel.BrowserCard("b", "Beta"),
                    new CardBrowserPanel.BrowserCard("c", "Gamma")));
            holder[0].updateViewport(new Rectangle(0, 0, 120, 100));
        });

        assertEquals(1, requests.get());
        first.complete(Optional.of(new BufferedImage(63, 88, BufferedImage.TYPE_INT_RGB)));
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals(3, holder[0].cards().size());
    }

    @Test
    void selectionSurvivesResponsiveRelayoutAndMutationsRequireEdt() throws Exception {
        CardBrowserPanel[] holder = new CardBrowserPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new CardBrowserPanel(
                    new CardGridLayout(100, 160, 10, 10, 10),
                    new ViewportImageWindow(20),
                    card -> CompletableFuture.completedFuture(Optional.empty()));
            holder[0].setSize(240, 500);
            holder[0].setCards(List.of(
                    new CardBrowserPanel.BrowserCard("a", "Alpha"),
                    new CardBrowserPanel.BrowserCard("b", "Beta")));
            holder[0].dispatchEvent(new java.awt.event.MouseEvent(holder[0],
                    java.awt.event.MouseEvent.MOUSE_PRESSED, 1L, 0, 125, 20, 1, false));
            assertEquals("b", holder[0].selectedCard().orElseThrow().identity());
            holder[0].setSize(500, 500);
            holder[0].invalidate();
            assertEquals("b", holder[0].selectedCard().orElseThrow().identity());
        });

        assertThrows(IllegalStateException.class, () -> holder[0].setCards(List.of()));
    }
}
