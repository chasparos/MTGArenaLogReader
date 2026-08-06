package app.deckplanner.ui;

import app.ui.AppScrollBarUI;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CardBrowserScrollPaneTest {
    @Test void usesApplicationScrollbarAndThemeViewportBackground() throws Exception {
        AtomicReference<CardBrowserScrollPane> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            CardBrowserPanel browser = panel();
            reference.set(new CardBrowserScrollPane(browser));
        });

        CardBrowserScrollPane pane = reference.get();
        assertInstanceOf(AppScrollBarUI.class, pane.getVerticalScrollBar().getUI());
        assertInstanceOf(AppScrollBarUI.class, pane.getHorizontalScrollBar().getUI());
        assertEquals(pane.browser().getBackground(), pane.getViewport().getBackground());
    }

    @Test
    void preservesLogicalTopCardAcrossResponsiveResize() throws Exception {
        CardBrowserScrollPane[] holder = new CardBrowserScrollPane[1];
        SwingUtilities.invokeAndWait(() -> {
            CardBrowserPanel panel = panel();
            holder[0] = new CardBrowserScrollPane(panel);
            holder[0].setSize(250, 220);
            holder[0].doLayout();
            holder[0].setCards(cards(20));
            holder[0].doLayout();
            holder[0].getViewport().setViewPosition(new Point(0, 350));
        });
        SwingUtilities.invokeAndWait(() -> { });

        String[] before = new String[1];
        SwingUtilities.invokeAndWait(() -> before[0] = holder[0].browser()
                .captureScrollAnchor(holder[0].getViewport().getViewRect()).orElseThrow().identity());

        SwingUtilities.invokeAndWait(() -> {
            holder[0].setSize(480, 220);
            holder[0].doLayout();
        });
        SwingUtilities.invokeAndWait(() -> { });

        SwingUtilities.invokeAndWait(() -> assertEquals(before[0], holder[0].browser()
                .captureScrollAnchor(holder[0].getViewport().getViewRect()).orElseThrow().identity()));
    }

    @Test
    void preservesAnchorWhenFilteredResultRetainsCard() throws Exception {
        CardBrowserScrollPane[] holder = new CardBrowserScrollPane[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new CardBrowserScrollPane(panel());
            holder[0].setSize(250, 220);
            holder[0].doLayout();
            holder[0].setCards(cards(12));
            holder[0].doLayout();
            holder[0].getViewport().setViewPosition(new Point(0, 350));
        });
        SwingUtilities.invokeAndWait(() -> { });

        String[] anchored = new String[1];
        SwingUtilities.invokeAndWait(() -> {
            anchored[0] = holder[0].browser().captureScrollAnchor(holder[0].getViewport().getViewRect())
                    .orElseThrow().identity();
            List<CardBrowserPanel.BrowserCard> reordered = new ArrayList<>(cards(12));
            reordered.removeIf(card -> card.identity().equals("card-0") || card.identity().equals("card-1"));
            holder[0].setCards(reordered);
            assertEquals(anchored[0], holder[0].browser()
                    .captureScrollAnchor(holder[0].getViewport().getViewRect()).orElseThrow().identity());
        });
    }

    private static CardBrowserPanel panel() {
        return new CardBrowserPanel(
                new CardGridLayout(100, 160, 10, 10, 10),
                new ViewportImageWindow(20),
                card -> CompletableFuture.completedFuture(Optional.empty()));
    }

    private static List<CardBrowserPanel.BrowserCard> cards(int count) {
        List<CardBrowserPanel.BrowserCard> cards = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            cards.add(new CardBrowserPanel.BrowserCard("card-" + index, "Card " + index));
        }
        return cards;
    }
}
