package app.replay;

import app.model.card.CardInfo;
import app.model.event.GameEvent;
import app.model.game.BoardPermanentSnapshot;

import javax.swing.*;
import java.awt.*;

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
