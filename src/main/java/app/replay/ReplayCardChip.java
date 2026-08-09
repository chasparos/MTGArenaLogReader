package app.replay;

import app.model.card.CardInfo;
import app.model.event.GameEvent;
import app.model.game.BoardPermanentSnapshot;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Reusable Swing wrapper around the replay card-fragment painter.
 *
 * <p>The component paints at a logical replay-chip size and scales the vector/SVG/text surface to
 * the requested presentation size. This keeps replay and planning views visually consistent while
 * allowing larger candidate chips without rasterizing text.</p>
 */
public class ReplayCardChip extends JComponent {
    private static final float BASE_WIDTH = 320f;
    private static final float BASE_HEIGHT = 38f;

    private final CardInfo card;
    private final String stateLabel;
    private final BoardPermanentSnapshot permanent;
    private final float presentationScale;
    private boolean selected;
    private Color outlineColor;
    private final ReplayFragmentRenderer renderer;

    public ReplayCardChip(CardInfo card) {
        this(card, "", null, false, 1f);
    }

    public ReplayCardChip(CardInfo card, boolean selected) {
        this(card, "", null, selected, 1f);
    }

    public ReplayCardChip(CardInfo card, boolean selected, float presentationScale) {
        this(card, "", null, selected, presentationScale);
    }

    public ReplayCardChip(CardInfo card, String stateLabel,
                          BoardPermanentSnapshot permanent, boolean selected) {
        this(card, stateLabel, permanent, selected, 1f);
    }

    public ReplayCardChip(CardInfo card, String stateLabel,
                          BoardPermanentSnapshot permanent, boolean selected,
                          float presentationScale) {
        this.card = card;
        this.stateLabel = stateLabel == null ? "" : stateLabel;
        this.permanent = permanent;
        this.selected = selected;
        this.presentationScale = Math.max(.75f, presentationScale);
        setOpaque(false);
        setPreferredSize(new Dimension(
                Math.round(BASE_WIDTH * this.presentationScale),
                Math.round(BASE_HEIGHT * this.presentationScale)));
        setMinimumSize(new Dimension(
                Math.round(180f * this.presentationScale),
                Math.round(BASE_HEIGHT * this.presentationScale)));
        if (card != null && card.getName() != null && !card.getName().isBlank()) {
            setToolTipText(card.getName());
        }
        this.renderer = new ReplayFragmentRenderer(new ReplayFragmentRenderer.Host() {
            @Override public Font font() { return effectiveFont(); }
            @Override public Color foreground() { return effectiveForeground(); }
            @Override public Color colorOr(String key, Color fallback) {
                Color color = UIManager.getColor(key);
                return color == null ? fallback : color;
            }
            @Override public boolean isHovered(Rectangle bounds) {
                return ReplayCardChip.this.selected;
            }
            @Override public void registerHitbox(Rectangle bounds, CardInfo renderedCard,
                                                 GameEvent event,
                                                 BoardPermanentSnapshot renderedPermanent) {
                // A reusable chip has no replay hit-map; containing views own interaction.
            }
        });
    }

    public CardInfo card() {
        return card;
    }

    /** Updates the shared replay hover/selection treatment without recreating the chip. */
    public void setSelected(boolean selected) {
        if (this.selected == selected) return;
        this.selected = selected;
        repaint();
    }

    public boolean selected() {
        return selected;
    }

    /** Paints a precise outline following the replay card-chip geometry. */
    public void paintColoredOutline(Color color) {
        if (java.util.Objects.equals(outlineColor, color)) return;
        outlineColor = color;
        repaint();
    }

    public Color outlineColor() {
        return outlineColor;
    }

    public float presentationScale() {
        return presentationScale;
    }

