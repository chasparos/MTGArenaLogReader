package app.replay;


import app.model.card.CardInfo;
import app.model.event.GameEvent;
import app.model.game.BoardPermanentSnapshot;
import app.model.log.LogMessageInterface;
import app.model.session.GameModel;
import app.model.session.GameSession;
import app.snapshot.BoardStateMonitor;
import app.enrichment.CardImageCache;
import app.projection.AbilityNameStore;
import app.projection.GameEventProjector;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

/**
 * Custom-painted chronological replay. Card references are rendered as compact
 * coloured chips; only those chips own card-preview hover targets.
 * <p><strong>Architectural role:</strong> This type belongs to the Swing presentation boundary and consumes structured models and events without owning game reconstruction.</p>
 */
public final class GameView extends JPanel implements Scrollable {
    private static final int OUTER_PADDING = 18;
    private static final int EVENT_GAP = 9;
    private final GameModel model;
    private final GameEventProjector projector;
    private final GameSession session;
    private final OrderedMessageBuffer pendingMessages = new OrderedMessageBuffer();
    private final List<CardHitbox> cardHitboxes = new ArrayList<>();
    private final List<EventHitbox> eventHitboxes = new ArrayList<>();
    private final List<TurnHitbox> turnHitboxes = new ArrayList<>();
    private final ReplayTurnSelection turnSelection = new ReplayTurnSelection();
    private final BoardStateMonitor boardStateMonitor = new BoardStateMonitor();
    private final CardImageCache imageCache = new CardImageCache(
            Path.of(System.getProperty("user.home"), ".arena-log-viewer", "images"));
    private final ReplayFragmentParser replayFragmentParser = new ReplayFragmentParser();
    private final CardPreviewController previewController = new CardPreviewController(imageCache);
    private CardHitbox hovered;
    private final ReplayInteractionController interactions;
    private final ReplayFragmentRenderer replayFragmentRenderer;
    private final TurnSnapshotRenderer turnSnapshotRenderer;
    private final ReplayEventRenderer replayEventRenderer;
    private GameEvent highlightedEvent;
    private Runnable modelChangedListener = () -> { };
    private List<LayoutItem> layoutItems = List.of();
    private int cachedLayoutWidth = -1;
    private long layoutRevision;
    private long cachedLayoutRevision = -1;

    public GameView(GameModel model) { this(model, new AbilityNameStore()); }

    public GameView(GameModel model, AbilityNameStore abilityNames) {
        this(model, abilityNames, new GameEventProjector(abilityNames));
    }

    public GameView(GameModel model, AbilityNameStore abilityNames, GameEventProjector projector) {
        this(model, abilityNames, projector, null);
    }

    public GameView(GameSession session, AbilityNameStore abilityNames) {
        this(session.model(), abilityNames, session.projector(), session);
    }

