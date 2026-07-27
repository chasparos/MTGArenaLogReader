package app.replay;

import app.enrichment.CardImageCache;
import app.model.card.CardInfo;
import app.model.event.GameEvent;
import app.model.game.BoardPermanentSnapshot;
import app.model.log.LogMessageInterface;
import app.model.session.GameModel;
import app.model.session.GameSession;
import app.projection.AbilityNameStore;
import app.projection.GameEventProjector;
import app.snapshot.BoardStateMonitor;
import app.ui.AsyncVirtualListPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Chronological replay backed by an asynchronous virtual-list paint pipeline.
 * Expensive rows are measured and painted once on a worker thread, while the
 * EDT only composites visible row images and lightweight interaction overlays.
 */
public final class GameView extends AsyncVirtualListPanel<GameView.ReplayRenderNode, GameView.ReplayHit> {
    private static final int OUTER_PADDING = 18;
    private static final int EVENT_GAP = 9;
    private static final int HEADER_ESTIMATE = 38;
    private static final int EVENT_ESTIMATE = 72;
    private static final int SNAPSHOT_ESTIMATE = 330;

    private final GameModel model;
    private final GameEventProjector projector;
    private final GameSession session;
    private final OrderedMessageBuffer pendingMessages = new OrderedMessageBuffer();
    private final ReplayTurnSelection turnSelection = new ReplayTurnSelection();
    private final BoardStateMonitor boardStateMonitor = new BoardStateMonitor();
    private final CardImageCache imageCache = new CardImageCache(
            Path.of(System.getProperty("user.home"), ".arena-log-viewer", "images"));
    private final ReplayFragmentParser replayFragmentParser = new ReplayFragmentParser();
    private final CardPreviewController previewController = new CardPreviewController(imageCache);
    private final ThreadLocal<RenderCapture> renderCapture = new ThreadLocal<>();
    private final ReplayInteractionController interactions;
    private final ReplayFragmentRenderer replayFragmentRenderer;
    private final TurnSnapshotRenderer turnSnapshotRenderer;
    private final ReplayEventRenderer replayEventRenderer;
    private CardHit hovered;
    private GameEvent highlightedEvent;
    private Runnable modelChangedListener = () -> { };

    public GameView(GameModel model) { this(model, new AbilityNameStore()); }

    public GameView(GameModel model, AbilityNameStore abilityNames) {
        this(model, abilityNames, new GameEventProjector(abilityNames));
    }

    public GameView(GameModel model, AbilityNameStore abilityNames,
                    GameEventProjector projector) {
        this(model, abilityNames, projector, null);
    }

    public GameView(GameSession session, AbilityNameStore abilityNames) {
        this(session.model(), abilityNames, session.projector(), session);
    }

