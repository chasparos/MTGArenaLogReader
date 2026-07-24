package app.replay;


import app.model.card.CardInfo;
import app.model.event.GameEvent;
import app.model.game.BoardPermanentSnapshot;
import app.model.game.PlayerTurnSnapshot;
import app.model.log.LogMessageInterface;
import app.model.log.ModelObject;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Custom-painted chronological replay. Card references are rendered as compact
 * coloured chips; only those chips own card-preview hover targets.
 * <p><strong>Architectural role:</strong> This type belongs to the Swing presentation boundary and consumes structured models and events without owning game reconstruction.</p>
 */
public final class GameView extends JPanel implements Scrollable {
    private static final int OUTER_PADDING = 18;
    private static final int EVENT_GAP = 9;
    private static final int CARD_PADDING = 11;
    private static final int CONTEXT_WIDTH = 190;
    private static final int CHIP_X_PADDING = 12;
    private static final int CHIP_Y_PADDING = 2;
    private static final int RICH_LINE_HEIGHT = 38;
    private static final int SNAPSHOT_LINE_HEIGHT = 36;
    private static final int CHIP_ARC = 14;
    private static final int SYMBOL_SIZE = 11;
    private static final int CARD_MANA_GAP = 14;
    private static final int CARD_TYPE_ICON_SIZE = 11;
    private static final int CARD_TYPE_GAP = 6;
    private static final Pattern MANA = Pattern.compile("\\{([^}]+)}");
    private static final Pattern POWER_TOUGHNESS = Pattern.compile(
            "\\(?(-?\\d+|\\*)/(-?\\d+|\\*)\\)?");
    private static final List<String> KEYWORDS = List.of(
            "deathtouch", "defender", "double strike", "first strike", "flying",
            "haste", "hexproof", "indestructible", "lifelink", "menace",
            "reach", "trample", "vigilance", "ward");

    private final GameModel model;
    private final GameEventProjector projector;
    private final GameSession session;
    private final Deque<PendingMessage> pending = new ArrayDeque<>();
    private final List<CardHitbox> cardHitboxes = new ArrayList<>();
    private final List<EventHitbox> eventHitboxes = new ArrayList<>();
    private final List<TurnHitbox> turnHitboxes = new ArrayList<>();
    private final NavigableSet<Integer> selectedTurns = new TreeSet<>();
    private final AbilityNameStore abilityNames;
    private final BoardStateMonitor boardStateMonitor = new BoardStateMonitor();
    private final CardImageCache imageCache = new CardImageCache(
            Path.of(System.getProperty("user.home"), ".arena-log-viewer", "images"));
    private final SvgAssetRenderer svgAssets = new SvgAssetRenderer();
    private final ManaCostPainter manaCostPainter = new ManaCostPainter(svgAssets, SYMBOL_SIZE - 2);
    private final ManaCostPainter miniManaCostPainter = new ManaCostPainter(svgAssets, 8);
    private JWindow previewWindow;
    private CardHitbox hovered;
    private CoachingActions coachingActions;
    private Integer selectionAnchorTurn;
    private GameEvent highlightedEvent;
    private Runnable modelChangedListener = () -> { };

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
        this.abilityNames = abilityNames;
        this.projector = projector;
        this.session = session;
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

    public void setModelChangedListener(Runnable listener) {
        modelChangedListener = listener == null ? () -> { } : listener;
    }

    /**
     * Enables the optional coaching interaction layer. Passing {@code null}
     * restores the normal reconstruction view.
     */
    public void setCoachingActions(CoachingActions coachingActions) {
        this.coachingActions = coachingActions;
        if (coachingActions == null) {
            selectedTurns.clear();
            selectionAnchorTurn = null;
        }
        repaint();
    }

    public Set<Integer> getSelectedTurns() {
        return Set.copyOf(selectedTurns);
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
        selectedTurns.clear();
        selectedTurns.add(turnNumber);
        selectionAnchorTurn = turnNumber;
        repaint();
    }

    private void scrollTurnToTop(int turnNumber) {
        turnHitboxes.stream()
                .filter(hitbox -> hitbox.turnNumber() == turnNumber)
                .findFirst()
                .ifPresent(hitbox -> setViewportY(Math.max(0, hitbox.bounds().y - 8)));
    }