    private GameView(GameModel model, AbilityNameStore abilityNames,
                     GameEventProjector projector, GameSession session) {
        this.model = model;
        this.projector = projector;
        this.session = session;
        this.interactions = new ReplayInteractionController(
                this,
                turnSelection,
                abilityNames,
                this::turnNumberAt,
                this::eventAtPoint,
                this::repaint);
        this.replayFragmentRenderer = new ReplayFragmentRenderer(
                new ReplayFragmentRenderer.Host() {
                    @Override public Font font() { return getFont(); }
                    @Override public Color foreground() { return getForeground(); }
                    @Override public Color colorOr(String key, Color fallback) {
                        return GameView.this.colorOr(key, fallback);
                    }
                    @Override public boolean isHovered(Rectangle bounds) {
                        return hovered != null && hovered.bounds().equals(bounds);
                    }
                    @Override public void registerHitbox(
                            Rectangle bounds, CardInfo card, GameEvent event,
                            BoardPermanentSnapshot permanent) {
                        cardHitboxes.add(
                                new CardHitbox(bounds, card, event, permanent));
                    }
                });
        this.turnSnapshotRenderer = new TurnSnapshotRenderer(
                new TurnSnapshotRenderer.Host() {
                    @Override public Font font() { return getFont(); }
                    @Override public Color foreground() { return getForeground(); }
                    @Override public Color colorOr(String key, Color fallback) {
                        return GameView.this.colorOr(key, fallback);
                    }
                    @Override public Color blend(
                            Color first, Color second, float amount) {
                        return GameView.this.blend(first, second, amount);
                    }
                    @Override public boolean paintSvg(
                            Graphics2D graphics, String resource,
                            int x, int y, int width, int height) {
                        return replayFragmentRenderer.paintSvg(
                                graphics, resource, x, y, width, height);
                    }
                    @Override public int fragmentWidth(
                            Graphics2D graphics, ReplayFragment fragment) {
                        return replayFragmentRenderer.width(graphics, fragment);
                    }
                    @Override public void paintFragment(
                            Graphics2D graphics, ReplayFragment fragment,
                            int x, int topY, int lineHeight, GameEvent event) {
                        replayFragmentRenderer.paint(
                                graphics, fragment, x, topY, lineHeight, event);
                    }
                    @Override public void paintPanel(
                            Graphics2D graphics, int y, int width,
                            int height, boolean snapshot) {
                        GameView.this.paintPanel(
                                graphics, y, width, height, snapshot);
                    }
                });
        this.replayEventRenderer = new ReplayEventRenderer(
                new ReplayEventRenderer.Host() {
                    @Override public Font font() { return getFont(); }
                    @Override public Color foreground() { return getForeground(); }
                    @Override public Color colorOr(String key, Color fallback) {
                        return GameView.this.colorOr(key, fallback);
                    }
                    @Override public String contextText(GameEvent event) {
                        return GameView.this.contextText(event);
                    }
                    @Override public int fragmentWidth(
                            Graphics2D graphics, ReplayFragment fragment) {
                        return replayFragmentRenderer.width(graphics, fragment);
                    }
                    @Override public void paintFragment(
                            Graphics2D graphics, ReplayFragment fragment,
                            int x, int y, int lineHeight, GameEvent event) {
                        replayFragmentRenderer.paint(
                                graphics, fragment, x, y, lineHeight, event);
                    }
                    @Override public void paintPanel(
                            Graphics2D graphics, int y, int width,
                            int height, boolean highlighted) {
                        GameView.this.paintPanel(
                                graphics, y, width, height, false, highlighted);
                    }
                    @Override public void registerHitbox(
                            Rectangle bounds, GameEvent event) {
                        eventHitboxes.add(new EventHitbox(bounds, event));
                    }
                },
                replayFragmentParser);
        setOpaque(true);
        setBackground(colorOr("Panel.background", new Color(0xF3F3F3)));
        setForeground(colorOr("Label.foreground", Color.DARK_GRAY));
        setFont(UIManager.getFont("Label.font") == null
                ? new Font(Font.SANS_SERIF, Font.PLAIN, 13)
                : UIManager.getFont("Label.font").deriveFont(13f));
        setPreferredSize(new Dimension(920, 500));

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { handleHover(e); }
            @Override public void mouseExited(MouseEvent e) { hovered = null; hidePreview(); repaint(); }
            @Override public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                } else if (SwingUtilities.isLeftMouseButton(e)) {
                    selectTurnAt(e);
                }
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showContextMenu(e);
            }
        };
        addMouseMotionListener(mouse);
        addMouseListener(mouse);
    }

    public GameModel getModel() { return model; }

    @Override
    public void updateUI() {
        super.updateUI();
        setBackground(colorOr("Panel.background", new Color(0xF3F3F3)));
        setForeground(colorOr("Label.foreground", Color.DARK_GRAY));
        Font label = UIManager.getFont("Label.font");
        if (label != null) setFont(label.deriveFont(13f));
        invalidateLayoutCache();
    }

    public void setModelChangedListener(Runnable listener) {
        modelChangedListener = listener == null ? () -> { } : listener;
    }

    /**
     * Enables the optional coaching interaction layer. Passing {@code null}
     * restores the normal reconstruction view.
     */
    public void setCoachingActions(CoachingActions coachingActions) {
        interactions.setCoachingActions(coachingActions);
    }

    public Set<Integer> getSelectedTurns() {
        return turnSelection.snapshot();
    }

    /**
     * Selects and reveals a turn on behalf of an external UI coordinator.
     */
    public void navigateToTurn(int turnNumber) {
        highlightedEvent = null;
        selectTurn(turnNumber);
        SwingUtilities.invokeLater(() -> scrollTurnToTop(turnNumber));
    }

    /**
     * Selects the owning turn, aligns it to the top of the viewport and then
     * reveals and highlights the referenced event when necessary.
     */
    public void navigateToEvent(GameEvent event) {
        if (event == null || event.getTurnNumber() == null) return;
        highlightedEvent = event;
        selectTurn(event.getTurnNumber());
        SwingUtilities.invokeLater(() -> {
            scrollTurnToTop(event.getTurnNumber());
            SwingUtilities.invokeLater(() -> revealEvent(event));
        });
    }

    private void selectTurn(int turnNumber) {
        turnSelection.selectOnly(turnNumber);
        repaint();
    }

    private void scrollTurnToTop(int turnNumber) {
        layoutItems.stream()
                .filter(item -> item.kind() == LayoutKind.TURN_HEADER)
                .filter(item -> Objects.equals(
                        item.event().getTurnNumber(), turnNumber))
                .findFirst()
                .ifPresent(item -> setViewportY(Math.max(0, item.y() - 8)));
    }

    private void revealEvent(GameEvent event) {
        layoutItems.stream()
                .filter(item -> item.kind() != LayoutKind.TURN_HEADER)
                .filter(item -> item.event() == event)
                .findFirst()
                .ifPresent(item -> {
                    Rectangle bounds = new Rectangle(
                            OUTER_PADDING, item.y(),
                            Math.max(260, getWidth() - OUTER_PADDING * 2),
                            item.height());
                    Rectangle visible = getVisibleRect();
                    if (!visible.contains(bounds)) {
                        int targetY = bounds.y < visible.y
                                ? bounds.y - 8
                                : bounds.y + bounds.height
                                        - visible.height + 8;
                        setViewportY(Math.max(0, targetY));
                    }
                    repaint(bounds);
                });
    }

    private void setViewportY(int y) {
        Container ancestor = SwingUtilities.getAncestorOfClass(JViewport.class, this);
        if (!(ancestor instanceof JViewport viewport)) return;
        Point position = viewport.getViewPosition();
        int maximumY = Math.max(0, getHeight() - viewport.getExtentSize().height);
        viewport.setViewPosition(new Point(position.x, Math.min(y, maximumY)));
    }

    public void accept(LogMessageInterface message) {
        pendingMessages.add(message,
                () -> SwingUtilities.invokeLater(this::flushCompletedMessages));
    }

    public void clear() {
        pendingMessages.clear();
        model.clear();
        boardStateMonitor.reset();
        hovered = null;
        hidePreview();
        invalidateLayoutCache();
        updatePreferredHeight();
        repaint();
    }

    private void flushCompletedMessages() {
        List<GameEvent> additions = new ArrayList<>();
        for (OrderedMessageBuffer.CompletedMessage completed
                : pendingMessages.drainReady()) {
            additions.addAll(session == null
                    ? projector.project(completed.message(), completed.modelObject())
                    : session.project(completed.message(), completed.modelObject()));
        }
        if (!additions.isEmpty()) {
            boardStateMonitor.accept(additions);
            model.addEvents(additions);
            model.setOpeningHand(projector.openingHandPlayer(),
                    projector.mulliganCount(), projector.openingHand());
            invalidateLayoutCache();
            updatePreferredHeight();
            repaint();
            modelChangedListener.run();
        }
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            configure(g);
            cardHitboxes.clear();
            eventHitboxes.clear();
            turnHitboxes.clear();
            List<GameEvent> events = model.snapshot();
            if (events.isEmpty()) { paintEmptyState(g); return; }

            int width = Math.max(260, getWidth() - OUTER_PADDING * 2);
            ensureLayout(g, events, width);
            Rectangle viewport = getVisibleRect();
            int first = firstVisibleLayoutItem(viewport.y);
            for (int index = first; index < layoutItems.size(); index++) {
                LayoutItem item = layoutItems.get(index);
                if (item.y() >= viewport.y + viewport.height) break;
                if (item.bottom() <= viewport.y) continue;
                switch (item.kind()) {
                    case TURN_HEADER -> paintTurnHeader(
                            g, item.event(), item.y(), width);
                    case EVENT -> paintEvent(
                            g, item.event(), item.y(), width, true);
                    case SNAPSHOT -> paintTurnSnapshot(
                            g, item.event(), item.y(), width, true);
                }
            }
        } finally {
            g.dispose();
        }
    }

    private int firstVisibleLayoutItem(int viewportY) {
        int low = 0;
        int high = layoutItems.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (layoutItems.get(middle).bottom() <= viewportY) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    private void configure(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }

    private int paintTurnHeader(Graphics2D g, GameEvent event, int y, int width) {
        Font old = g.getFont();
        Font titleFont = old.deriveFont(Font.BOLD, 15f);
        g.setFont(titleFont);
        FontMetrics fm = g.getFontMetrics();
        int h = fm.getHeight() + 10;
        boolean selected = interactions.coachingEnabled()
                && turnSelection.contains(event.getTurnNumber());
        Color accent = colorOr("List.selectionBackground", new Color(0x4477AA));
        g.setColor(blend(getBackground(), accent, selected ? .34f : .13f));
        Shape header = new RoundRectangle2D.Float(OUTER_PADDING, y, width, h, 14, 14);
        g.fill(header);
        if (selected) {
            g.setColor(blend(accent, Color.BLACK, .16f));
            g.draw(header);
        }
        g.setColor(colorOr("Label.foreground", getForeground()));
        String title = "Turn " + event.getTurnNumber() + "  ·  " + nullToEmpty(event.getActivePlayerName());
        g.drawString(title, OUTER_PADDING + 12, y + 5 + fm.getAscent());
        if (interactions.coachingEnabled()) {
            turnHitboxes.add(new TurnHitbox(new Rectangle(OUTER_PADDING, y, width, h),
                    event.getTurnNumber()));
        }
        g.setFont(old);
        return y + h + EVENT_GAP;
    }

    private int paintTurnSnapshot(Graphics2D g, GameEvent event, int y, int width, boolean draw) {
        return turnSnapshotRenderer.paint(g, event, y, width, draw);
    }

    private int paintEvent(Graphics2D g, GameEvent event, int y, int width, boolean draw) {
        return replayEventRenderer.paint(g, event, y, width, draw,
                event == highlightedEvent);
    }

    private void paintPanel(Graphics2D g, int y, int width, int height, boolean snapshot) {
        paintPanel(g, y, width, height, snapshot, false);
    }

    private void paintPanel(
            Graphics2D g,
            int y,
            int width,
            int height,
            boolean snapshot,
            boolean highlighted) {
        Color accent = colorOr("List.selectionBackground", new Color(0x4477AA));
        Color panel = colorOr("TextArea.background", Color.WHITE);
        if (snapshot) panel = blend(panel, accent, .045f);
        if (highlighted) panel = blend(panel, accent, .22f);
        Color border = highlighted
                ? blend(accent, Color.BLACK, .12f)
                : blend(colorOr("Separator.foreground", new Color(0xAAAAAA)), panel, .30f);
        Shape box = new RoundRectangle2D.Float(OUTER_PADDING, y, width, height, 15, 15);
        g.setColor(panel);
        g.fill(box);
        g.setColor(border);
        Stroke previous = g.getStroke();
        if (highlighted) g.setStroke(new BasicStroke(2f));
        g.draw(box);
        g.setStroke(previous);
    }

    private String contextText(GameEvent event) {
        if (event.getGameResult() != null) return "Game result";
        String phase = displayEnum(event.getPhase());
        String step = displayEnum(event.getStep());
        if (phase.isBlank()) return "";
        if (step.isBlank() || step.equalsIgnoreCase(phase)) return phase;
        return phase + " / " + step;
    }

    private String displayEnum(String value) {
        if (value == null || value.isBlank()) return "";
        return value.replaceFirst("^Phase_", "").replaceFirst("^Step_", "")
                .replace('_', ' ').replaceAll("(?<=[a-z])(?=[A-Z])", " ").strip();
    }

    private String nullToEmpty(String value) { return value == null ? "" : value; }

    private Color blend(Color first, Color second, float amount) {
        float normalized = Math.max(0, Math.min(1, amount));
        return new Color(
                Math.round(first.getRed() * (1 - normalized)
                        + second.getRed() * normalized),
                Math.round(first.getGreen() * (1 - normalized)
                        + second.getGreen() * normalized),
                Math.round(first.getBlue() * (1 - normalized)
                        + second.getBlue() * normalized),
                Math.round(first.getAlpha() * (1 - normalized)
                        + second.getAlpha() * normalized));
    }

    private Color colorOr(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color == null ? fallback : color;
    }

    private void paintEmptyState(Graphics2D g) {
        g.setColor(colorOr("Label.disabledForeground", getForeground()));
        String text = "Waiting for game events…";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, OUTER_PADDING, OUTER_PADDING + fm.getAscent());
    }

    private void updatePreferredHeight() {
        int width = getWidth() > 0 ? getWidth() : 920;
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setFont(getFont());
            configure(g);
            int contentWidth = Math.max(260, width - OUTER_PADDING * 2);
            ensureLayout(g, model.snapshot(), contentWidth);
            int height = layoutItems.isEmpty()
                    ? 300
                    : layoutItems.get(layoutItems.size() - 1).bottom() + OUTER_PADDING;
            Dimension current = getPreferredSize();
            Dimension preferred = new Dimension(
                    Math.max(720, current.width), Math.max(300, height));
            if (!preferred.equals(current)) {
                setPreferredSize(preferred);
                revalidate();
            }
        } finally {
            g.dispose();
        }
    }

    private void ensureLayout(Graphics2D graphics, List<GameEvent> events, int width) {
        if (cachedLayoutRevision == layoutRevision && cachedLayoutWidth == width) return;
        List<LayoutItem> rebuilt = new ArrayList<>();
        int y = OUTER_PADDING;
        Integer previousTurn = null;
        for (GameEvent event : events) {
            if (event.getTurnNumber() != null
                    && !event.getTurnNumber().equals(previousTurn)) {
                int height = turnHeaderHeight(graphics);
                rebuilt.add(new LayoutItem(
                        LayoutKind.TURN_HEADER, event, y, height));
                y += height + EVENT_GAP;
                previousTurn = event.getTurnNumber();
            }
            int nextY = event.getTurnSnapshot().isEmpty()
                    ? paintEvent(graphics, event, y, width, false)
                    : paintTurnSnapshot(graphics, event, y, width, false);
            rebuilt.add(new LayoutItem(
                    event.getTurnSnapshot().isEmpty()
                            ? LayoutKind.EVENT : LayoutKind.SNAPSHOT,
                    event, y, nextY - y));
            y = nextY;
        }
        layoutItems = List.copyOf(rebuilt);
        cachedLayoutWidth = width;
        cachedLayoutRevision = layoutRevision;
    }

    private int turnHeaderHeight(Graphics2D graphics) {
        Font titleFont = graphics.getFont().deriveFont(Font.BOLD, 15f);
        return graphics.getFontMetrics(titleFont).getHeight() + 10;
    }

    private void invalidateLayoutCache() {
        layoutRevision++;
        cachedLayoutRevision = -1;
    }

    @Override public void doLayout() {
        super.doLayout();
        updatePreferredHeight();
    }

    private void handleHover(MouseEvent mouse) {
        CardHitbox hit = cardHitboxes.stream()
                .filter(value -> value.bounds().contains(mouse.getPoint())).findFirst().orElse(null);
        if (Objects.equals(hit, hovered)) return;
        hovered = hit;
        hidePreview();
        setCursor(hit == null ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        repaint();
        if (hit != null) showPreview(hit, mouse);
    }

    private void showPreview(CardHitbox hit, MouseEvent mouse) {
        previewController.show(
                this, hit.card(), new PreviewCardChip(hit.card(), hit.permanent()), mouse);
    }

    private String roomStateLabel(BoardPermanentSnapshot permanent) {
        return permanent.getUnlockedRoomHalves().isEmpty()
                ? ""
                : "unlocked: "
                        + String.join(", ", permanent.getUnlockedRoomHalves());
    }

    private final class PreviewCardChip extends JComponent {
        private final CardInfo card;
        private final BoardPermanentSnapshot permanent;

        private PreviewCardChip(CardInfo card, BoardPermanentSnapshot permanent) {
            this.card = card;
            this.permanent = permanent;
            setOpaque(false);
            setPreferredSize(new Dimension(320, 42));
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                configure(g);
                CardFragment fragment = new CardFragment(
                        card,
                        card == null ? "Unknown card" : nullToEmpty(card.getName()),
                        permanent == null ? "" : roomStateLabel(permanent),
                        permanent);
                int width = replayFragmentRenderer.width(g, fragment);
                int x = Math.max(7, (getWidth() - width) / 2);
                replayFragmentRenderer.paint(
                        g, fragment, x, 2, getHeight() - 4, null, false);
            } finally {
                g.dispose();
            }
        }
    }

    private void hidePreview() {
        previewController.hide();
    }

    private void selectTurnAt(MouseEvent mouse) {
        interactions.selectTurnAt(mouse);
    }

    private void showContextMenu(MouseEvent mouse) {
        interactions.showContextMenu(mouse);
    }

    private TurnHitbox turnAt(Point point) {
        return turnHitboxes.stream()
                .filter(value -> value.bounds().contains(point))
                .findFirst().orElse(null);
    }

    private EventHitbox eventAt(Point point) {
        return eventHitboxes.stream()
                .filter(value -> value.bounds().contains(point))
                .findFirst().orElse(null);
    }

    private Integer turnNumberAt(Point point) {
        TurnHitbox hit = turnAt(point);
        if (hit != null) return hit.turnNumber();
        GameEvent event = eventAtPoint(point);
        return event == null ? null : event.getTurnNumber();
    }

    private GameEvent eventAtPoint(Point point) {
        EventHitbox hit = eventAt(point);
        return hit == null ? null : hit.event();
    }

    @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(900, 600); }
    @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 22; }
    @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(44, visibleRect.height - 44);
    }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }

    private record CardHitbox(Rectangle bounds, CardInfo card, GameEvent event,
                              BoardPermanentSnapshot permanent) {}
    private record EventHitbox(Rectangle bounds, GameEvent event) {}
    private record TurnHitbox(Rectangle bounds, int turnNumber) {}
    private record LayoutItem(
            LayoutKind kind, GameEvent event, int y, int height) {
        int bottom() { return y + height; }
    }
    private enum LayoutKind { TURN_HEADER, EVENT, SNAPSHOT }

    public enum CoachingScope { MATCH, GAME, TURN, SELECTED_TURNS }

    public record CoachingRequest(CoachingScope scope, Set<Integer> turns, String question) {
        public CoachingRequest {
            Objects.requireNonNull(scope, "scope");
            turns = turns == null ? Set.of() : Set.copyOf(turns);
        }
    }

    @FunctionalInterface
    public interface CoachingActions {
        void request(CoachingRequest request);
    }

}