    /**
     * Shrinks the component to the vector chip's measured content width instead of reserving the
     * historical fixed replay width. Candidate flow layouts use this to avoid large transparent
     * gutters between otherwise compact chips.
     */
    public void compactToContentWidth() {
        if (card == null) return;
        BufferedImage probe = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = probe.createGraphics();
        try {
            applyQualityHints(g);
            g.setFont(effectiveFont());
            String label = card.getName() == null || card.getName().isBlank()
                    ? "Unknown card" : card.getName();
            CardFragment fragment = new CardFragment(card, label, stateLabel, permanent);
            int logicalWidth = renderer.width(g, fragment) + 10;
            int width = Math.max(Math.round(110f * presentationScale),
                    Math.round(logicalWidth * presentationScale));
            int height = Math.round(BASE_HEIGHT * presentationScale);
            Dimension compact = new Dimension(width, height);
            setPreferredSize(compact);
            setMinimumSize(compact);
            setMaximumSize(compact);
        } finally {
            g.dispose();
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            applyQualityHints(g);
            double scale = getHeight() <= 0
                    ? presentationScale
                    : Math.max(.5d, getHeight() / (double) BASE_HEIGHT);
            g.scale(scale, scale);

            int logicalHeight = Math.max(1, (int) Math.round(getHeight() / scale));
            if (card == null) {
                g.setColor(effectiveForeground());
                g.setFont(effectiveFont());
                ReplayFragmentRenderer.drawGlyphText(
                        g, "Unknown card", 8f,
                        (logicalHeight + g.getFontMetrics().getAscent()) / 2f - 2f);
                return;
            }
            String label = card.getName() == null || card.getName().isBlank()
                    ? "Unknown card" : card.getName();
            CardFragment fragment = new CardFragment(card, label, stateLabel, permanent);
            renderer.paint(g, fragment, 4, 2, logicalHeight - 4, null, false);
            if (outlineColor != null) {
                renderer.paintCardOutline(g, fragment, 4, 2, logicalHeight - 4,
                        outlineColor, 2.2f);
            }
        } finally {
            g.dispose();
        }
    }

    /**
     * Builds a compact translucent drag ghost from the same vector replay-chip painter used on
     * candidate rows. Up to three cards are shown; an additional-count badge covers larger drags.
     */
    public static Image createDragImage(List<CardInfo> cards) {
        if (cards == null || cards.isEmpty()) return null;
        int shown = Math.min(3, cards.size());
        int chipWidth = 220;
        int chipHeight = 30;
        int overlap = 8;
        int width = chipWidth + (shown - 1) * overlap;
        int height = chipHeight + (shown - 1) * overlap;
        BufferedImage image = new BufferedImage(width + 18, height + 18, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            applyQualityHints(g);
            g.setComposite(AlphaComposite.SrcOver.derive(.92f));
            for (int index = shown - 1; index >= 0; index--) {
                ReplayCardChip chip = new ReplayCardChip(cards.get(index), false,
                        chipHeight / BASE_HEIGHT);
                chip.setSize(chipWidth, chipHeight);
                Graphics2D cg = (Graphics2D) g.create(index * overlap, index * overlap,
                        chipWidth, chipHeight);
                try {
                    chip.paint(cg);
                } finally {
                    cg.dispose();
                }
            }
            if (cards.size() > shown) {
                String label = "+" + (cards.size() - shown);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
                FontMetrics metrics = g.getFontMetrics();
                int badgeWidth = Math.max(20, metrics.stringWidth(label) + 10);
                int x = image.getWidth() - badgeWidth - 2;
                int y = image.getHeight() - 20;
                g.setColor(new Color(0xCC202328, true));
                g.fillRoundRect(x, y, badgeWidth, 18, 9, 9);
                g.setColor(Color.WHITE);
                g.drawString(label, x + (badgeWidth - metrics.stringWidth(label)) / 2,
                        y + 13);
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    private static void applyQualityHints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    private Font effectiveFont() {
        Font font = getFont();
        if (font != null) return font;
        Font ui = UIManager.getFont("Label.font");
        return ui == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 12) : ui;
    }

    private Color effectiveForeground() {
        Color color = getForeground();
        if (color != null) return color;
        Color ui = UIManager.getColor("Label.foreground");
        return ui == null ? Color.DARK_GRAY : ui;
    }
}