    private void revealEvent(GameEvent event) {
        eventHitboxes.stream()
                .filter(hitbox -> hitbox.event() == event)
                .findFirst()
                .ifPresent(hitbox -> {
                    Rectangle visible = getVisibleRect();
                    if (!visible.contains(hitbox.bounds())) {
                        int targetY = hitbox.bounds().y < visible.y
                                ? hitbox.bounds().y - 8
                                : hitbox.bounds().y + hitbox.bounds().height
                                        - visible.height + 8;
                        setViewportY(Math.max(0, targetY));
                    }
                    repaint(hitbox.bounds());
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
        PendingMessage pendingMessage = new PendingMessage(message);
        synchronized (pending) { pending.addLast(pendingMessage); }
        message.getModelFuture().whenComplete((modelObject, error) -> {
            synchronized (pending) { pendingMessage.complete(modelObject, error); }
            SwingUtilities.invokeLater(this::flushCompletedMessages);
        });
    }

    public void clear() {
        synchronized (pending) { pending.clear(); }
        model.clear();
        boardStateMonitor.reset();
        hovered = null;
        hidePreview();
        updatePreferredHeight();
        repaint();
    }

    private void flushCompletedMessages() {
        List<GameEvent> additions = new ArrayList<>();
        synchronized (pending) {
            while (!pending.isEmpty() && pending.peekFirst().completed()) {
                PendingMessage completed = pending.removeFirst();
                if (completed.error() == null) {
                    additions.addAll(session == null
                            ? projector.project(completed.message(), completed.modelObject())
                            : session.project(completed.message(), completed.modelObject()));
                }
            }
        }
        if (!additions.isEmpty()) {
            boardStateMonitor.accept(additions);
            model.addEvents(additions);
            model.setOpeningHand(projector.openingHandPlayer(),
                    projector.mulliganCount(), projector.openingHand());
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

            int y = OUTER_PADDING;
            int width = Math.max(260, getWidth() - OUTER_PADDING * 2);
            Integer previousTurn = null;
            for (GameEvent event : events) {
                if (event.getTurnNumber() != null && !event.getTurnNumber().equals(previousTurn)) {
                    y = paintTurnHeader(g, event, y, width);
                    previousTurn = event.getTurnNumber();
                }
                y = event.getTurnSnapshot().isEmpty()
                        ? paintEvent(g, event, y, width, true)
                        : paintTurnSnapshot(g, event, y, width, true);
            }
        } finally {
            g.dispose();
        }
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
        boolean selected = coachingActions != null
                && selectedTurns.contains(event.getTurnNumber());
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
        if (coachingActions != null) {
            turnHitboxes.add(new TurnHitbox(new Rectangle(OUTER_PADDING, y, width, h),
                    event.getTurnNumber()));
        }
        g.setFont(old);
        return y + h + EVENT_GAP;
    }

    private int paintTurnSnapshot(Graphics2D g, GameEvent event, int y, int width, boolean draw) {
        List<PlayerTurnSnapshot> players = event.getTurnSnapshot();
        int innerWidth = width - CARD_PADDING * 2;
        int gap = 14;
        int columnWidth = players.size() <= 1 ? innerWidth : (innerWidth - gap) / 2;
        List<List<SnapshotRow>> columns = players.stream()
                .map(player -> snapshotRows(g, player, columnWidth))
                .toList();
        int lineHeight = Math.max(g.getFontMetrics().getHeight() + 5, SNAPSHOT_LINE_HEIGHT);
        int contentRows = columns.stream().mapToInt(List::size).max().orElse(1);
        int titleHeight = g.getFontMetrics(getFont().deriveFont(Font.BOLD)).getHeight() + 8;
        int boxHeight = CARD_PADDING * 2 + titleHeight + contentRows * lineHeight;

        if (draw) {
            paintPanel(g, y, width, boxHeight, true);
            int x = OUTER_PADDING + CARD_PADDING;
            int titleBaseline = y + CARD_PADDING
                    + g.getFontMetrics(getFont().deriveFont(Font.BOLD)).getAscent();
            g.setFont(getFont().deriveFont(Font.BOLD));
            g.setColor(colorOr("Label.foreground", getForeground()));
            g.drawString("Start of turn", x, titleBaseline);
            g.setFont(getFont());

            int top = y + CARD_PADDING + titleHeight;
            for (int i = 0; i < columns.size(); i++) {
                int columnX = x + i * (columnWidth + gap);
                paintSnapshotColumn(g, event, columns.get(i), columnX, top, columnWidth, lineHeight);
                if (i > 0) {
                    g.setColor(blend(colorOr("Separator.foreground", new Color(0xAAAAAA)),
                            colorOr("TextArea.background", Color.WHITE), .45f));
                    int dividerX = columnX - gap / 2;
                    g.drawLine(dividerX, top, dividerX, y + boxHeight - CARD_PADDING);
                }
            }
        }
        return y + boxHeight + EVENT_GAP;
    }

    private List<SnapshotRow> snapshotRows(Graphics2D g, PlayerTurnSnapshot player, int width) {
        List<SnapshotRow> rows = new ArrayList<>();
        rows.add(new SnapshotRow(null, nullToEmpty(player.getPlayerName()), "", true));
        rows.add(new SnapshotRow("/svg/ability-lifelink.svg",
                player.getLifeTotal() == null ? "Life ?" : player.getLifeTotal() + " life", "", false));
        if (player.getPoisonCounters() != null && player.getPoisonCounters() > 0) {
            rows.add(new SnapshotRow("/svg/ability-toxic.svg",
                    player.getPoisonCounters() + " poison", "", false));
        }
        rows.add(new SnapshotRow("/svg/multiple.svg",
                player.getHandSize() == null ? "Hand ?" : player.getHandSize() + " cards in hand",
                "", false));

        addKnownZoneRows(g, rows, width, "/svg/multiple.svg", "Known hand", player.getKnownHand());
        addKnownZoneRows(g, rows, width, "/svg/counter-skull.svg", "Graveyard", player.getKnownGraveyard());
        addKnownZoneRows(g, rows, width, "/svg/ability-foretell.svg", "Exile", player.getKnownExile());

        rows.add(new SnapshotRow("/svg/land.svg", "Battlefield", "", true));
        if (player.getBattlefield().isEmpty()) {
            rows.add(new SnapshotRow(null, "Empty", "", false));
        } else {
            List<BoardPermanentSnapshot> roots = roots(player.getBattlefield()).stream()
                    .sorted(Comparator.comparing(BoardPermanentSnapshot::getCard,
                            Comparator.nullsLast(cardTypeComparator())))
                    .toList();
            addBattlefieldGroupRows(g, rows, width, "Lands", roots.stream()
                    .filter(permanent -> permanentTypeGroup(permanent) == 0).toList());
            addBattlefieldGroupRows(g, rows, width, "Creatures", roots.stream()
                    .filter(permanent -> permanentTypeGroup(permanent) == 1).toList());
            addBattlefieldGroupRows(g, rows, width, "Other permanents", roots.stream()
                    .filter(permanent -> permanentTypeGroup(permanent) == 2).toList());

            List<BoardPermanentSnapshot> attachments = player.getBattlefield().stream()
                    .filter(permanent -> permanent.getAttachedToLogicalObjectId() != null)
                    .sorted(Comparator.comparing(BoardPermanentSnapshot::getCard,
                            Comparator.nullsLast(cardTypeComparator())))
                    .toList();
            addBattlefieldGroupRows(g, rows, width, "Attachments", attachments);
        }
        return rows;
    }

    private void addBattlefieldGroupRows(Graphics2D g, List<SnapshotRow> rows, int width,
                                         String group, List<BoardPermanentSnapshot> permanents) {
        if (permanents.isEmpty()) return;
        rows.add(new SnapshotRow(null, group + " (" + permanents.size() + ")", "", true));
        int columns = inferPermanentColumns(g, permanents, width);
        for (int offset = 0; offset < permanents.size(); offset += columns) {
            rows.add(SnapshotRow.permanentGrid(
                    permanents.subList(offset, Math.min(offset + columns, permanents.size())),
                    columns));
        }
    }

    private int inferPermanentColumns(Graphics2D g,
                                      List<BoardPermanentSnapshot> permanents,
                                      int width) {
        int gap = 8;
        int preferredCellWidth = permanents.stream()
                .filter(permanent -> permanent.getCard() != null)
                .map(permanent -> new CardFragment(
                        permanent.getCard(),
                        permanent.getCard().getName(),
                        roomStateLabel(permanent),
                        permanent))
                .mapToInt(fragment -> fragmentWidth(g, fragment))
                .max()
                .orElse(0);
        for (int columns = Math.min(3, permanents.size()); columns >= 2; columns--) {
            int cellWidth = (width - gap * (columns - 1)) / columns;
            if (cellWidth >= Math.min(preferredCellWidth, 205)) return columns;
        }
        return 1;
    }

    private int permanentTypeGroup(BoardPermanentSnapshot permanent) {
        CardInfo card = permanent.getCard();
        String typeLine = card == null ? "" : nullToEmpty(card.getTypeLine()).toLowerCase(Locale.ROOT);
        if (typeLine.contains("land")) return 0;
        if (typeLine.contains("creature")) return 1;
        return 2;
    }

    private String roomStateLabel(BoardPermanentSnapshot permanent) {
        return permanent.getUnlockedRoomHalves().isEmpty()
                ? ""
                : "unlocked: " + String.join(", ", permanent.getUnlockedRoomHalves());
    }

    private void addKnownZoneRows(Graphics2D g, List<SnapshotRow> rows, int width,
                                  String icon, String zone, List<CardInfo> cards) {
        if (cards.isEmpty()) return;
        List<CardInfo> ordered = cards.stream()
                .filter(Objects::nonNull)
                .sorted(cardTypeComparator())
                .toList();
        rows.add(new SnapshotRow(icon, zone + " (" + cards.size() + " known)", "", true));
        int columns = inferZoneColumns(g, ordered, width);
        for (int offset = 0; offset < ordered.size(); offset += columns) {
            rows.add(SnapshotRow.cardGrid(
                    ordered.subList(offset, Math.min(offset + columns, ordered.size())),
                    columns));
        }
    }

    private int inferZoneColumns(Graphics2D g, List<CardInfo> cards, int width) {
        int gap = 8;
        int preferredCellWidth = cards.stream()
                .map(card -> new CardFragment(card, card.getName(), "", null))
                .mapToInt(fragment -> fragmentWidth(g, fragment))
                .max()
                .orElse(0);
        for (int columns = Math.min(3, cards.size()); columns >= 2; columns--) {
            int cellWidth = (width - gap * (columns - 1)) / columns;
            if (cellWidth >= Math.min(preferredCellWidth, 190)) return columns;
        }
        return 1;
    }

    private Comparator<CardInfo> cardTypeComparator() {
        return Comparator.comparingInt(this::cardTypeRank)
                .thenComparing(card -> nullToEmpty(card.getTypeLine()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(card -> nullToEmpty(card.getName()), String.CASE_INSENSITIVE_ORDER);
    }

    private int cardTypeRank(CardInfo card) {
        String typeLine = nullToEmpty(card.getTypeLine()).toLowerCase(Locale.ROOT);
        if (typeLine.contains("land")) return 0;
        if (typeLine.contains("creature")) return 1;
        if (typeLine.contains("enchantment")) return 2;
        if (typeLine.contains("artifact")) return 3;
        if (typeLine.contains("planeswalker")) return 4;
        if (typeLine.contains("battle")) return 5;
        if (typeLine.contains("instant")) return 6;
        if (typeLine.contains("sorcery")) return 7;
        return 8;
    }

    private void addPermanentRows(List<SnapshotRow> rows, List<BoardPermanentSnapshot> battlefield,
                                  BoardPermanentSnapshot permanent, int depth) {
        String suffix = boardPermanentSuffix(permanent);
        rows.add(new SnapshotRow(null, "  ".repeat(depth), suffix, false,
                permanent.getCard(), permanent, List.of(), 1, List.of(), 1));
        attachmentsOf(battlefield, permanent.getLogicalObjectId()).stream()
                .sorted(Comparator.comparing(BoardPermanentSnapshot::getCard,
                        Comparator.nullsLast(cardTypeComparator())))
                .forEach(attached -> addPermanentRows(
                        rows, battlefield, attached, depth + 1));
    }

    private void paintSnapshotColumn(Graphics2D g, GameEvent event, List<SnapshotRow> rows,
                                     int x, int top, int width, int lineHeight) {
        int y = top;
        for (SnapshotRow row : rows) {
            int textX = x;
            if (row.iconResource() != null) {
                int iconSize = 14;
                int iconY = y + (lineHeight - iconSize) / 2;
                if (svgAssets.paint(g, row.iconResource(), textX, iconY, iconSize, iconSize)) {
                    textX += iconSize + 6;
                }
            }
            if (!row.permanents().isEmpty()) {
                int gap = 8;
                int cellWidth = (width - gap * (row.permanentColumns() - 1))
                        / row.permanentColumns();
                for (int index = 0; index < row.permanents().size(); index++) {
                    BoardPermanentSnapshot permanent = row.permanents().get(index);
                    if (permanent.getCard() == null) continue;
                    int cellX = x + index * (cellWidth + gap);
                    Graphics2D cellGraphics = (Graphics2D) g.create();
                    try {
                        cellGraphics.clipRect(cellX - 7, y - 10, cellWidth + 14, lineHeight + 20);
                        paintFragment(cellGraphics,
                                new CardFragment(
                                        permanent.getCard(),
                                        permanent.getCard().getName(),
                                        roomStateLabel(permanent),
                                        permanent),
                                cellX, y, lineHeight, event);
                    } finally {
                        cellGraphics.dispose();
                    }
                }
            } else if (!row.cards().isEmpty()) {
                int gap = 8;
                int cellWidth = (width - gap * (row.cardColumns() - 1)) / row.cardColumns();
                for (int index = 0; index < row.cards().size(); index++) {
                    CardInfo card = row.cards().get(index);
                    int cellX = x + index * (cellWidth + gap);
                    Graphics2D cellGraphics = (Graphics2D) g.create();
                    try {
                        cellGraphics.clipRect(cellX - 7, y - 4, cellWidth + 14, lineHeight + 8);
                        paintFragment(cellGraphics,
                                new CardFragment(card, card.getName(), "", null),
                                cellX, y, lineHeight, event);
                    } finally {
                        cellGraphics.dispose();
                    }
                }
            } else if (row.card() != null) {
                if (!row.text().isBlank()) {
                    g.setColor(colorOr("TextArea.foreground", getForeground()));
                    g.drawString(row.text(), textX,
                            y + (lineHeight - g.getFontMetrics().getHeight()) / 2
                                    + g.getFontMetrics().getAscent());
                    textX += g.getFontMetrics().stringWidth(row.text());
                }
                String state = row.suffix().startsWith("unlocked:")
                        ? row.suffix() : "";
                CardFragment fragment = new CardFragment(row.card(), row.card().getName(), state, row.permanent());
                int chipWidth = Math.min(fragmentWidth(g, fragment), Math.max(80, width - (textX - x)));
                if (chipWidth > 0) paintFragment(g, fragment, textX, y, lineHeight, event);
                if (!row.suffix().isBlank() && state.isBlank()) {
                    int suffixX = textX + fragmentWidth(g, fragment) + 6;
                    g.setColor(colorOr("Label.disabledForeground", getForeground()));
                    g.drawString(row.suffix(), suffixX,
                            y + (lineHeight - g.getFontMetrics().getHeight()) / 2
                                    + g.getFontMetrics().getAscent());
                }
            } else {
                g.setFont(row.heading() ? getFont().deriveFont(Font.BOLD) : getFont());
                g.setColor(row.heading()
                        ? colorOr("Label.foreground", getForeground())
                        : colorOr("TextArea.foreground", getForeground()));
                g.drawString(row.text(), textX,
                        y + (lineHeight - g.getFontMetrics().getHeight()) / 2
                                + g.getFontMetrics().getAscent());
                g.setFont(getFont());
            }
            y += lineHeight;
        }
    }

    private String boardPermanentSuffix(BoardPermanentSnapshot permanent) {
        List<String> state = new ArrayList<>();
        if (permanent.getPower() != null && permanent.getToughness() != null) {
            state.add(permanent.getPower() + "/" + permanent.getToughness());
        }
        if (Boolean.TRUE.equals(permanent.getTapped())) state.add("tapped");
        if (!permanent.getUnlockedRoomHalves().isEmpty()) {
            state.add(0, "unlocked: " + String.join(", ", permanent.getUnlockedRoomHalves()));
        }
        return String.join(" · ", state);
    }

    private String boardPermanentText(BoardPermanentSnapshot permanent) {
        String name = permanent.getName() == null || permanent.getName().isBlank()
                ? "Unknown permanent" : permanent.getName();
        String suffix = boardPermanentSuffix(permanent);
        return suffix.isBlank() ? name : name + "  · " + suffix;
    }

    private int paintEvent(Graphics2D g, GameEvent event, int y, int width, boolean draw) {
        int contentX = OUTER_PADDING + CARD_PADDING + CONTEXT_WIDTH;
        int maxX = OUTER_PADDING + width - CARD_PADDING;
        RichLayout layout = layoutEvent(g, event, contentX, maxX, y + CARD_PADDING, draw);
        int boxHeight = Math.max(g.getFontMetrics().getHeight(), layout.height()) + CARD_PADDING * 2;
        if (draw) {
            paintPanel(g, y, width, boxHeight, false, event == highlightedEvent);
            String context = contextText(event);
            g.setColor(colorOr("Label.disabledForeground", getForeground()));
            g.setFont(getFont().deriveFont(Font.PLAIN, 12f));
            g.drawString(context, OUTER_PADDING + CARD_PADDING,
                    y + CARD_PADDING + g.getFontMetrics(getFont()).getAscent());
            g.setFont(getFont());
            layoutEvent(g, event, contentX, maxX, y + CARD_PADDING, true);
            eventHitboxes.add(new EventHitbox(new Rectangle(OUTER_PADDING, y, width, boxHeight), event));
        }
        return y + boxHeight + EVENT_GAP;
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

    private RichLayout layoutEvent(Graphics2D g, GameEvent event, int startX, int maxX, int topY, boolean draw) {
        List<Fragment> fragments = fragments(event);
        FontMetrics fm = g.getFontMetrics(getFont());
        int lineHeight = Math.max(RICH_LINE_HEIGHT, fm.getHeight());
        int x = startX;
        int y = topY;
        for (Fragment fragment : fragments) {
            int width = fragmentWidth(g, fragment);
            if (x > startX && x + width > maxX) {
                x = startX;
                y += lineHeight;
            }
            if (draw) paintFragment(g, fragment, x, y, lineHeight, event);
            x += width;
        }
        return new RichLayout(y - topY + lineHeight);
    }

    private List<Fragment> fragments(GameEvent event) {
        String text = event.getText() == null ? "" : event.getText();
        List<CardMatchCandidate> cards = event.getCards().stream()
                .filter(Objects::nonNull)
                .filter(c -> c.getName() != null && !c.getName().isBlank())
                .flatMap(card -> {
                    List<CardMatchCandidate> candidates = new ArrayList<>();
                    candidates.add(new CardMatchCandidate(card, card.getName()));
                    for (String face : card.getName().split("\\s+//\\s+")) {
                        if (!face.isBlank() && !face.equals(card.getName())) {
                            candidates.add(new CardMatchCandidate(card, face));
                        }
                    }
                    return candidates.stream();
                })
                .sorted(Comparator.comparingInt((CardMatchCandidate c) -> c.label().length()).reversed())
                .toList();
        List<Fragment> result = new ArrayList<>();
        int pos = 0;
        while (pos < text.length()) {
            Match next = nextMatch(text, pos, cards);
            if (next == null) {
                appendTextAndMana(result, text.substring(pos));
                break;
            }
            if (next.start() > pos) appendTextAndMana(result, text.substring(pos, next.start()));
            result.add(new CardFragment(next.card(), next.label(),
                    roomStateLabel(event, next.card(), next.label()), null));
            pos = next.end();
        }
        if (text.isEmpty()) result.add(new TextFragment(""));
        return result;
    }

    private Match nextMatch(String text, int from, List<CardMatchCandidate> cards) {
        Match best = null;
        for (CardMatchCandidate candidate : cards) {
            int at = text.indexOf(candidate.label(), from);
            if (at < 0) continue;
            Match match = new Match(at, at + candidate.label().length(),
                    candidate.card(), candidate.label());
            if (best == null || match.start() < best.start()
                    || (match.start() == best.start() && match.end() > best.end())) best = match;
        }
        return best;
    }

    private String roomStateLabel(GameEvent event, CardInfo card, String renderedLabel) {
        for (BoardPermanentSnapshot permanent : event.getBattlefieldObservation()) {
            if (permanent.getCard() == null
                    || !java.util.Objects.equals(permanent.getCard().getArenaId(), card.getArenaId())
                    || permanent.getUnlockedRoomHalves().isEmpty()) continue;
            return "unlocked: " + String.join(", ", permanent.getUnlockedRoomHalves());
        }
        if (card.getName() != null && card.getName().contains(" // ")
                && !renderedLabel.equals(card.getName())
                && event.getText() != null && event.getText().contains("casts " + renderedLabel)) {
            return "unlock";
        }
        return "";
    }

    private void appendTextAndMana(List<Fragment> out, String text) {
        Matcher matcher = MANA.matcher(text);
        int pos = 0;
        while (matcher.find()) {
            appendWords(out, text.substring(pos, matcher.start()));
            out.add(new ManaFragment(matcher.group(1)));
            pos = matcher.end();
        }
        appendWords(out, text.substring(pos));
    }

    private void appendWords(List<Fragment> out, String text) {
        int pos = 0;
        while (pos < text.length()) {
            MatchToken token = nextDecoratedToken(text, pos);
            if (token == null) {
                appendPlainWords(out, text.substring(pos));
                return;
            }
            if (token.start() > pos) appendPlainWords(out, text.substring(pos, token.start()));
            out.add(token.fragment());
            pos = token.end();
        }
    }

    private MatchToken nextDecoratedToken(String text, int from) {
        MatchToken best = null;

        Matcher pt = POWER_TOUGHNESS.matcher(text);
        if (pt.find(from)) {
            best = new MatchToken(pt.start(), pt.end(),
                    new PowerToughnessFragment(pt.group(1), pt.group(2)));
        }

        String lower = text.toLowerCase(Locale.ROOT);
        for (String keyword : KEYWORDS) {
            int at = lower.indexOf(keyword, from);
            if (at < 0 || !wordBoundary(lower, at, at + keyword.length())) continue;
            MatchToken candidate = new MatchToken(at, at + keyword.length(),
                    new KeywordFragment(keyword, displayKeyword(keyword)));
            if (best == null || candidate.start() < best.start()) best = candidate;
        }
        return best;
    }

    private void appendPlainWords(List<Fragment> out, String text) {
        Matcher matcher = Pattern.compile("\\s+|\\S+").matcher(text);
        while (matcher.find()) out.add(new TextFragment(matcher.group()));
    }

    private boolean wordBoundary(String text, int start, int end) {
        boolean left = start == 0 || !Character.isLetterOrDigit(text.charAt(start - 1));
        boolean right = end >= text.length() || !Character.isLetterOrDigit(text.charAt(end));
        return left && right;
    }

    private String displayKeyword(String keyword) {
        return Arrays.stream(keyword.split(" "))
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private int fragmentWidth(Graphics2D g, Fragment fragment) {
        FontMetrics fm = g.getFontMetrics(getFont());
        if (fragment instanceof TextFragment t) return fm.stringWidth(t.text());
        if (fragment instanceof ManaFragment) return SYMBOL_SIZE + 2;
        if (fragment instanceof PowerToughnessFragment pt) {
            return fm.stringWidth(pt.power() + "/" + pt.toughness()) + CHIP_X_PADDING * 2;
        }
        if (fragment instanceof KeywordFragment keyword) {
            return SYMBOL_SIZE + 4 + fm.stringWidth(keyword.label()) + CHIP_X_PADDING * 2;
        }
        CardFragment cardFragment = (CardFragment) fragment;
        CardInfo card = cardFragment.card();
        Font chipFont = getFont().deriveFont(Font.BOLD, Math.max(9f, getFont().getSize2D() - 1f));
        FontMetrics chipMetrics = g.getFontMetrics(chipFont);
        int mana = manaCostPainter.width(card.getManaCost());
        int typeIcon = cardTypeResource(card) == null ? 0 : CARD_TYPE_ICON_SIZE + CARD_TYPE_GAP;
        boolean[] unlocks = roomUnlockSides(cardFragment);
        int lockWidth = (unlocks[0] ? SYMBOL_SIZE + 3 : 0)
                + (unlocks[1] ? SYMBOL_SIZE + 3 : 0);
        return typeIcon + chipMetrics.stringWidth(cardFragment.label()) + lockWidth
                + CHIP_X_PADDING * 2 + mana + (mana > 0 ? CARD_MANA_GAP : 0);
    }

    private void paintFragment(Graphics2D g, Fragment fragment, int x, int topY,
                               int lineHeight, GameEvent event) {
        paintFragment(g, fragment, x, topY, lineHeight, event, true);
    }

    private void paintFragment(Graphics2D g, Fragment fragment, int x, int topY,
                               int lineHeight, GameEvent event, boolean registerHitbox) {
        FontMetrics fm = g.getFontMetrics(getFont());
        int baseline = topY + (lineHeight - fm.getHeight()) / 2 + fm.getAscent();
        if (fragment instanceof TextFragment t) {
            g.setColor(colorOr("TextArea.foreground", getForeground()));
            g.drawString(t.text(), x, baseline);
            return;
        }
        if (fragment instanceof ManaFragment mana) {
            paintMana(g, mana.symbol(), x, topY + (lineHeight - SYMBOL_SIZE) / 2);
            return;
        }
        if (fragment instanceof PowerToughnessFragment pt) {
            paintPowerToughnessChip(g, pt, x, topY, lineHeight);
            return;
        }
        if (fragment instanceof KeywordFragment keyword) {
            paintKeywordChip(g, keyword, x, topY, lineHeight);
            return;
        }

        CardFragment cardFragment = (CardFragment) fragment;
        CardInfo card = cardFragment.card();
        int width = fragmentWidth(g, fragment);
        int chipHeight = fm.getHeight() + CHIP_Y_PADDING * 2;
        int chipY = topY + (lineHeight - chipHeight) / 2;
        Rectangle bounds = new Rectangle(x, chipY, width, chipHeight);
        boolean hot = hovered != null && hovered.bounds().equals(bounds);

        Color base = cardColor(card);
        Color edge = blend(base, Color.BLACK, .28f);
        if (hot) base = blend(base, Color.WHITE, .18f);
        g.setColor(base);
        g.fill(new RoundRectangle2D.Float(x, chipY, width, chipHeight, CHIP_ARC, CHIP_ARC));
        g.setColor(edge);
        g.draw(new RoundRectangle2D.Float(x, chipY, width, chipHeight, CHIP_ARC, CHIP_ARC));

        Color textColor = contrast(base);
        g.setColor(textColor);
        Font oldFont = g.getFont();
        Font chipFont = oldFont.deriveFont(Font.BOLD, Math.max(9f, oldFont.getSize2D() - 1f));
        g.setFont(chipFont);
        FontMetrics chipMetrics = g.getFontMetrics();
        int textX = x + CHIP_X_PADDING;
        String typeResource = cardTypeResource(card);
        if (typeResource != null) {
            int iconY = chipY + (chipHeight - CARD_TYPE_ICON_SIZE) / 2 + 1;
            if (svgAssets.paint(g, "/svg/" + typeResource + ".svg",
                    textX, iconY, CARD_TYPE_ICON_SIZE, CARD_TYPE_ICON_SIZE)) {
                textX += CARD_TYPE_ICON_SIZE + CARD_TYPE_GAP;
            }
        }
        boolean[] unlocks = roomUnlockSides(cardFragment);
        int lockY = chipY + (chipHeight - SYMBOL_SIZE) / 2 + 1;
        if (unlocks[0] && svgAssets.paint(g, "/svg/open-lock.svg",
                textX, lockY, SYMBOL_SIZE, SYMBOL_SIZE)) {
            textX += SYMBOL_SIZE + 3;
        }
        int cardNameX = textX;
        int textBaseline = chipY + (chipHeight - chipMetrics.getHeight()) / 2 + chipMetrics.getAscent();
        g.drawString(cardFragment.label(), textX, textBaseline);
        textX += chipMetrics.stringWidth(cardFragment.label());
        if (unlocks[1]) {
            int rightLockX = textX + 3;
            if (svgAssets.paint(g, "/svg/open-lock.svg",
                    rightLockX, lockY, SYMBOL_SIZE, SYMBOL_SIZE)) {
                textX = rightLockX + SYMBOL_SIZE;
            }
        }
        if (false) {
            int badgeX = textX + CARD_TYPE_GAP;
            int badgeWidth = chipMetrics.stringWidth(cardFragment.stateLabel()) + 10;
            int badgeY = chipY + 3;
            int badgeHeight = chipHeight - 6;
            Color badge = blend(base, Color.WHITE, .28f);
            g.setColor(badge);
            g.fill(new RoundRectangle2D.Float(badgeX, badgeY, badgeWidth, badgeHeight, 10, 10));
            g.setColor(contrast(badge));
            Font badgeFont = chipFont.deriveFont(Font.PLAIN, Math.max(8f, chipFont.getSize2D() - 2f));
            g.setFont(badgeFont);
            FontMetrics badgeMetrics = g.getFontMetrics();
            g.drawString(cardFragment.stateLabel(), badgeX + 5,
                    badgeY + (badgeHeight - badgeMetrics.getHeight()) / 2 + badgeMetrics.getAscent());
            g.setFont(chipFont);
        }

        int manaWidth = manaCostPainter.width(card.getManaCost());
        if (manaWidth > 0) {
            int manaX = x + width - CHIP_X_PADDING - manaWidth;
            int manaY = chipY + (chipHeight - (SYMBOL_SIZE - 2)) / 2;
            manaCostPainter.paint(g, card.getManaCost(), manaX, manaY, base);
        }
        if (cardFragment.permanent() != null) {
            BoardPermanentSnapshot permanent = cardFragment.permanent();
            if (permanent.getPower() != null && permanent.getToughness() != null) {
                paintPowerToughnessChip(g,
                        new PowerToughnessFragment(
                                String.valueOf(permanent.getPower()),
                                String.valueOf(permanent.getToughness())),
                        x + width, topY, lineHeight);
            }
            paintActivatedAbilityMiniChip(g, card, x, topY);
            paintPermanentAbilityMiniChip(g, permanent, cardNameX, topY, lineHeight);
            paintTappedMiniChip(g, permanent, x, topY, lineHeight);
        }
        g.setFont(oldFont);
        if (registerHitbox) {
            cardHitboxes.add(new CardHitbox(bounds, card, event, cardFragment.permanent()));
        }
    }

    private boolean[] roomUnlockSides(CardFragment fragment) {
        boolean[] result = new boolean[2];
        String name = fragment.card() == null ? "" : nullToEmpty(fragment.card().getName());
        if (!name.contains(" // ") || fragment.stateLabel().isBlank()) return result;
        String[] halves = name.split(" // ", 2);
        String state = fragment.stateLabel().toLowerCase(Locale.ROOT);
        if ("unlock".equals(state)) {
            result[fragment.label().equalsIgnoreCase(halves[1]) ? 1 : 0] = true;
            return result;
        }
        result[0] = state.contains(halves[0].toLowerCase(Locale.ROOT));
        result[1] = state.contains(halves[1].toLowerCase(Locale.ROOT));
        return result;
    }

    private void paintPermanentAbilityMiniChip(Graphics2D g, BoardPermanentSnapshot permanent,
                                               int cardNameX, int topY, int lineHeight) {
        List<String> abilities = permanent.getEvergreenAbilities();
        if (abilities == null || abilities.isEmpty()) return;
        int visible = Math.min(4, abilities.size());
        int iconSize = 10;
        int padding = 3;
        int gap = 2;
        int width = padding * 2 + visible * iconSize + Math.max(0, visible - 1) * gap;
        int height = iconSize + padding * 2;
        int x = cardNameX;
        int y = topY + lineHeight - height - 1;
        Color base = blend(colorOr("TextArea.background", Color.WHITE),
                colorOr("List.selectionBackground", new Color(0x6D7F9B)), .18f);
        Shape chip = new RoundRectangle2D.Float(x, y, width, height, 10, 10);
        g.setColor(base);
        g.fill(chip);
        g.setColor(blend(base, Color.BLACK, .28f));
        g.draw(chip);
        int iconX = x + padding;
        for (int i = 0; i < visible; i++) {
            paintKeyword(g, abilities.get(i), iconX, y + padding);
            iconX += iconSize + gap;
        }
    }


    private void paintTappedMiniChip(Graphics2D g, BoardPermanentSnapshot permanent,
                                      int leftX, int topY, int lineHeight) {
        if (!Boolean.TRUE.equals(permanent.getTapped())) return;

        int iconSize = 8;
        int padding = 2;
        int width = iconSize + padding * 2;
        int height = iconSize + padding * 2;
        int x = leftX - 4;
        int y = topY + lineHeight - height - 4;

        Color base = blend(colorOr("TextArea.background", Color.WHITE),
                new Color(0xC94F4F), .55f);
        Shape chip = new RoundRectangle2D.Float(x, y, width, height, 10, 10);
        g.setColor(base);
        g.fill(chip);
        g.setColor(blend(base, Color.BLACK, .28f));
        g.draw(chip);

        if (!svgAssets.paint(g, "/svg/tap.svg",
                x + padding, y + padding, iconSize, iconSize)) {
            paintFallbackSymbol(g, "T", x + padding, y + padding, iconSize);
        }
    }


    private void paintActivatedAbilityMiniChip(Graphics2D g, CardInfo card, int leftX, int topY) {
        ActivatedAbilityBadge badge = activatedAbilityBadge(card);
        if (badge == null) return;

        Font old = g.getFont();
        Font compact = old.deriveFont(Font.PLAIN, Math.max(6f, old.getSize2D() - 5f));
        FontMetrics metrics = g.getFontMetrics(compact);
        int tapSize = 8;
        int padding = 2;
        int gap = 2;
        int contentWidth = 0;
        if (badge.tap()) contentWidth += tapSize;
        if (!badge.manaCost().isBlank()) {
            if (contentWidth > 0) contentWidth += gap;
            contentWidth += miniManaCostPainter.width(badge.manaCost());
        }
        if (!badge.textCost().isBlank()) {
            if (contentWidth > 0) contentWidth += gap;
            contentWidth += metrics.stringWidth(badge.textCost());
        }
        if (!badge.manaOptions().isEmpty()) {
            contentWidth += manaOptionsWidth(g, badge.manaOptions(), compact);
        }
        if (contentWidth == 0) return;

        int width = padding * 2 + contentWidth;
        int height = Math.max(12, tapSize + padding * 2);
        int x = leftX - 5;
        int y = topY;

        Color base = blend(colorOr("TextArea.background", Color.WHITE),
                colorOr("List.selectionBackground", new Color(0x6D7F9B)), .18f);
        Shape chip = new RoundRectangle2D.Float(x, y, width, height, 10, 10);
        g.setColor(base);
        g.fill(chip);
        g.setColor(blend(base, Color.BLACK, .28f));
        g.draw(chip);

        int cursor = x + padding;
        if (badge.tap()) {
            if (!svgAssets.paint(g, "/svg/tap.svg", cursor, y + padding, tapSize, tapSize)) {
                paintFallbackSymbol(g, "T", cursor, y + padding, tapSize);
            }
            cursor += tapSize;
        }
        if (!badge.manaCost().isBlank()) {
            if (cursor > x + padding) cursor += gap;
            miniManaCostPainter.paint(g, badge.manaCost(), cursor,
                    y + (height - 8) / 2, base);
            cursor += miniManaCostPainter.width(badge.manaCost());
        }
        if (!badge.textCost().isBlank()) {
            if (cursor > x + padding) cursor += gap;
            g.setColor(contrast(base));
            g.setFont(compact);
            g.drawString(badge.textCost(), cursor,
                    y + (height - metrics.getHeight()) / 2 + metrics.getAscent());
            cursor += metrics.stringWidth(badge.textCost());
        }
        if (!badge.manaOptions().isEmpty()) {
            paintManaOptions(g, badge.manaOptions(), cursor, y, height, compact, base);
        }
        g.setFont(old);
    }

    private int manaOptionsWidth(Graphics2D g, List<String> options, Font font) {
        FontMetrics metrics = g.getFontMetrics(font);
        int width = metrics.stringWidth(": ");
        for (int i = 0; i < options.size(); i++) {
            width += miniManaCostPainter.width("{" + options.get(i) + "}");
            if (i + 1 < options.size()) {
                width += metrics.stringWidth(" | ");
            }
        }
        return width;
    }

    private void paintManaOptions(Graphics2D g, List<String> options, int x, int y,
                                  int height, Font font, Color base) {
        Font old = g.getFont();
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        int baseline = y + (height - metrics.getHeight()) / 2 + metrics.getAscent();

        g.setColor(contrast(base));
        g.drawString(": ", x, baseline);
        int cursor = x + metrics.stringWidth(": ");

        for (int i = 0; i < options.size(); i++) {
            String manaCost = "{" + options.get(i) + "}";
            miniManaCostPainter.paint(g, manaCost, cursor,
                    y + (height - 8) / 2, base);
            cursor += miniManaCostPainter.width(manaCost);

            if (i + 1 < options.size()) {
                g.setColor(contrast(base));
                g.drawString(" | ", cursor, baseline);
                cursor += metrics.stringWidth(" | ");
            }
        }
        g.setFont(old);
    }

    private ActivatedAbilityBadge activatedAbilityBadge(CardInfo card) {
        if (card == null) return null;
        String oracle = card.effectiveOracleText();
        if (oracle == null || oracle.isBlank()) return null;

        for (String line : oracle.split("\\R")) {
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String cost = line.substring(0, colon).strip();
            if (cost.isBlank() || cost.startsWith("When ") || cost.startsWith("Whenever ")
                    || cost.startsWith("At ")) continue;

            boolean tap = cost.contains("{T}");
            String effect = line.substring(colon + 1);
            boolean manaAbility = tap && effect.toLowerCase(Locale.ROOT)
                    .matches(".*\\badd\\b.*");
            String manaCost = manaSymbols(cost, false);
            String textCost = compactNonManaAbilityCost(cost);
            List<String> manaOptions = manaAbility ? producedManaOptions(effect) : List.of();
            return new ActivatedAbilityBadge(manaCost, textCost, tap, manaOptions);
        }
        return null;
    }

    private String manaSymbols(String text, boolean colorsOnly) {
        Matcher matcher = MANA.matcher(text == null ? "" : text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String symbol = matcher.group(1).toUpperCase(Locale.ROOT);
            if ("T".equals(symbol) || "Q".equals(symbol)) continue;
            if (colorsOnly && !symbol.matches("[WUBRGC]")) continue;
            result.append('{').append(symbol).append('}');
        }
        return result.toString();
    }

    private List<String> producedManaOptions(String effect) {
        Matcher matcher = MANA.matcher(effect == null ? "" : effect);
        LinkedHashSet<String> result = new LinkedHashSet<>();
        while (matcher.find()) {
            String symbol = matcher.group(1).toUpperCase(Locale.ROOT);
            if (symbol.matches("[WUBRGC]")) result.add(symbol);
        }
        return List.copyOf(result);
    }

    private String compactNonManaAbilityCost(String cost) {
        String compact = MANA.matcher(cost == null ? "" : cost).replaceAll("")
                .replace(",", "")
                .replace("(", "")
                .replace(")", "")
                .replace(":", "")
                .replaceAll("\\s+", "")
                .strip();
        return compact.length() <= 5 ? compact : "";
    }

    private void paintPowerToughnessChip(Graphics2D g, PowerToughnessFragment pt,
                                         int x, int topY, int lineHeight) {
        FontMetrics fm = g.getFontMetrics(getFont());
        String value = pt.power() + "/" + pt.toughness();
        Font old = g.getFont();
        Font compact = old.deriveFont(Font.PLAIN, Math.max(7f, old.getSize2D() - 5f));
        FontMetrics compactMetrics = g.getFontMetrics(compact);
        int overlap = 8;
        x -= overlap;
        int width = compactMetrics.stringWidth(value) + 7;
        int height = Math.max(11, compactMetrics.getHeight()) + 2;
        int y = topY + lineHeight - height - 4;
        Color base = blend(colorOr("TextArea.background", Color.WHITE),
                new Color(0x9D785A), .24f);
        Shape chip = new RoundRectangle2D.Float(x, y, width, height, CHIP_ARC, CHIP_ARC);
        g.setColor(base);
        g.fill(chip);
        g.setColor(blend(base, Color.BLACK, .30f));
        g.draw(chip);
        g.setColor(contrast(base));
        g.setFont(compact);
        g.drawString(value, x + 4,
                y + (height - compactMetrics.getHeight()) / 2 + compactMetrics.getAscent());
        g.setFont(old);
    }

    private void paintKeywordChip(Graphics2D g, KeywordFragment keyword,
                                  int x, int topY, int lineHeight) {
        FontMetrics fm = g.getFontMetrics(getFont());
        int width = fragmentWidth(g, keyword);
        int height = fm.getHeight() + CHIP_Y_PADDING * 2;
        int y = topY + (lineHeight - height) / 2;
        Color base = blend(colorOr("TextArea.background", Color.WHITE),
                colorOr("List.selectionBackground", new Color(0x6D7F9B)), .14f);
        Shape chip = new RoundRectangle2D.Float(x, y, width, height, CHIP_ARC, CHIP_ARC);
        g.setColor(base);
        g.fill(chip);
        g.setColor(blend(base, Color.BLACK, .24f));
        g.draw(chip);
        int iconY = topY + (lineHeight - SYMBOL_SIZE) / 2;
        paintKeyword(g, keyword.keyword(), x + CHIP_X_PADDING, iconY);
        g.setColor(contrast(base));
        g.drawString(keyword.label(), x + CHIP_X_PADDING + SYMBOL_SIZE + 4,
                topY + (lineHeight - fm.getHeight()) / 2 + fm.getAscent());
    }

    private void paintMana(Graphics2D g, String symbol, int x, int y) {
        String normalized = normalizeSymbol(symbol).replace("/", "_");
        if (svgAssets.paint(g, "/mana-svg/" + normalized + ".svg",
                x, y, SYMBOL_SIZE, SYMBOL_SIZE)) return;
        paintFallbackSymbol(g, symbol, x, y, SYMBOL_SIZE);
    }

    private void paintKeyword(Graphics2D g, String keyword, int x, int y) {
        String resource = switch (keyword.toLowerCase(Locale.ROOT)) {
            case "double strike" -> "doublestrike";
            case "first strike" -> "firststrike";
            default -> keyword.toLowerCase(Locale.ROOT).replace(' ', '-');
        };
        if (svgAssets.paint(g, "/keyword-svg/ability-" + resource + ".svg",
                x, y, SYMBOL_SIZE, SYMBOL_SIZE)) return;
        paintFallbackSymbol(g, keyword.substring(0, 1).toUpperCase(Locale.ROOT),
                x, y, SYMBOL_SIZE);
    }

    private void paintFallbackSymbol(Graphics2D g, String text, int x, int y, int size) {
        Color fill = blend(colorOr("TextArea.background", Color.WHITE),
                colorOr("Label.foreground", Color.DARK_GRAY), .12f);
        g.setColor(fill);
        g.fillOval(x, y, size, size);
        g.setColor(blend(fill, Color.BLACK, .35f));
        g.drawOval(x, y, size, size);
        Font old = g.getFont();
        g.setFont(old.deriveFont(Font.BOLD, text.length() > 2 ? 8f : 10f));
        FontMetrics metrics = g.getFontMetrics();
        String clipped = text.length() > 3 ? text.substring(0, 3) : text;
        g.drawString(clipped, x + (size - metrics.stringWidth(clipped)) / 2,
                y + (size - metrics.getHeight()) / 2 + metrics.getAscent());
        g.setFont(old);
    }

    private List<String> manaTokens(String cost) {
        if (cost == null || cost.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        Matcher m = MANA.matcher(cost);
        while (m.find()) result.add(m.group(1));
        return result;
    }

    private String normalizeSymbol(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private String cardTypeResource(CardInfo card) {
        if (card == null) return null;
        String type = card.effectiveTypeLine();
        if (type == null || type.isBlank()) return null;
        if (type.contains("Land")) return "land";
        if (type.contains("Creature")) return "creature";
        if (type.contains("Planeswalker")) return "planeswalker";
        if (type.contains("Artifact")) return "artifact";
        if (type.contains("Enchantment")) return "enchantment";
        if (type.contains("Instant")) return "instant";
        if (type.contains("Sorcery")) return "sorcery";
        return null;
    }

    private Color cardColor(CardInfo card) {
        List<String> colors = card.getColors() == null || card.getColors().isEmpty()
                ? card.getColorIdentity() : card.getColors();
        if (colors == null || colors.isEmpty()) {
            String type = card.effectiveTypeLine();
            if (type != null && type.contains("Land")) return new Color(0xC9B18A);
            return new Color(0xC7CBD1);
        }
        if (colors.size() > 1) return new Color(0xD6B85A);
        return switch (colors.get(0).toUpperCase(Locale.ROOT)) {
            case "W" -> new Color(0xEEE6C8);
            case "U" -> new Color(0x8CB8D8);
            case "B" -> new Color(0x8E8791);
            case "R" -> new Color(0xD9957E);
            case "G" -> new Color(0x8FBE91);
            default -> new Color(0xC7CBD1);
        };
    }

    private Color contrast(Color color) {
        double luminance = .299 * color.getRed() + .587 * color.getGreen() + .114 * color.getBlue();
        return luminance < 128 ? Color.WHITE : new Color(0x202020);
    }

    private Color blend(Color a, Color b, float amount) {
        float n = Math.max(0, Math.min(1, amount));
        return new Color(
                Math.round(a.getRed() * (1 - n) + b.getRed() * n),
                Math.round(a.getGreen() * (1 - n) + b.getGreen() * n),
                Math.round(a.getBlue() * (1 - n) + b.getBlue() * n),
                Math.round(a.getAlpha() * (1 - n) + b.getAlpha() * n));
    }

    private List<BoardPermanentSnapshot> roots(List<BoardPermanentSnapshot> battlefield) {
        Set<Long> ids = new HashSet<>();
        battlefield.forEach(p -> ids.add(p.getLogicalObjectId()));
        return battlefield.stream()
                .filter(p -> p.getAttachedToLogicalObjectId() == null
                        || !ids.contains(p.getAttachedToLogicalObjectId()))
                .toList();
    }

    private List<BoardPermanentSnapshot> attachmentsOf(List<BoardPermanentSnapshot> battlefield, long hostId) {
        return battlefield.stream()
                .filter(p -> p.getAttachedToLogicalObjectId() != null
                        && p.getAttachedToLogicalObjectId() == hostId)
                .toList();
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
        g.setFont(getFont());
        configure(g);
        int y = OUTER_PADDING;
        int contentWidth = Math.max(260, width - OUTER_PADDING * 2);
        Integer previousTurn = null;
        for (GameEvent event : model.snapshot()) {
            if (event.getTurnNumber() != null && !event.getTurnNumber().equals(previousTurn)) {
                y = paintTurnHeader(g, event, y, contentWidth);
                previousTurn = event.getTurnNumber();
            }
            y = event.getTurnSnapshot().isEmpty()
                    ? paintEvent(g, event, y, contentWidth, false)
                    : paintTurnSnapshot(g, event, y, contentWidth, false);
        }
        g.dispose();
        Dimension current = getPreferredSize();
        setPreferredSize(new Dimension(Math.max(720, current.width), Math.max(300, y + OUTER_PADDING)));
        revalidate();
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
        CardInfo card = hit.card();
        JWindow window = new JWindow(SwingUtilities.getWindowAncestor(this));
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(colorOr("Separator.foreground", Color.GRAY)),
                BorderFactory.createEmptyBorder(9, 9, 9, 9)));

        List<String> urls = card.previewImageUrls();
        int imageCount = Math.max(1, urls.size());
        JPanel images = new JPanel();
        images.setOpaque(false);
        images.setLayout(new BoxLayout(images, BoxLayout.X_AXIS));
        List<JLabel> labels = new ArrayList<>();
        for (int index = 0; index < imageCount; index++) {
            JLabel image = new JLabel("Loading image…", SwingConstants.CENTER);
            image.setPreferredSize(previewImageSize(card, imageCount));
            labels.add(image);
            images.add(image);
            if (index + 1 < imageCount) images.add(Box.createHorizontalStrut(8));
        }
        panel.add(new PreviewCardChip(hit.card(), hit.permanent()), BorderLayout.NORTH);
        panel.add(images, BorderLayout.CENTER);

        window.setContentPane(panel);
        window.pack();
        Point screen = mouse.getLocationOnScreen();
        Rectangle screenBounds = getGraphicsConfiguration().getBounds();
        int px = Math.min(screen.x + 18, screenBounds.x + screenBounds.width - window.getWidth() - 8);
        int py = Math.min(screen.y + 18, screenBounds.y + screenBounds.height - window.getHeight() - 8);
        window.setLocation(px, py);
        window.setVisible(true);
        previewWindow = window;

        for (int index = 0; index < imageCount; index++) {
            int imageIndex = index;
            JLabel image = labels.get(index);
            imageCache.get(card, imageIndex).thenAccept(optional -> SwingUtilities.invokeLater(() -> {
                if (previewWindow != window || !window.isVisible()) return;
                if (optional.isPresent()) {
                    BufferedImage raw = optional.get();
                    if (isSplitLayout(card) && imageCount == 1) raw = rotateClockwise(raw);
                    Dimension target = previewImageSize(card, imageCount);
                    int w = target.width;
                    int h = Math.max(1, raw.getHeight() * w / raw.getWidth());
                    if (h > target.height) {
                        h = target.height;
                        w = Math.max(1, raw.getWidth() * h / raw.getHeight());
                    }
                    image.setText("");
                    image.setIcon(new ImageIcon(raw.getScaledInstance(w, h, Image.SCALE_SMOOTH)));
                    window.pack();
                } else {
                    image.setText("No image available");
                }
            }));
        }
    }

    private Dimension previewImageSize(CardInfo card, int imageCount) {
        if (isSplitLayout(card) && imageCount == 1) return new Dimension(340, 244);
        return imageCount > 1 ? new Dimension(220, 307) : new Dimension(244, 340);
    }

    private boolean isSplitLayout(CardInfo card) {
        String layout = card == null ? "" : nullToEmpty(card.getLayout()).toLowerCase(Locale.ROOT);
        return layout.equals("split") || layout.equals("aftermath");
    }

    private BufferedImage rotateClockwise(BufferedImage source) {
        BufferedImage rotated = new BufferedImage(
                source.getHeight(), source.getWidth(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = rotated.createGraphics();
        try {
            configure(g);
            g.translate(rotated.getWidth(), 0);
            g.rotate(Math.PI / 2);
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rotated;
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
                int width = fragmentWidth(g, fragment);
                int x = Math.max(7, (getWidth() - width) / 2);
                paintFragment(g, fragment, x, 2, getHeight() - 4, null, false);
            } finally {
                g.dispose();
            }
        }
    }

    private void hidePreview() {
        if (previewWindow != null) { previewWindow.dispose(); previewWindow = null; }
    }

    private void selectTurnAt(MouseEvent mouse) {
        if (coachingActions == null) return;
        TurnHitbox hit = turnAt(mouse.getPoint());
        if (hit == null) return;

        int turn = hit.turnNumber();
        if (mouse.isShiftDown() && selectionAnchorTurn != null) {
            int from = Math.min(selectionAnchorTurn, turn);
            int to = Math.max(selectionAnchorTurn, turn);
            if (!mouse.isControlDown() && !mouse.isMetaDown()) selectedTurns.clear();
            for (int value = from; value <= to; value++) selectedTurns.add(value);
        } else if (mouse.isControlDown() || mouse.isMetaDown()) {
            if (!selectedTurns.remove(turn)) selectedTurns.add(turn);
            selectionAnchorTurn = turn;
        } else {
            selectedTurns.clear();
            selectedTurns.add(turn);
            selectionAnchorTurn = turn;
        }
        repaint();
    }

    private void showContextMenu(MouseEvent mouse) {
        if (coachingActions == null) {
            if (SwingUtilities.isRightMouseButton(mouse)) nameAbilityAt(mouse.getPoint());
            return;
        }

        TurnHitbox hit = turnAt(mouse.getPoint());
        Integer turn = hit == null ? eventTurnAt(mouse.getPoint()) : hit.turnNumber();
        if (turn != null && !selectedTurns.contains(turn)) {
            selectedTurns.clear();
            selectedTurns.add(turn);
            selectionAnchorTurn = turn;
            repaint();
        }

        JPopupMenu menu = new JPopupMenu();
        addContextItem(menu, "Ask about this match", CoachingScope.MATCH, Set.of(), null);
        addContextItem(menu, "Ask about this game", CoachingScope.GAME, Set.of(), null);
        if (turn != null) {
            addContextItem(menu, "Ask about turn " + turn, CoachingScope.TURN,
                    Set.of(turn), null);
        }
        if (!selectedTurns.isEmpty()) {
            addContextItem(menu, selectedTurns.size() == 1
                            ? "Ask about selected turn"
                            : "Ask about selected turns " + compactTurns(selectedTurns),
                    CoachingScope.SELECTED_TURNS, Set.copyOf(selectedTurns), null);
        }

        menu.addSeparator();
        JMenu standard = new JMenu("Standard questions");
        addStandardQuestions(standard, turn);
        menu.add(standard);

        EventHitbox event = eventAt(mouse.getPoint());
        if (event != null && event.event().getAbility() != null) {
            menu.addSeparator();
            JMenuItem nameAbility = new JMenuItem("Name this ability…");
            nameAbility.addActionListener(ignored -> nameAbilityAt(mouse.getPoint()));
            menu.add(nameAbility);
        }
        menu.show(this, mouse.getX(), mouse.getY());
    }

    private void addStandardQuestions(JMenu menu, Integer turn) {
        addContextItem(menu, "What deck is my opponent using?",
                CoachingScope.MATCH, Set.of(),
                "What deck is my opponent using, and what should I know about decks of this kind?");
        addContextItem(menu, "Was my starting hand keep correct?",
                CoachingScope.GAME, Set.of(),
                "Was keeping my starting hand correct? Explain the important factors and alternatives.");
        if (turn != null) {
            addContextItem(menu, "Could I have played this turn differently?",
                    CoachingScope.TURN, Set.of(turn),
                    "Could I have played this turn differently? Focus on realistic alternatives using only known information.");
            addContextItem(menu, "Review attacks and blocks",
                    CoachingScope.TURN, Set.of(turn),
                    "Were the attacks and blocks on this turn correct? Explain better lines, if any.");
        }
        if (!selectedTurns.isEmpty()) {
            addContextItem(menu, "Review selected turns",
                    CoachingScope.SELECTED_TURNS, Set.copyOf(selectedTurns),
                    "Review these turns as one sequence. Identify the most important decision and a better line, if one existed.");
        }
    }

    private void addContextItem(JComponent menu, String label, CoachingScope scope,
                                Set<Integer> turns, String question) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(ignored -> coachingActions.request(
                new CoachingRequest(scope, turns, question)));
        menu.add(item);
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

    private Integer eventTurnAt(Point point) {
        EventHitbox hit = eventAt(point);
        return hit == null ? null : hit.event().getTurnNumber();
    }

    private String compactTurns(Collection<Integer> turns) {
        if (turns.isEmpty()) return "";
        if (turns.size() == 1) return Integer.toString(turns.iterator().next());
        return turns.iterator().next() + "–" + ((NavigableSet<Integer>) new TreeSet<>(turns)).last();
    }

    private void nameAbilityAt(Point point) {
        EventHitbox hit = eventHitboxes.stream()
                .filter(value -> value.bounds().contains(point)).findFirst().orElse(null);
        if (hit == null || hit.event().getAbility() == null) return;
        var ability = hit.event().getAbility();
        String current = abilityNames.find(ability.getSourceGrpId(), ability.getAbilityGrpId());
        String prompt = "Name this " + ability.getKind() + " ability for future games:\n"
                + ability.getSourceName() + "\nArena IDs "
                + ability.getSourceGrpId() + ":" + ability.getAbilityGrpId();
        String name = JOptionPane.showInputDialog(this, prompt, current);
        if (name == null) return;
        abilityNames.put(ability.getSourceGrpId(), ability.getAbilityGrpId(), name);
    }

    @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(900, 600); }
    @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 22; }
    @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(44, visibleRect.height - 44);
    }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }

    private record SnapshotRow(
            String iconResource,
            String text,
            String suffix,
            boolean heading,
            CardInfo card,
            BoardPermanentSnapshot permanent,
            List<CardInfo> cards,
            int cardColumns,
            List<BoardPermanentSnapshot> permanents,
            int permanentColumns) {
        private SnapshotRow(String iconResource, String text, String suffix, boolean heading) {
            this(iconResource, text, suffix, heading, null, null, List.of(), 1, List.of(), 1);
        }

        private SnapshotRow(String iconResource, String text, String suffix,
                            boolean heading, CardInfo card) {
            this(iconResource, text, suffix, heading, card, null, List.of(), 1, List.of(), 1);
        }

        private static SnapshotRow cardGrid(List<CardInfo> cards, int columns) {
            return new SnapshotRow(null, "", "", false, null, null,
                    List.copyOf(cards), columns, List.of(), 1);
        }

        private static SnapshotRow permanentGrid(List<BoardPermanentSnapshot> permanents,
                                                 int columns) {
            return new SnapshotRow(null, "", "", false, null, null,
                    List.of(), 1, List.copyOf(permanents), columns);
        }
    }

    private sealed interface Fragment permits TextFragment, CardFragment, ManaFragment,
            PowerToughnessFragment, KeywordFragment {}
    private record TextFragment(String text) implements Fragment {}
    private record CardFragment(CardInfo card, String label, String stateLabel,
                                BoardPermanentSnapshot permanent) implements Fragment {}
    private record ManaFragment(String symbol) implements Fragment {}
    private record PowerToughnessFragment(String power, String toughness) implements Fragment {}
    private record KeywordFragment(String keyword, String label) implements Fragment {}
    private record MatchToken(int start, int end, Fragment fragment) {}
    private record Match(int start, int end, CardInfo card, String label) {}
    private record CardMatchCandidate(CardInfo card, String label) {}
    private record RichLayout(int height) {}
    private record ActivatedAbilityBadge(String manaCost, String textCost,
                                         boolean tap, List<String> manaOptions) {}
    private record CardHitbox(Rectangle bounds, CardInfo card, GameEvent event,
                              BoardPermanentSnapshot permanent) {}
    private record EventHitbox(Rectangle bounds, GameEvent event) {}
    private record TurnHitbox(Rectangle bounds, int turnNumber) {}

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

    private static final class PendingMessage {
        private final LogMessageInterface message;
        private ModelObject modelObject;
        private Throwable error;
        private boolean completed;
        private PendingMessage(LogMessageInterface message) { this.message = message; }
        private void complete(ModelObject modelObject, Throwable error) {
            this.modelObject = modelObject; this.error = error; this.completed = true;
        }
        private LogMessageInterface message() { return message; }
        private ModelObject modelObject() { return modelObject; }
        private Throwable error() { return error; }
        private boolean completed() { return completed; }
    }
}
