package app.deckplanner.ui;

import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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


    @Test
    void cancelsRequestsThatLeaveViewportAndIgnoresLateCompletion() throws Exception {
        CompletableFuture<Optional<BufferedImage>> first = new CompletableFuture<>();
        CompletableFuture<Optional<BufferedImage>> second = new CompletableFuture<>();
        AtomicInteger sequence = new AtomicInteger();
        CardBrowserPanel[] holder = new CardBrowserPanel[1];

        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new CardBrowserPanel(
                    new CardGridLayout(100, 100, 10, 10, 10),
                    new ViewportImageWindow(0),
                    card -> sequence.getAndIncrement() == 0 ? first : second);
            holder[0].setSize(120, 500);
            holder[0].setCards(List.of(
                    new CardBrowserPanel.BrowserCard("a", "Alpha"),
                    new CardBrowserPanel.BrowserCard("b", "Beta")));
            holder[0].updateViewport(new Rectangle(0, 0, 120, 100));
            holder[0].updateViewport(new Rectangle(0, 170, 120, 100));
        });

        assertTrue(first.isCancelled(), "off-window request should be cancelled");
        assertFalse(second.isCancelled());
        first.complete(Optional.of(new BufferedImage(63, 88, BufferedImage.TYPE_INT_RGB)));
        second.complete(Optional.empty());
        SwingUtilities.invokeAndWait(() -> { });
    }

    @Test
    void selectionAndFocusFollowStableIdentityWhenCardsReorder() throws Exception {
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
            holder[0].setCards(List.of(
                    new CardBrowserPanel.BrowserCard("b", "Beta"),
                    new CardBrowserPanel.BrowserCard("a", "Alpha")));
            assertEquals(0, holder[0].selectedIndex());
            assertEquals("b", holder[0].selectedCard().orElseThrow().identity());
        });
    }


    @Test
    void supportsIdentityBasedMultiSelectionAndCandidateMembership() throws Exception {
        CardBrowserPanel[] holder = new CardBrowserPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new CardBrowserPanel(
                    new CardGridLayout(100, 160, 10, 10, 10),
                    new ViewportImageWindow(20),
                    card -> CompletableFuture.completedFuture(Optional.empty()));
            holder[0].setSize(240, 500);
            holder[0].setCards(List.of(
                    new CardBrowserPanel.BrowserCard("a", "Alpha"),
                    new CardBrowserPanel.BrowserCard("b", "Beta"),
                    new CardBrowserPanel.BrowserCard("c", "Gamma")));
            holder[0].dispatchEvent(new java.awt.event.MouseEvent(holder[0],
                    java.awt.event.MouseEvent.MOUSE_PRESSED, 1L, 0, 20, 20, 1, false));
            holder[0].dispatchEvent(new java.awt.event.MouseEvent(holder[0],
                    java.awt.event.MouseEvent.MOUSE_PRESSED, 2L, java.awt.event.InputEvent.CTRL_DOWN_MASK,
                    125, 20, 1, false));
            holder[0].setCandidateIdentities(java.util.Set.of("b", "c", "missing"));

            assertEquals(java.util.Set.of("a", "b"), holder[0].selectedIdentities());
            assertEquals(List.of("a", "b"), holder[0].selectedCards().stream()
                    .map(CardBrowserPanel.BrowserCard::identity).toList());
            assertEquals(java.util.Set.of("b", "c"), holder[0].candidateIdentities());

            holder[0].setCards(List.of(
                    new CardBrowserPanel.BrowserCard("c", "Gamma"),
                    new CardBrowserPanel.BrowserCard("b", "Beta"),
                    new CardBrowserPanel.BrowserCard("a", "Alpha")));
            assertEquals(java.util.Set.of("a", "b"), holder[0].selectedIdentities());
            assertEquals(java.util.Set.of("b", "c"), holder[0].candidateIdentities());
        });
    }

    @Test void clearsRendererBackgroundWithThemeColor() throws Exception {
        AtomicReference<Integer> painted = new AtomicReference<>();
        AtomicReference<Integer> expected = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            CardBrowserPanel panel = new CardBrowserPanel(
                    new CardGridLayout(100, 160, 10, 10, 10),
                    new ViewportImageWindow(20),
                    card -> CompletableFuture.completedFuture(Optional.empty()));
            panel.setSize(240, 180);
            panel.setCards(List.of());
            BufferedImage image = new BufferedImage(240, 180, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setColor(Color.MAGENTA);
                graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                panel.paint(graphics);
            } finally {
                graphics.dispose();
            }
            painted.set(image.getRGB(120, 90));
            expected.set(panel.getBackground().getRGB());
        });
        assertEquals(expected.get(), painted.get());
    }

    @Test
    void followsWindowsSelectionAndCandidateGestures() throws Exception {
        CardBrowserPanel[] holder = new CardBrowserPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new CardBrowserPanel(
                    new CardGridLayout(100, 160, 10, 10, 10),
                    new ViewportImageWindow(20),
                    card -> CompletableFuture.completedFuture(Optional.empty()));
            holder[0].setSize(460, 500);
            holder[0].setCards(List.of(
                    new CardBrowserPanel.BrowserCard("a", "Alpha"),
                    new CardBrowserPanel.BrowserCard("b", "Beta"),
                    new CardBrowserPanel.BrowserCard("c", "Gamma"),
                    new CardBrowserPanel.BrowserCard("d", "Delta")));

            press(holder[0], 20, 20, 1, 0);
            press(holder[0], 235, 20, 1, java.awt.event.InputEvent.CTRL_DOWN_MASK);
            assertEquals(java.util.Set.of("a", "c"), holder[0].selectedIdentities());

            press(holder[0], 350, 20, 1, java.awt.event.InputEvent.SHIFT_DOWN_MASK);
            assertEquals(java.util.Set.of("c", "d"), holder[0].selectedIdentities());

            press(holder[0], 20, 20, 1, java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK);
            assertEquals(java.util.Set.of("a", "b", "c", "d"), holder[0].selectedIdentities());

            // Plain click replaces the previous multi-selection.
            press(holder[0], 125, 20, 1, 0);
            assertEquals(java.util.Set.of("b"), holder[0].selectedIdentities());

            // Double-clicking a card adds it to candidates without changing selection.
            press(holder[0], 235, 20, 1, 0);
            press(holder[0], 235, 20, 2, 0);
            assertEquals(java.util.Set.of("b"), holder[0].selectedIdentities());
            assertEquals(java.util.Set.of("c"), holder[0].candidateIdentities());

            // Double-clicking the selected chip adds all selected cards.
            press(holder[0], 150, 145, 1, 0);
            press(holder[0], 150, 145, 2, 0);
            assertEquals(java.util.Set.of("b", "c"), holder[0].candidateIdentities());

            // Clicking the candidates badge removes that card.
            press(holder[0], 325, 15, 1, 0);
            assertEquals(java.util.Set.of("b"), holder[0].candidateIdentities());
        });
    }

    private static void press(CardBrowserPanel panel, int x, int y, int clickCount, int modifiers) {
        panel.dispatchEvent(new java.awt.event.MouseEvent(panel,
                java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), modifiers,
                x, y, clickCount, false));
    }


    @Test
    void scrollToIdentityFocusesOnlyCardsStillVisibleInCurrentCatalog() throws Exception {
        CardBrowserPanel[] holder = new CardBrowserPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new CardBrowserPanel(
                    new CardGridLayout(100, 160, 10, 10, 10),
                    new ViewportImageWindow(20),
                    card -> CompletableFuture.completedFuture(Optional.empty()));
            holder[0].setSize(220, 500);
            holder[0].setCards(List.of(
                    new CardBrowserPanel.BrowserCard("a", "Alpha"),
                    new CardBrowserPanel.BrowserCard("b", "Beta")));
            assertTrue(holder[0].scrollToIdentity("b"));
            assertFalse(holder[0].scrollToIdentity("missing"));
        });
    }

}
