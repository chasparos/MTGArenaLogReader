package app.deckplanner.ui;

import app.ui.AppColors;
import app.ui.CardDragTransfer;
import app.model.card.CardInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;

/**
 * Responsive Deck Planner card surface. Layout and request selection are delegated to pure models;
 * image work stays asynchronous and only Swing mutation/repaint runs on the EDT.
 */
public final class CardBrowserPanel extends JComponent implements Scrollable {
    public interface ImageSource {
        CompletableFuture<Optional<BufferedImage>> request(BrowserCard card);

        default CompletableFuture<Optional<BufferedImage>> requestFace(BrowserCard card, int faceIndex) {
            return faceIndex <= 0 ? request(card)
                    : CompletableFuture.completedFuture(Optional.empty());
        }
    }

    public interface CandidateListener {
        void added(java.util.Collection<String> identities);
        void removed(String identity);
        default void removed(java.util.Collection<String> identities) {
            if (identities == null) return;
            for (String identity : identities) removed(identity);
        }
    }

    public record BrowserCard(String identity, String name, int alternateArtCount,
                              app.model.card.CardInfo card, boolean alternateArtKnown) {
        public BrowserCard(String identity, String name) {
            this(identity, name, 1, null, true);
        }
        public BrowserCard(String identity, String name, int alternateArtCount) {
            this(identity, name, alternateArtCount, null, true);
        }
        public BrowserCard(String identity, String name, int alternateArtCount,
                           app.model.card.CardInfo card) {
            this(identity, name, alternateArtCount, card, true);
        }
        public BrowserCard {
            if (identity == null || identity.isBlank()) throw new IllegalArgumentException("identity required");
            name = name == null || name.isBlank() ? "Unknown card" : name;
            alternateArtCount = Math.max(1, alternateArtCount);
        }
    }

    /** Logical viewport position that survives responsive relayout and result reordering. */
    public record ScrollAnchor(String identity, int offsetY) {
        public ScrollAnchor {
            if (identity == null || identity.isBlank()) throw new IllegalArgumentException("identity required");
        }
    }

    private final CardGridLayout gridLayout;
    private final ViewportImageWindow imageWindow;
    private final ImageSource imageSource;
    private final CellRendererPane rendererPane = new CellRendererPane();
    private final CardView cardView = new CardView();
    private final Map<String, BufferedImage> images = new LinkedHashMap<>();
    private final Map<String, CompletableFuture<Optional<BufferedImage>>> pending = new LinkedHashMap<>();
    private final Map<String, Integer> visibleFaceByIdentity = new LinkedHashMap<>();
    private java.util.Set<String> requestedIdentities = java.util.Set.of();

    private List<BrowserCard> cards = List.of();
    private CardGridLayout.Result layoutResult;
    private final LinkedHashSet<String> selectedIdentities = new LinkedHashSet<>();
    private final LinkedHashSet<String> candidateIdentities = new LinkedHashSet<>();
    private CandidateListener candidateListener = new CandidateListener() {
        @Override public void added(java.util.Collection<String> identities) { }
        @Override public void removed(String identity) { }
    };
    private int selectedIndex = -1;
    private int selectionAnchorIndex = -1;
    private LinkedHashSet<String> selectionBeforeClick = new LinkedHashSet<>();
    private int selectedIndexBeforeClick = -1;
    private int focusedIndex = -1;
    private int hoveredIndex = -1;
    private int previousViewportY;
    private long generation;
    private Point dragPressed;
    private java.util.function.Function<List<String>, Image> dragImageProvider = identities -> null;
    private java.util.function.Consumer<String> alternateArtListener = ignored -> { };

