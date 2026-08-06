package app.deckplanner.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Responsive Deck Planner card surface. Layout and request selection are delegated to pure models;
 * image work stays asynchronous and only Swing mutation/repaint runs on the EDT.
 */
public final class CardBrowserPanel extends JComponent implements Scrollable {
    public interface ImageSource {
        CompletableFuture<Optional<BufferedImage>> request(BrowserCard card);
    }

    public record BrowserCard(String identity, String name) {
        public BrowserCard {
            if (identity == null || identity.isBlank()) throw new IllegalArgumentException("identity required");
            name = name == null || name.isBlank() ? "Unknown card" : name;
        }
    }

    private static final Color PLACEHOLDER = new Color(38, 42, 48);
    private static final Color PLACEHOLDER_EDGE = new Color(86, 92, 101);
    private static final Color SELECTED = new Color(255, 196, 64);
    private static final Color FOCUSED = new Color(112, 184, 255);
    private static final Color HOVERED = new Color(255, 255, 255, 120);

    private final CardGridLayout gridLayout;
    private final ViewportImageWindow imageWindow;
    private final ImageSource imageSource;
    private final Map<String, BufferedImage> images = new LinkedHashMap<>();
    private final Map<String, CompletableFuture<Optional<BufferedImage>>> pending = new LinkedHashMap<>();

    private List<BrowserCard> cards = List.of();
    private CardGridLayout.Result layoutResult;
    private int selectedIndex = -1;
    private int focusedIndex = -1;
    private int hoveredIndex = -1;
    private int previousViewportY;
    private long generation;