    private GameView(GameModel model, AbilityNameStore abilityNames,
                     GameEventProjector projector, GameSession session) {
        super("arena-replay-row-renderer");
        this.model = model;
        this.projector = projector;
        this.session = session;
        this.interactions = new ReplayInteractionController(
                this, turnSelection, abilityNames,
                this::turnNumberAt, this::eventAtPoint, this::repaint);
        this.replayFragmentRenderer = new ReplayFragmentRenderer(
                new ReplayFragmentRenderer.Host() {
                    @Override public Font font() { return GameView.this.getFont(); }
                    @Override public Color foreground() { return GameView.this.getForeground(); }
                    @Override public Color colorOr(String key, Color fallback) {
                        return GameView.this.colorOr(key, fallback);
                    }
                    @Override public boolean isHovered(Rectangle bounds) { return false; }
                    @Override public void registerHitbox(Rectangle bounds, CardInfo card,
                                                         GameEvent event,
                                                         BoardPermanentSnapshot permanent) {
                        RenderCapture capture = renderCapture.get();
                        if (capture != null) capture.add(bounds,
                                new CardHit(card, event, permanent));
                    }
                });
        this.turnSnapshotRenderer = new TurnSnapshotRenderer(
                new TurnSnapshotRenderer.Host() {
                    @Override public Font font() { return GameView.this.getFont(); }
                    @Override public Color foreground() { return GameView.this.getForeground(); }
                    @Override public Color colorOr(String key, Color fallback) {
                        return GameView.this.colorOr(key, fallback);
                    }
                    @Override public Color blend(Color first, Color second, float amount) {
                        return GameView.this.blend(first, second, amount);
                    }
                    @Override public boolean paintSvg(Graphics2D graphics, String resource,
                                                      int x, int y, int width, int height) {
                        return replayFragmentRenderer.paintSvg(
                                graphics, resource, x, y, width, height);
                    }
                    @Override public int fragmentWidth(Graphics2D graphics,
                                                       ReplayFragment fragment) {
                        return replayFragmentRenderer.width(graphics, fragment);
                    }
                    @Override public void paintFragment(Graphics2D graphics,
                                                        ReplayFragment fragment,
                                                        int x, int topY,
                                                        int lineHeight,
                                                        GameEvent event) {
                        replayFragmentRenderer.paint(
                                graphics, fragment, x, topY, lineHeight, event);
                    }
                    @Override public void paintPanel(Graphics2D graphics, int y,
                                                     int width, int height,
                                                     boolean snapshot) {
                        GameView.this.paintPanel(
                                graphics, y, width, height, snapshot, false);
                    }
                });
        this.replayEventRenderer = new ReplayEventRenderer(
                new ReplayEventRenderer.Host() {
                    @Override public Font font() { return GameView.this.getFont(); }
                    @Override public Color foreground() { return GameView.this.getForeground(); }
                    @Override public Color colorOr(String key, Color fallback) {
                        return GameView.this.colorOr(key, fallback);
                    }
                    @Override public String contextText(GameEvent event) {
                        return GameView.this.contextText(event);
                    }
                    @Override public int fragmentWidth(Graphics2D graphics,
                                                       ReplayFragment fragment) {
                        return replayFragmentRenderer.width(graphics, fragment);
                    }
                    @Override public void paintFragment(Graphics2D graphics,
                                                        ReplayFragment fragment,
                                                        int x, int y,
                                                        int lineHeight,
                                                        GameEvent event) {
                        replayFragmentRenderer.paint(
                                graphics, fragment, x, y, lineHeight, event);
                    }
                    @Override public void paintPanel(Graphics2D graphics, int y,
                                                     int width, int height,
                                                     boolean highlighted) {
                        GameView.this.paintPanel(
                                graphics, y, width, height, false, false);
                    }
                    @Override public void registerHitbox(Rectangle bounds,
                                                         GameEvent event) {
                        RenderCapture capture = renderCapture.get();
                        if (capture != null) capture.add(bounds, new EventHit(event));
                    }
                }, replayFragmentParser);

        setOpaque(true);
        setBackground(colorOr("Panel.background", new Color(0xF3F3F3)));
        setForeground(colorOr("Label.foreground", Color.DARK_GRAY));
        Font label = UIManager.getFont("Label.font");
        setFont(label == null
                ? new Font(Font.SANS_SERIF, Font.PLAIN, 13)
                : label.deriveFont(13f));
        setPreferredSize(new Dimension(900, 600));

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent event) { handleHover(event); }
            @Override public void mouseExited(MouseEvent event) {
                hovered = null;
                hidePreview();
                repaint();
            }
            @Override public void mousePressed(MouseEvent event) {
                if (event.isPopupTrigger()) showContextMenu(event);
                else if (SwingUtilities.isLeftMouseButton(event)) selectTurnAt(event);
            }
            @Override public void mouseReleased(MouseEvent event) {
                if (event.isPopupTrigger()) showContextMenu(event);
            }
        };
        addMouseMotionListener(mouse);
        addMouseListener(mouse);

        // Live views are initially empty and will rebuild as messages arrive.
        // Coaching views are constructed from an already populated decoded model,
        // so hydrate their retained render nodes immediately.
        if (!model.snapshot().isEmpty()) {
            if (SwingUtilities.isEventDispatchThread()) rebuildRenderNodes();
            else SwingUtilities.invokeLater(this::rebuildRenderNodes);
        }
    }

    public GameModel getModel() { return model; }

    @Override public void updateUI() {
        super.updateUI();
        setBackground(colorOr("Panel.background", new Color(0xF3F3F3)));
        setForeground(colorOr("Label.foreground", Color.DARK_GRAY));
        Font label = UIManager.getFont("Label.font");
        if (label != null) setFont(label.deriveFont(13f));
        if (model != null) invalidateRenderedItems();
    }

    public void setModelChangedListener(Runnable listener) {
        modelChangedListener = listener == null ? () -> { } : listener;
    }

    public void setCoachingActions(CoachingActions coachingActions) {
        interactions.setCoachingActions(coachingActions);
    }

    public Set<Integer> getSelectedTurns() { return turnSelection.snapshot(); }

    public void navigateToTurn(int turnNumber) {
        highlightedEvent = null;
        selectTurn(turnNumber);
        SwingUtilities.invokeLater(() -> scrollItemToTop(turnKey(turnNumber), 8));
    }

    public void navigateToEvent(GameEvent event) {
        if (event == null || event.getTurnNumber() == null) return;
        highlightedEvent = event;
        selectTurn(event.getTurnNumber());
        SwingUtilities.invokeLater(() -> revealItem(eventKey(event), 8));
    }

    private void selectTurn(int turnNumber) {
        turnSelection.selectOnly(turnNumber);
        repaint();
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
        setItems(List.of());
        repaint();
    }

    /** Permanently releases resources when this view will not be reused. */
    public void dispose() {
        pendingMessages.clear();
        hidePreview();
        disposeRenderer();
    }

    private void flushCompletedMessages() {
        List<GameEvent> additions = new ArrayList<>();
        for (OrderedMessageBuffer.CompletedMessage completed
                : pendingMessages.drainReady()) {
            additions.addAll(session == null
                    ? projector.project(completed.message(), completed.modelObject())
                    : session.project(completed.message(), completed.modelObject()));
        }
        if (additions.isEmpty()) return;
        boardStateMonitor.accept(additions);
        model.addEvents(additions);
        model.setOpeningHand(projector.openingHandPlayer(),
                projector.mulliganCount(), projector.openingHand());
        rebuildRenderNodes();
        modelChangedListener.run();
    }

    private void rebuildRenderNodes() {
        List<Item<ReplayRenderNode>> panels = new ArrayList<>();
        Integer previousTurn = null;
        for (GameEvent event : model.snapshot()) {
            if (event.getTurnNumber() != null
                    && !event.getTurnNumber().equals(previousTurn)) {
                Object key = turnKey(event.getTurnNumber());
                panels.add(new Item<>(key,
                        new ReplayRenderNode(key, PanelKind.TURN_HEADER, event, HEADER_ESTIMATE),
                        HEADER_ESTIMATE));
                previousTurn = event.getTurnNumber();
            }
            PanelKind kind = event.getTurnSnapshot().isEmpty()
                    ? PanelKind.EVENT : PanelKind.SNAPSHOT;
            Object key = eventKey(event);
            int estimate = kind == PanelKind.EVENT ? EVENT_ESTIMATE : SNAPSHOT_ESTIMATE;
            panels.add(new Item<>(key,
                    new ReplayRenderNode(key, kind, event, estimate), estimate));
        }
        setItems(panels);
    }

    @Override protected RenderedItem<ReplayHit> renderItem(
            Item<ReplayRenderNode> item, int width) {
        ReplayRenderNode panel = item.value();
        int contentWidth = Math.max(260, width - OUTER_PADDING * 2);
        RenderCapture capture = new RenderCapture();
        renderCapture.set(capture);
        try {
            int height = measurePanel(panel, contentWidth);
            BufferedImage image = new BufferedImage(Math.max(1, width),
                    Math.max(1, height), BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                configureGraphics(graphics);
                graphics.setComposite(AlphaComposite.Src);
                graphics.setColor(getBackground());
                graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                graphics.setComposite(AlphaComposite.SrcOver);
                graphics.setFont(getFont());
                paintPanelContent(graphics, panel, contentWidth);
            } finally {
                graphics.dispose();
            }
            return new RenderedItem<>(image, height, capture.regions());
        } finally {
            renderCapture.remove();
        }
    }

    private int measurePanel(ReplayRenderNode panel, int width) {
        BufferedImage scratch = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scratch.createGraphics();
        try {
            configureGraphics(graphics);
            graphics.setFont(getFont());
            return switch (panel.kind()) {
                case TURN_HEADER -> turnHeaderHeight(graphics) + EVENT_GAP;
                case EVENT -> replayEventRenderer.paint(
                        graphics, panel.event(), 0, width, false, false);
                case SNAPSHOT -> turnSnapshotRenderer.paint(
                        graphics, panel.event(), 0, width, false);
            };
        } finally {
            graphics.dispose();
        }
    }

    private void paintPanelContent(Graphics2D graphics, ReplayRenderNode panel, int width) {
        switch (panel.kind()) {
            case TURN_HEADER -> paintTurnHeader(graphics, panel.event(), 0, width);
            case EVENT -> replayEventRenderer.paint(
                    graphics, panel.event(), 0, width, true, false);
            case SNAPSHOT -> turnSnapshotRenderer.paint(
                    graphics, panel.event(), 0, width, true);
        }
    }

    private void paintTurnHeader(Graphics2D graphics, GameEvent event, int y, int width) {
        Font old = graphics.getFont();
        Font titleFont = old.deriveFont(Font.BOLD, 15f);
        graphics.setFont(titleFont);
        FontMetrics metrics = graphics.getFontMetrics();
        int height = metrics.getHeight() + 10;
        Color accent = colorOr("List.selectionBackground", new Color(0x4477AA));
        graphics.setColor(blend(getBackground(), accent, .13f));
        Shape header = new RoundRectangle2D.Float(
                OUTER_PADDING, y, width, height, 14, 14);
        graphics.fill(header);
        graphics.setColor(colorOr("Label.foreground", getForeground()));
        String title = "Turn " + event.getTurnNumber() + "  ·  "
                + nullToEmpty(event.getActivePlayerName());
        graphics.drawString(title, OUTER_PADDING + 12,
                y + 5 + metrics.getAscent());
        RenderCapture capture = renderCapture.get();
        if (capture != null) capture.add(
                new Rectangle(OUTER_PADDING, y, width, height),
                new TurnHit(event.getTurnNumber()));
        graphics.setFont(old);
    }

    private int turnHeaderHeight(Graphics2D graphics) {
        return graphics.getFontMetrics(
                graphics.getFont().deriveFont(Font.BOLD, 15f)).getHeight() + 10;
    }

    @Override protected void paintPlaceholder(Graphics2D graphics,
                                               Item<ReplayRenderNode> item,
                                               Rectangle bounds) {
        Color panel = colorOr("TextArea.background", Color.WHITE);
        graphics.setColor(panel);
        graphics.fillRoundRect(OUTER_PADDING, bounds.y,
                Math.max(1, bounds.width - OUTER_PADDING * 2),
                Math.max(1, bounds.height - EVENT_GAP), 15, 15);
    }

    @Override protected void paintItemOverlay(Graphics2D graphics,
                                              LocatedItem<ReplayRenderNode, ReplayHit> located) {
        ReplayRenderNode panel = located.item().value();
        Rectangle bounds = located.bounds();
        Color accent = colorOr("List.selectionBackground", new Color(0x4477AA));
        boolean selected = panel.kind() == PanelKind.TURN_HEADER
                && interactions.coachingEnabled()
                && turnSelection.contains(panel.event().getTurnNumber());
        boolean highlighted = panel.kind() != PanelKind.TURN_HEADER
                && panel.event() == highlightedEvent;
        if (selected || highlighted) {
            graphics.setColor(new Color(accent.getRed(), accent.getGreen(),
                    accent.getBlue(), selected ? 115 : 155));
            Stroke old = graphics.getStroke();
            graphics.setStroke(new BasicStroke(2f));
            int height = Math.max(1, bounds.height - EVENT_GAP);
            graphics.drawRoundRect(OUTER_PADDING, bounds.y,
                    Math.max(1, bounds.width - OUTER_PADDING * 2),
                    height, 15, 15);
            graphics.setStroke(old);
        }
        if (hovered != null && located.rendered() != null) {
            for (HitRegion<ReplayHit> region : located.rendered().hitRegions()) {
                if (region.context().equals(hovered)) {
                    Rectangle local = region.shape().getBounds();
                    graphics.setColor(new Color(255, 255, 255, 95));
                    graphics.fillRoundRect(local.x, bounds.y + local.y,
                            local.width, local.height, 12, 12);
                    graphics.setColor(new Color(255, 255, 255, 180));
                    graphics.drawRoundRect(local.x, bounds.y + local.y,
                            local.width, local.height, 12, 12);
                }
            }
        }
    }

    @Override protected void paintEmpty(Graphics2D graphics, Rectangle bounds) {
        graphics.setColor(colorOr("Label.disabledForeground", getForeground()));
        String text = "Waiting for game events…";
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.drawString(text, OUTER_PADDING,
                OUTER_PADDING + metrics.getAscent());
    }

    private void paintPanel(Graphics2D graphics, int y, int width, int height,
                            boolean snapshot, boolean highlighted) {
        Color accent = colorOr("List.selectionBackground", new Color(0x4477AA));
        Color panel = colorOr("TextArea.background", Color.WHITE);
        if (snapshot) panel = blend(panel, accent, .045f);
        Color border = blend(colorOr("Separator.foreground",
                new Color(0xAAAAAA)), panel, .30f);
        Shape box = new RoundRectangle2D.Float(
                OUTER_PADDING, y, width, height, 15, 15);
        graphics.setColor(panel);
        graphics.fill(box);
        graphics.setColor(border);
        graphics.draw(box);
    }

    private void handleHover(MouseEvent mouse) {
        ReplayHit hit = contextAt(mouse.getPoint());
        CardHit card = hit instanceof CardHit value ? value : null;
        if (Objects.equals(card, hovered)) return;
        hovered = card;
        hidePreview();
        setCursor(card == null ? Cursor.getDefaultCursor()
                : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        repaint();
        if (card != null) showPreview(card, mouse);
    }

    private void showPreview(CardHit hit, MouseEvent mouse) {
        previewController.show(this, hit.card(),
                new PreviewCardChip(hit.card(), hit.permanent()), mouse);
    }

    private void hidePreview() { previewController.hide(); }

    private void selectTurnAt(MouseEvent mouse) { interactions.selectTurnAt(mouse); }

    private void showContextMenu(MouseEvent mouse) { interactions.showContextMenu(mouse); }

    private Integer turnNumberAt(Point point) {
        ReplayHit hit = contextAt(point);
        if (hit instanceof TurnHit turn) return turn.turnNumber();
        if (hit instanceof EventHit event) return event.event().getTurnNumber();
        if (hit instanceof CardHit card && card.event() != null) {
            return card.event().getTurnNumber();
        }
        LocatedItem<ReplayRenderNode, ReplayHit> item = itemAt(point);
        return item == null ? null : item.item().value().event().getTurnNumber();
    }

    private GameEvent eventAtPoint(Point point) {
        ReplayHit hit = contextAt(point);
        if (hit instanceof EventHit event) return event.event();
        if (hit instanceof CardHit card) return card.event();
        LocatedItem<ReplayRenderNode, ReplayHit> item = itemAt(point);
        if (item == null || item.item().value().kind() == PanelKind.TURN_HEADER) return null;
        return item.item().value().event();
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
        return value.replaceFirst("^Phase_", "")
                .replaceFirst("^Step_", "").replace('_', ' ')
                .replaceAll("(?<=[a-z])(?=[A-Z])", " ").strip();
    }

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

    private String nullToEmpty(String value) { return value == null ? "" : value; }

    private String roomStateLabel(BoardPermanentSnapshot permanent) {
        return permanent.getUnlockedRoomHalves().isEmpty() ? ""
                : "unlocked: " + String.join(", ", permanent.getUnlockedRoomHalves());
    }

    private Object turnKey(int turn) { return "turn:" + turn; }

    private Object eventKey(GameEvent event) { return new EventPanelKey(event); }

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
                configureGraphics(g);
                CardFragment fragment = new CardFragment(card,
                        card == null ? "Unknown card" : nullToEmpty(card.getName()),
                        permanent == null ? "" : roomStateLabel(permanent), permanent);
                int width = replayFragmentRenderer.width(g, fragment);
                int x = Math.max(7, (getWidth() - width) / 2);
                replayFragmentRenderer.paint(
                        g, fragment, x, 2, getHeight() - 4, null, false);
            } finally {
                g.dispose();
            }
        }
    }

    private final class RenderCapture {
        private final List<HitRegion<ReplayHit>> regions = new ArrayList<>();
        void add(Shape shape, ReplayHit hit) {
            regions.add(new HitRegion<>(shape, hit));
        }
        List<HitRegion<ReplayHit>> regions() { return List.copyOf(regions); }
    }

    /**
     * Projection may emit several semantic events for one source log sequence.
     * Cache identity therefore has to follow the event instance, not sequence.
     */
    private static final class EventPanelKey {
        private final GameEvent event;
        private final int hash;

        private EventPanelKey(GameEvent event) {
            this.event = Objects.requireNonNull(event, "event");
            this.hash = System.identityHashCode(event);
        }

        @Override public boolean equals(Object other) {
            return other instanceof EventPanelKey key && key.event == event;
        }

        @Override public int hashCode() { return hash; }
    }

    /**
     * Immutable retained presentation node. Domain events stay unchanged while
     * virtual-list identity, row kind and provisional layout belong to the view.
     */
    record ReplayRenderNode(Object key, PanelKind kind, GameEvent event,
                            int estimatedHeight) {
        ReplayRenderNode {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(event, "event");
            estimatedHeight = Math.max(1, estimatedHeight);
        }
    }
    private enum PanelKind { TURN_HEADER, EVENT, SNAPSHOT }
    sealed interface ReplayHit permits CardHit, EventHit, TurnHit {}
    private record CardHit(CardInfo card, GameEvent event,
                           BoardPermanentSnapshot permanent) implements ReplayHit {}
    private record EventHit(GameEvent event) implements ReplayHit {}
    private record TurnHit(int turnNumber) implements ReplayHit {}

    public enum CoachingScope { MATCH, GAME, TURN, SELECTED_TURNS }

    public record CoachingRequest(CoachingScope scope, Set<Integer> turns,
                                  String question) {
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