    public CardBrowserPanel(CardGridLayout gridLayout,
                            ViewportImageWindow imageWindow,
                            ImageSource imageSource) {
        this.gridLayout = java.util.Objects.requireNonNull(gridLayout);
        this.imageWindow = java.util.Objects.requireNonNull(imageWindow);
        this.imageSource = java.util.Objects.requireNonNull(imageSource);
        setFocusable(true);
        add(rendererPane);
        setOpaque(true);
        refreshThemeColors();
        setTransferHandler(new BrowserTransferHandler());
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                requestFocusInWindow();
                dragPressed = event.getPoint();
                handleMousePressed(event);
            }
            @Override public void mouseReleased(MouseEvent event) {
                dragPressed = null;
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
            @Override public void mouseDragged(MouseEvent event) {
                if (dragPressed == null || dragPressed.distance(event.getPoint()) < 5) return;
                ensureLayout();
                int index = layoutResult.indexAt(dragPressed.x, dragPressed.y);
                if (index < 0) return;
                String identity = cards.get(index).identity();
                if (!selectedIdentities.contains(identity)) applySelection(index, false, false);
                TransferHandler handler = getTransferHandler();
                if (handler instanceof BrowserTransferHandler browserHandler) {
                    Image image = dragImageProvider.apply(selectedCards().stream()
                            .map(BrowserCard::identity).toList());
                    browserHandler.setDragImage(image);
                    if (image != null) browserHandler.setDragImageOffset(
                            new Point(Math.min(24, image.getWidth(null) / 3),
                                    Math.min(18, image.getHeight(null) / 3)));
                }
                handler.exportAsDrag(CardBrowserPanel.this, event, TransferHandler.COPY);
                dragPressed = null;
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
        String selectedIdentity = identityAt(selectedIndex);
        String focusedIdentity = identityAt(focusedIndex);
        generation++;
        cancelAllPending();
        this.cards = List.copyOf(cards == null ? List.of() : cards);
        Set<String> available = this.cards.stream().map(BrowserCard::identity).collect(java.util.stream.Collectors.toSet());
        selectedIdentities.retainAll(available);
        visibleFaceByIdentity.keySet().retainAll(available);
        selectedIndex = indexOfIdentity(selectedIdentity);
        if (selectedIndex < 0) selectedIndex = lastSelectedIndex();
        selectionAnchorIndex = selectedIndex;
        focusedIndex = indexOfIdentity(focusedIdentity);
        hoveredIndex = -1;
        requestedIdentities = java.util.Set.of();
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

    public boolean scrollToIdentity(String identity) {
        int index = indexOfIdentity(identity);
        if (index < 0) return false;
        ensureLayout();
        if (index >= layoutResult.bounds().size()) return false;
        int old = focusedIndex;
        focusedIndex = index;
        repaintIndex(old);
        repaintIndex(focusedIndex);
        scrollRectToVisible(layoutResult.bounds().get(index));
        return true;
    }

    public Set<String> selectedIdentities() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(selectedIdentities));
    }

    public List<BrowserCard> selectedCards() {
        return cards.stream().filter(card -> selectedIdentities.contains(card.identity())).toList();
    }

    public void clearSelection() {
        assertEdt();
        Set<String> previous = Set.copyOf(selectedIdentities);
        selectedIdentities.clear();
        selectedIndex = -1;
        selectionAnchorIndex = -1;
        repaintIdentities(previous);
    }

    public void setCandidateIdentities(Set<String> identities) {
        assertEdt();
        Set<String> previous = Set.copyOf(candidateIdentities);
        candidateIdentities.clear();
        if (identities != null) candidateIdentities.addAll(identities);
        LinkedHashSet<String> changed = new LinkedHashSet<>(previous);
        changed.addAll(candidateIdentities);
        repaintIdentities(changed);
    }

    /** Returns the candidates identities currently visible in this filtered browser result. */
    public Set<String> candidateIdentities() {
        Set<String> visible = cards.stream().map(BrowserCard::identity)
                .filter(candidateIdentities::contains)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return Collections.unmodifiableSet(visible);
    }

    public void setCandidateListener(CandidateListener listener) {
        assertEdt();
        candidateListener = listener == null ? new CandidateListener() {
            @Override public void added(java.util.Collection<String> identities) { }
            @Override public void removed(String identity) { }
        } : listener;
    }

    public void setDragImageProvider(java.util.function.Function<List<String>, Image> provider) {
        assertEdt();
        dragImageProvider = provider == null ? identities -> null : provider;
    }

