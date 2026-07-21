package app.replay;


import app.model.card.CardInfo;
import app.model.event.GameEvent;
import app.model.game.BoardPermanentSnapshot;
import app.model.game.PlayerTurnSnapshot;
import app.model.log.LogMessageInterface;
import app.model.log.ModelObject;
import app.model.session.GameModel;
import app.model.*;
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
    private static final int CHIP_X_PADDING = 18;
    private static final int CHIP_Y_PADDING = 3;
    private static final int CHIP_ARC = 14;
    private static final int SYMBOL_SIZE = 12;
    private static final Pattern MANA = Pattern.compile("\\{([^}]+)}");
    private static final Pattern POWER_TOUGHNESS = Pattern.compile(
            "\\(?(-?\\d+|\\*)/(-?\\d+|\\*)\\)?");
    private static final List<String> KEYWORDS = List.of(
            "deathtouch", "defender", "double strike", "first strike", "flying",
            "haste", "hexproof", "indestructible", "lifelink", "menace",
            "reach", "trample", "vigilance", "ward");

    private final GameModel model;
    private final GameEventProjector projector;
    private final Deque<PendingMessage> pending = new ArrayDeque<>();
    private final List<CardHitbox> cardHitboxes = new ArrayList<>();
    private final List<EventHitbox> eventHitboxes = new ArrayList<>();
    private final AbilityNameStore abilityNames;
    private final BoardStateMonitor boardStateMonitor = new BoardStateMonitor();
    private final CardImageCache imageCache = new CardImageCache(
            Path.of(System.getProperty("user.home"), ".arena-log-viewer", "images"));
    private final SvgAssetRenderer svgAssets = new SvgAssetRenderer();
    private JWindow previewWindow;
    private CardHitbox hovered;

    public GameView(GameModel model) { this(model, new AbilityNameStore()); }

    public GameView(GameModel model, AbilityNameStore abilityNames) {
        this.model = model;
        this.abilityNames = abilityNames;
        this.projector = new GameEventProjector(abilityNames);
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
                if (SwingUtilities.isRightMouseButton(e)) nameAbilityAt(e.getPoint());
            }
        };
        addMouseMotionListener(mouse);
        addMouseListener(mouse);
    }

    public GameModel getModel() { return model; }

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
                    additions.addAll(projector.project(completed.message(), completed.modelObject()));
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
        }
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            configure(g);
            cardHitboxes.clear();
            eventHitboxes.clear();
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
        g.setColor(blend(getBackground(), colorOr("List.selectionBackground", new Color(0x4477AA)), .13f));
        g.fill(new RoundRectangle2D.Float(OUTER_PADDING, y, width, h, 14, 14));
        g.setColor(colorOr("Label.foreground", getForeground()));
        String title = "Turn " + event.getTurnNumber() + "  ·  " + nullToEmpty(event.getActivePlayerName());
        g.drawString(title, OUTER_PADDING + 12, y + 5 + fm.getAscent());
        g.setFont(old);
        return y + h + EVENT_GAP;
    }

    private int paintTurnSnapshot(Graphics2D g, GameEvent event, int y, int width, boolean draw) {
        FontMetrics fm = g.getFontMetrics();
        List<String> lines = snapshotLines(event);
        int boxHeight = lines.size() * fm.getHeight() + CARD_PADDING * 2;
        if (draw) {
            paintPanel(g, y, width, boxHeight, true);
            int baseline = y + CARD_PADDING + fm.getAscent();
            for (int i = 0; i < lines.size(); i++) {
                g.setColor(i == 0 ? colorOr("Label.foreground", getForeground())
                        : colorOr("TextArea.foreground", getForeground()));
                if (i == 0) g.setFont(getFont().deriveFont(Font.BOLD));
                else g.setFont(getFont());
                g.drawString(lines.get(i), OUTER_PADDING + CARD_PADDING, baseline);
                baseline += fm.getHeight();
            }
            g.setFont(getFont());
        }
        return y + boxHeight + EVENT_GAP;
    }

    private List<String> snapshotLines(GameEvent event) {
        List<String> lines = new ArrayList<>();
        lines.add("Start of turn");
        for (PlayerTurnSnapshot player : event.getTurnSnapshot()) {
            StringBuilder summary = new StringBuilder();
            summary.append(nullToEmpty(player.getPlayerName())).append(" — ")
                    .append(player.getLifeTotal() == null ? "life ?" : player.getLifeTotal() + " life");
            if (player.getPoisonCounters() != null && player.getPoisonCounters() > 0)
                summary.append(", ").append(player.getPoisonCounters()).append(" poison");
            summary.append(player.getHandSize() == null ? ", hand ?" : ", " + player.getHandSize() + " cards");
            lines.add(summary.toString());
            if (player.getBattlefield().isEmpty()) {
                lines.add("  Battlefield: empty");
            } else {
                lines.add("  Battlefield:");
                for (BoardPermanentSnapshot permanent : roots(player.getBattlefield())) {
                    addPermanentLines(lines, player.getBattlefield(), permanent, 2);
                }
            }
        }
        return lines;
    }

    private void addPermanentLines(List<String> lines, List<BoardPermanentSnapshot> battlefield,
                                   BoardPermanentSnapshot permanent, int depth) {
        lines.add("  ".repeat(depth) + (depth == 2 ? "• " : "↳ ") + boardPermanentText(permanent));
        for (BoardPermanentSnapshot attached : attachmentsOf(battlefield, permanent.getLogicalObjectId())) {
            addPermanentLines(lines, battlefield, attached, depth + 1);
        }
    }

    private int paintEvent(Graphics2D g, GameEvent event, int y, int width, boolean draw) {
        int contentX = OUTER_PADDING + CARD_PADDING + CONTEXT_WIDTH;
        int maxX = OUTER_PADDING + width - CARD_PADDING;
        RichLayout layout = layoutEvent(g, event, contentX, maxX, y + CARD_PADDING, draw);
        int boxHeight = Math.max(g.getFontMetrics().getHeight(), layout.height()) + CARD_PADDING * 2;
        if (draw) {
            paintPanel(g, y, width, boxHeight, false);
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
        Color panel = colorOr("TextArea.background", Color.WHITE);
        if (snapshot) panel = blend(panel, colorOr("List.selectionBackground", new Color(0x4477AA)), .045f);
        Color border = blend(colorOr("Separator.foreground", new Color(0xAAAAAA)), panel, .30f);
        Shape box = new RoundRectangle2D.Float(OUTER_PADDING, y, width, height, 15, 15);
        g.setColor(panel); g.fill(box);
        g.setColor(border); g.draw(box);
    }

    private RichLayout layoutEvent(Graphics2D g, GameEvent event, int startX, int maxX, int topY, boolean draw) {
        List<Fragment> fragments = fragments(event);
        FontMetrics fm = g.getFontMetrics(getFont());
        int lineHeight = Math.max(fm.getHeight(), SYMBOL_SIZE + 4);
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
        List<CardInfo> cards = event.getCards().stream()
                .filter(Objects::nonNull)
                .filter(c -> c.getName() != null && !c.getName().isBlank())
                .sorted(Comparator.comparingInt((CardInfo c) -> c.getName().length()).reversed())
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
            result.add(new CardFragment(next.card()));
            pos = next.end();
        }
        if (text.isEmpty()) result.add(new TextFragment(""));
        return result;
    }

    private Match nextMatch(String text, int from, List<CardInfo> cards) {
        Match best = null;
        for (CardInfo card : cards) {
            int at = text.indexOf(card.getName(), from);
            if (at < 0) continue;
            Match candidate = new Match(at, at + card.getName().length(), card);
            if (best == null || candidate.start() < best.start()
                    || (candidate.start() == best.start() && candidate.end() > best.end())) best = candidate;
        }
        return best;
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
        CardInfo card = ((CardFragment) fragment).card();
        int mana = manaTokens(card.getManaCost()).size() * (SYMBOL_SIZE + 1);
        return fm.stringWidth(card.getName()) + CHIP_X_PADDING * 2 + mana + (mana > 0 ? 18 : 0);
    }

    private void paintFragment(Graphics2D g, Fragment fragment, int x, int topY, int lineHeight, GameEvent event) {
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

        CardInfo card = ((CardFragment) fragment).card();
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
        g.setFont(getFont().deriveFont(Font.BOLD));
        int textX = x + CHIP_X_PADDING;
        g.drawString(card.getName(), textX, baseline);
        int manaX = textX + fm.stringWidth(card.getName()) + SYMBOL_SIZE;
        for (String symbol : manaTokens(card.getManaCost())) {
            paintMana(g, symbol, manaX, topY + (lineHeight - SYMBOL_SIZE) / 2);
            manaX += SYMBOL_SIZE + 1;
        }
        g.setFont(getFont());
        cardHitboxes.add(new CardHitbox(bounds, card, event));
    }

    private void paintPowerToughnessChip(Graphics2D g, PowerToughnessFragment pt,
                                         int x, int topY, int lineHeight) {
        FontMetrics fm = g.getFontMetrics(getFont());
        String value = pt.power() + "/" + pt.toughness();
        int width = fm.stringWidth(value) + CHIP_X_PADDING * 2;
        int height = fm.getHeight() + CHIP_Y_PADDING * 2;
        int y = topY + (lineHeight - height) / 2;
        Color base = blend(colorOr("TextArea.background", Color.WHITE),
                new Color(0x9D785A), .24f);
        Shape chip = new RoundRectangle2D.Float(x, y, width, height, CHIP_ARC, CHIP_ARC);
        g.setColor(base);
        g.fill(chip);
        g.setColor(blend(base, Color.BLACK, .30f));
        g.draw(chip);
        g.setColor(contrast(base));
        Font old = g.getFont();
        g.setFont(old.deriveFont(Font.BOLD));
        g.drawString(value, x + CHIP_X_PADDING,
                topY + (lineHeight - fm.getHeight()) / 2 + fm.getAscent());
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

    private String boardPermanentText(BoardPermanentSnapshot permanent) {
        StringBuilder text = new StringBuilder(
                permanent.getName() == null || permanent.getName().isBlank()
                        ? "Unknown permanent" : permanent.getName());
        if (permanent.getPower() != null && permanent.getToughness() != null)
            text.append("  ").append(permanent.getPower()).append('/').append(permanent.getToughness());
        if (Boolean.TRUE.equals(permanent.getTapped())) text.append("  · tapped");
        return text.toString();
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
        if (hit != null) showPreview(hit.card(), mouse);
    }

    private void showPreview(CardInfo card, MouseEvent mouse) {
        JWindow window = new JWindow(SwingUtilities.getWindowAncestor(this));
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(colorOr("Separator.foreground", Color.GRAY)),
                BorderFactory.createEmptyBorder(9, 9, 9, 9)));
        JLabel image = new JLabel("Loading image…", SwingConstants.CENTER);
        image.setPreferredSize(new Dimension(244, 340));
        JTextArea details = new JTextArea(cardDetails(card));
        details.setEditable(false); details.setOpaque(false);
        details.setLineWrap(true); details.setWrapStyleWord(true);
        details.setPreferredSize(new Dimension(270, 120));
        panel.add(image, BorderLayout.CENTER); panel.add(details, BorderLayout.SOUTH);
        window.setContentPane(panel); window.pack();
        Point screen = mouse.getLocationOnScreen();
        Rectangle screenBounds = getGraphicsConfiguration().getBounds();
        int px = Math.min(screen.x + 18, screenBounds.x + screenBounds.width - window.getWidth() - 8);
        int py = Math.min(screen.y + 18, screenBounds.y + screenBounds.height - window.getHeight() - 8);
        window.setLocation(px, py); window.setVisible(true);
        previewWindow = window;
        imageCache.get(card).thenAccept(optional -> SwingUtilities.invokeLater(() -> {
            if (previewWindow != window || !window.isVisible()) return;
            if (optional.isPresent()) {
                BufferedImage raw = optional.get();
                int w = 244, h = Math.max(1, raw.getHeight() * w / raw.getWidth());
                image.setText("");
                image.setIcon(new ImageIcon(raw.getScaledInstance(w, h, Image.SCALE_SMOOTH)));
                window.pack();
            } else image.setText("No image available");
        }));
    }

    private String cardDetails(CardInfo card) {
        StringBuilder out = new StringBuilder(card.getName() == null ? "Unknown card" : card.getName());
        if (card.getManaCost() != null && !card.getManaCost().isBlank()) out.append("  ").append(card.getManaCost());
        String typeLine = card.effectiveTypeLine();
        if (typeLine != null && !typeLine.isBlank()) out.append("\n").append(typeLine);
        String oracleText = card.effectiveOracleText();
        if (oracleText != null && !oracleText.isBlank()) out.append("\n").append(oracleText);
        if (card.getPower() != null || card.getToughness() != null)
            out.append("\n").append(card.getPower()).append('/').append(card.getToughness());
        return out.toString();
    }

    private void hidePreview() {
        if (previewWindow != null) { previewWindow.dispose(); previewWindow = null; }
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

    private sealed interface Fragment permits TextFragment, CardFragment, ManaFragment,
            PowerToughnessFragment, KeywordFragment {}
    private record TextFragment(String text) implements Fragment {}
    private record CardFragment(CardInfo card) implements Fragment {}
    private record ManaFragment(String symbol) implements Fragment {}
    private record PowerToughnessFragment(String power, String toughness) implements Fragment {}
    private record KeywordFragment(String keyword, String label) implements Fragment {}
    private record MatchToken(int start, int end, Fragment fragment) {}
    private record Match(int start, int end, CardInfo card) {}
    private record RichLayout(int height) {}
    private record CardHitbox(Rectangle bounds, CardInfo card, GameEvent event) {}
    private record EventHitbox(Rectangle bounds, GameEvent event) {}

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