    public CardBrowserPanel(CardGridLayout gridLayout,
                            ViewportImageWindow imageWindow,
                            ImageSource imageSource) {
        this.gridLayout = java.util.Objects.requireNonNull(gridLayout);
        this.imageWindow = java.util.Objects.requireNonNull(imageWindow);
        this.imageSource = java.util.Objects.requireNonNull(imageSource);
        setFocusable(true);
        setOpaque(true);
        setBackground(new Color(22, 24, 28));
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                requestFocusInWindow();
                selectAt(event.getX(), event.getY());
            }
            @Override public void mouseExited(MouseEvent event) {
                setHoveredIndex(-1);
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent event) {
                ensureLayout();
                setHoveredIndex(layoutResult.indexAt(event.getX(), event.getY()));
            }
        });
        addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent event) { repaintFocused(); }
            @Override public void focusLost(FocusEvent event) { repaintFocused(); }
        });
        installKeys();
    }

    public void setCards(List<BrowserCard> cards) {
        assertEdt();
        this.cards = List.copyOf(cards == null ? List.of() : cards);
        generation++;
        pending.clear();
        selectedIndex = normalizeIndex(selectedIndex);
        focusedIndex = normalizeIndex(focusedIndex);
        hoveredIndex = -1;
        relayout();
    }

    public List<BrowserCard> cards() {
        return cards;
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public Optional<BrowserCard> selectedCard() {
        return selectedIndex >= 0 ? Optional.of(cards.get(selectedIndex)) : Optional.empty();
    }

    /** Called by the containing scroll pane when its visible rectangle changes. */
    public void updateViewport(Rectangle viewport) {
        assertEdt();
        ensureLayout();
        int direction = Integer.compare(viewport.y, previousViewportY);
        previousViewportY = viewport.y;
        var window = imageWindow.select(layoutResult.bounds(), viewport, direction);
        long requestGeneration = generation;
        for (int index : window.requestedIndices()) requestImage(index, requestGeneration);
    }

    private void requestImage(int index, long requestGeneration) {
        BrowserCard card = cards.get(index);
        if (images.containsKey(card.identity()) || pending.containsKey(card.identity())) return;
        CompletableFuture<Optional<BufferedImage>> future = imageSource.request(card);
        pending.put(card.identity(), future);
        future.whenComplete((image, error) -> SwingUtilities.invokeLater(() -> {
            if (requestGeneration != generation) return;
            pending.remove(card.identity());
            if (error == null && image != null && image.isPresent()) {
                images.put(card.identity(), image.get());
                if (index < layoutResult.bounds().size()) repaint(layoutResult.bounds().get(index));
            }
        }));
    }

    private void selectAt(int x, int y) {
        ensureLayout();
        int index = layoutResult.indexAt(x, y);
        if (index < 0) return;
        int oldSelected = selectedIndex;
        int oldFocused = focusedIndex;
        selectedIndex = index;
        focusedIndex = index;
        repaintIndex(oldSelected);
        repaintIndex(oldFocused);
        repaintIndex(index);
    }

    private void moveFocus(int delta) {
        if (cards.isEmpty()) return;
        ensureLayout();
        int old = focusedIndex;
        if (focusedIndex < 0) focusedIndex = 0;
        else focusedIndex = Math.max(0, Math.min(cards.size() - 1, focusedIndex + delta));
        repaintIndex(old);
        repaintIndex(focusedIndex);
        scrollRectToVisible(layoutResult.bounds().get(focusedIndex));
    }

    private void installKeys() {
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "left");
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "right");
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "up");
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "down");
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "select");
        getActionMap().put("left", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { moveFocus(-1); }});
        getActionMap().put("right", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { moveFocus(1); }});
        getActionMap().put("up", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { ensureLayout(); moveFocus(-layoutResult.columns()); }});
        getActionMap().put("down", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { ensureLayout(); moveFocus(layoutResult.columns()); }});
        getActionMap().put("select", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (focusedIndex < 0) return;
                int old = selectedIndex;
                selectedIndex = focusedIndex;
                repaintIndex(old);
                repaintIndex(selectedIndex);
            }
        });
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        ensureLayout();
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            Rectangle clip = g.getClipBounds();
            for (int index = 0; index < cards.size(); index++) {
                Rectangle bounds = layoutResult.bounds().get(index);
                if (!bounds.intersects(clip)) continue;
                paintCard(g, index, bounds);
            }
        } finally {
            g.dispose();
        }
    }

    private void paintCard(Graphics2D g, int index, Rectangle bounds) {
        BrowserCard card = cards.get(index);
        BufferedImage image = images.get(card.identity());
        if (image == null) {
            g.setColor(PLACEHOLDER);
            g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 14, 14);
            g.setColor(PLACEHOLDER_EDGE);
            g.drawRoundRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1, 14, 14);
            g.setColor(Color.WHITE);
            FontMetrics metrics = g.getFontMetrics();
            String label = ellipsize(card.name(), metrics, Math.max(20, bounds.width - 20));
            g.drawString(label, bounds.x + (bounds.width - metrics.stringWidth(label)) / 2,
                    bounds.y + bounds.height / 2);
        } else {
            g.drawImage(image, bounds.x, bounds.y, bounds.width, bounds.height, null);
        }
        if (index == hoveredIndex) stroke(g, bounds, HOVERED, 2f);
        if (index == selectedIndex) stroke(g, bounds, SELECTED, 4f);
        if (hasFocus() && index == focusedIndex) stroke(g, bounds, FOCUSED, 2f);
    }

    private static void stroke(Graphics2D g, Rectangle bounds, Color color, float width) {
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(width));
        g.setColor(color);
        int inset = Math.max(1, Math.round(width / 2));
        g.drawRoundRect(bounds.x + inset, bounds.y + inset,
                bounds.width - inset * 2 - 1, bounds.height - inset * 2 - 1, 14, 14);
        g.setStroke(old);
    }

    private static String ellipsize(String text, FontMetrics metrics, int width) {
        if (metrics.stringWidth(text) <= width) return text;
        String suffix = "…";
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end) + suffix) > width) end--;
        return text.substring(0, end) + suffix;
    }

    @Override public void invalidate() {
        super.invalidate();
        layoutResult = null;
    }

    private void relayout() {
        layoutResult = null;
        ensureLayout();
        setPreferredSize(layoutResult.preferredSize());
        revalidate();
        repaint();
    }

    private void ensureLayout() {
        int width = Math.max(1, getWidth());
        if (layoutResult == null || layoutResult.preferredSize().width != width) {
            layoutResult = gridLayout.layout(cards.size(), width);
            setPreferredSize(layoutResult.preferredSize());
        }
    }

    private int normalizeIndex(int index) {
        return index >= 0 && index < cards.size() ? index : -1;
    }

    private void setHoveredIndex(int index) {
        if (hoveredIndex == index) return;
        int old = hoveredIndex;
        hoveredIndex = index;
        repaintIndex(old);
        repaintIndex(index);
    }

    private void repaintFocused() { repaintIndex(focusedIndex); }
    private void repaintIndex(int index) {
        if (index >= 0 && layoutResult != null && index < layoutResult.bounds().size()) {
            repaint(layoutResult.bounds().get(index));
        }
    }

    private static void assertEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Swing mutation must run on EDT");
        }
    }

    @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(840, 620); }
    @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 32; }
    @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(32, orientation == javax.swing.SwingConstants.VERTICAL ? visibleRect.height - 32 : visibleRect.width - 32);
    }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