    public void setAlternateArtListener(java.util.function.Consumer<String> listener) {
        assertEdt();
        alternateArtListener = listener == null ? ignored -> { } : listener;
    }

    public void addCandidateIdentities(java.util.Collection<String> identities) {
        assertEdt();
        if (identities == null) return;
        Set<String> available = cards.stream().map(BrowserCard::identity).collect(java.util.stream.Collectors.toSet());
        LinkedHashSet<String> changed = new LinkedHashSet<>();
        for (String identity : identities) {
            if (available.contains(identity) && candidateIdentities.add(identity)) changed.add(identity);
        }
        repaintIdentities(changed);
        if (!changed.isEmpty()) candidateListener.added(List.copyOf(changed));
    }

    public void removeCandidateIdentity(String identity) {
        assertEdt();
        if (identity != null && candidateIdentities.remove(identity)) {
            repaintIndex(indexOfIdentity(identity));
            candidateListener.removed(identity);
        }
    }

    /** Captures the first card intersecting the viewport plus its vertical pixel offset. */
    public Optional<ScrollAnchor> captureScrollAnchor(Rectangle viewport) {
        assertEdt();
        ensureLayout();
        if (viewport == null || cards.isEmpty()) return Optional.empty();
        for (int index = 0; index < layoutResult.bounds().size(); index++) {
            Rectangle bounds = layoutResult.bounds().get(index);
            if (bounds.y + bounds.height > viewport.y) {
                return Optional.of(new ScrollAnchor(cards.get(index).identity(), viewport.y - bounds.y));
            }
        }
        int last = cards.size() - 1;
        Rectangle bounds = layoutResult.bounds().get(last);
        return Optional.of(new ScrollAnchor(cards.get(last).identity(), viewport.y - bounds.y));
    }

    /** Resolves a logical anchor against the current card order and responsive layout. */
    public OptionalInt resolveScrollAnchorY(ScrollAnchor anchor) {
        assertEdt();
        if (anchor == null) return OptionalInt.empty();
        ensureLayout();
        int index = indexOfIdentity(anchor.identity());
        if (index < 0) return OptionalInt.empty();
        Rectangle bounds = layoutResult.bounds().get(index);
        return OptionalInt.of(Math.max(0, bounds.y + anchor.offsetY()));
    }

    /** Called by the containing scroll pane when its visible rectangle changes. */
    public void updateViewport(Rectangle viewport) {
        assertEdt();
        ensureLayout();
        int direction = Integer.compare(viewport.y, previousViewportY);
        previousViewportY = viewport.y;
        var window = imageWindow.select(layoutResult.bounds(), viewport, direction);
        java.util.LinkedHashSet<String> nextRequested = new java.util.LinkedHashSet<>();
        for (int index : window.requestedIndices()) nextRequested.add(cards.get(index).identity());
        cancelRequestsOutside(nextRequested);
        requestedIdentities = java.util.Set.copyOf(nextRequested);
        long requestGeneration = generation;
        for (int index : window.requestedIndices()) requestImage(index, requestGeneration);
    }

    private void requestImage(int index, long requestGeneration) {
        BrowserCard card = cards.get(index);
        int faceIndex = visibleFaceByIdentity.getOrDefault(card.identity(), 0);
        String key = imageKey(card.identity(), faceIndex);
        if (images.containsKey(key) || pending.containsKey(key)) return;
        CompletableFuture<Optional<BufferedImage>> future = imageSource.requestFace(card, faceIndex);
        pending.put(key, future);
        future.whenComplete((image, error) -> SwingUtilities.invokeLater(() -> {
            if (requestGeneration != generation) return;
            pending.remove(key);
            if (!requestedIdentities.contains(card.identity())) return;
            if (error == null && image != null && image.isPresent()) {
                images.put(key, image.get());
                int currentIndex = indexOfIdentity(card.identity());
                if (currentIndex >= 0 && currentIndex < layoutResult.bounds().size()) {
                    repaint(layoutResult.bounds().get(currentIndex));
                }
            }
        }));
    }

    private static String imageKey(String identity, int faceIndex) {
        return identity + "#face=" + Math.max(0, faceIndex);
    }

    private void handleMousePressed(MouseEvent event) {
        ensureLayout();
        int index = layoutResult.indexAt(event.getX(), event.getY());
        if (index < 0) return;
        Rectangle bounds = layoutResult.bounds().get(index);
        int localX = event.getX() - bounds.x;
        int localY = event.getY() - bounds.y;
        String identity = cards.get(index).identity();

        if (event.getClickCount() == 1) {
            selectionBeforeClick = new LinkedHashSet<>(selectedIdentities);
            selectedIndexBeforeClick = selectedIndex;
        }

        CardInfo clickedCard = cards.get(index).card();
        if (clickedCard != null && clickedCard.getCardFaces() != null
                && clickedCard.getCardFaces().size() > 1
                && CardView.faceToggleBadgeBounds(bounds.width, bounds.height).contains(localX, localY)) {
            int currentFace = visibleFaceByIdentity.getOrDefault(identity, 0);
            int nextFace = (currentFace + 1) % clickedCard.getCardFaces().size();
            visibleFaceByIdentity.put(identity, nextFace);
            requestImage(index, generation);
            repaint(bounds);
            return;
        }

        if ((!cards.get(index).alternateArtKnown() || cards.get(index).alternateArtCount() > 1)
                && CardView.alternateArtBadgeBounds(bounds.width).contains(localX, localY)) {
            alternateArtListener.accept(identity);
            return;
        }
        if (candidateIdentities.contains(identity)
                && CardView.candidateBadgeBounds(bounds.width).contains(localX, localY)) {
            if (event.getClickCount() == 1) removeCandidateIdentity(identity);
            return;
        }
        if (event.getClickCount() >= 2) {
            restoreSelectionBeforeClick();
            if (selectedIdentities.contains(identity) && selectedIdentities.size() > 1) {
                addCandidateIdentities(selectedIdentities);
            } else {
                addCandidateIdentities(List.of(identity));
            }
            focusedIndex = index;
            repaintIndex(index);
            return;
        }

        int oldFocused = focusedIndex;
        focusedIndex = index;
        applySelection(index, event.isControlDown(), event.isShiftDown());
        repaintIndex(oldFocused);
        repaintIndex(index);
    }

    private void restoreSelectionBeforeClick() {
        LinkedHashSet<String> changed = new LinkedHashSet<>(selectedIdentities);
        changed.addAll(selectionBeforeClick);
        selectedIdentities.clear();
        selectedIdentities.addAll(selectionBeforeClick);
        selectedIndex = selectedIndexBeforeClick;
        selectionAnchorIndex = selectedIndex;
        repaintIdentities(changed);
    }

    private void applySelection(int index, boolean control, boolean shift) {
        if (index < 0 || index >= cards.size()) return;
        LinkedHashSet<String> before = new LinkedHashSet<>(selectedIdentities);
        if (shift) {
            int anchor = selectionAnchorIndex >= 0 ? selectionAnchorIndex : (selectedIndex >= 0 ? selectedIndex : index);
            if (!control) selectedIdentities.clear();
            int from = Math.min(anchor, index);
            int to = Math.max(anchor, index);
            for (int current = from; current <= to; current++) selectedIdentities.add(cards.get(current).identity());
        } else if (control) {
            String identity = cards.get(index).identity();
            if (!selectedIdentities.remove(identity)) selectedIdentities.add(identity);
            selectionAnchorIndex = index;
        } else {
            selectedIdentities.clear();
            selectedIdentities.add(cards.get(index).identity());
            selectionAnchorIndex = index;
        }
        selectedIndex = selectedIdentities.contains(cards.get(index).identity()) ? index : lastSelectedIndex();
        before.addAll(selectedIdentities);
        repaintIdentities(before);
    }

    private int lastSelectedIndex() {
        int last = -1;
        for (int index = 0; index < cards.size(); index++) {
            if (selectedIdentities.contains(cards.get(index).identity())) last = index;
        }
        return last;
    }

    private void repaintIdentities(java.util.Collection<String> identities) {
        for (String identity : identities) repaintIndex(indexOfIdentity(identity));
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
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_DOWN_MASK), "toggle-select");
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.SHIFT_DOWN_MASK), "range-select");
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "add-range-select");
        getActionMap().put("left", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { moveFocus(-1); }});
        getActionMap().put("right", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { moveFocus(1); }});
        getActionMap().put("up", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { ensureLayout(); moveFocus(-layoutResult.columns()); }});
        getActionMap().put("down", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { ensureLayout(); moveFocus(layoutResult.columns()); }});
        getActionMap().put("select", selectionAction(false, false));
        getActionMap().put("toggle-select", selectionAction(true, false));
        getActionMap().put("range-select", selectionAction(false, true));
        getActionMap().put("add-range-select", selectionAction(true, true));
    }


    private Action selectionAction(boolean control, boolean shift) {
        return new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                if (focusedIndex < 0) return;
                applySelection(focusedIndex, control, shift);
            }
        };
    }
    @Override public void updateUI() {
        super.updateUI();
        refreshThemeColors();
    }

    private void refreshThemeColors() {
        setBackground(AppColors.color("Viewport.background",
                AppColors.color("App.surface", new Color(0x24272C))));
    }

    @Override protected void paintComponent(Graphics graphics) {
        // Clear the complete clip explicitly. The browser is a renderer surface rather than a
        // normal child hierarchy, so relying on incidental parent/viewport painting leaves stale
        // rows visible after resize or result replacement on some look-and-feels.
        Graphics2D background = (Graphics2D) graphics.create();
        try {
            background.setColor(getBackground());
            Rectangle clip = background.getClipBounds();
            background.fillRect(clip.x, clip.y, clip.width, clip.height);
        } finally {
            background.dispose();
        }
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
        int faceIndex = visibleFaceByIdentity.getOrDefault(card.identity(), 0);
        cardView.configure(
                card.name(),
                card.card(),
                images.get(imageKey(card.identity(), faceIndex)),
                index == hoveredIndex,
                selectedIdentities.contains(card.identity()),
                candidateIdentities.contains(card.identity()),
                hasFocus() && index == focusedIndex,
                card.alternateArtCount(), card.alternateArtKnown(), faceIndex);
        rendererPane.paintComponent(g, cardView, this,
                bounds.x, bounds.y, bounds.width, bounds.height, true);
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

    private void cancelRequestsOutside(java.util.Set<String> retained) {
        var iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (retained.contains(entry.getKey())) continue;
            entry.getValue().cancel(true);
            iterator.remove();
        }
    }

    private void cancelAllPending() {
        for (CompletableFuture<Optional<BufferedImage>> future : pending.values()) {
            future.cancel(true);
        }
        pending.clear();
    }

    private String identityAt(int index) {
        return index >= 0 && index < cards.size() ? cards.get(index).identity() : null;
    }

    private int indexOfIdentity(String identity) {
        if (identity == null) return -1;
        for (int index = 0; index < cards.size(); index++) {
            if (identity.equals(cards.get(index).identity())) return index;
        }
        return -1;
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

    private final class BrowserTransferHandler extends TransferHandler {
        @Override protected Transferable createTransferable(JComponent component) {
            List<String> identities = selectedCards().stream().map(BrowserCard::identity).toList();
            return identities.isEmpty() ? null : new CardDragTransfer("catalog", identities);
        }

        @Override public int getSourceActions(JComponent component) {
            return COPY;
        }

        @Override public boolean canImport(TransferSupport support) {
            if (!support.isDrop() || !support.isDataFlavorSupported(CardDragTransfer.FLAVOR)) return false;
            try {
                CardDragTransfer.Payload payload = CardDragTransfer.read(support.getTransferable());
                return "candidates".equals(payload.source()) && !payload.identities().isEmpty();
            } catch (Exception error) {
                return false;
            }
        }

        @Override public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            try {
                CardDragTransfer.Payload payload = CardDragTransfer.read(support.getTransferable());
                candidateListener.removed(payload.identities());
                return true;
            } catch (Exception error) {
                return false;
            }
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
