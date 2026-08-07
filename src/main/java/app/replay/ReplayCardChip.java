package app.replay;

import app.model.card.CardInfo;
import app.model.event.GameEvent;
import app.model.game.BoardPermanentSnapshot;

import javax.swing.*;
import java.awt.*;

/**
 * Reusable Swing wrapper around the replay card-fragment painter.
 *
 * <p>This keeps compact card presentation consistent between replay hover previews
 * and other workspaces without copying replay painting rules into those UIs.</p>
 */
public class ReplayCardChip extends JComponent {
    private final CardInfo card;
    private final String stateLabel;
    private final BoardPermanentSnapshot permanent;
    private final boolean selected;
    private final ReplayFragmentRenderer renderer;

    public ReplayCardChip(CardInfo card) {
        this(card, "", null, false);
    }

    public ReplayCardChip(CardInfo card, boolean selected) {
        this(card, "", null, selected);
    }

    public ReplayCardChip(CardInfo card, String stateLabel,
                          BoardPermanentSnapshot permanent, boolean selected) {
        this.card = card;
        this.stateLabel = stateLabel == null ? "" : stateLabel;
        this.permanent = permanent;
        this.selected = selected;
        setOpaque(false);
        setPreferredSize(new Dimension(320, 38));
        setMinimumSize(new Dimension(180, 38));
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
            @Override public boolean isHovered(Rectangle bounds) { return ReplayCardChip.this.selected; }
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

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            if (card == null) {
                g.setColor(effectiveForeground());
                g.setFont(effectiveFont());
                g.drawString("Unknown card", 8,
                        (getHeight() + g.getFontMetrics().getAscent()) / 2 - 2);
                return;
            }
            String label = card.getName() == null || card.getName().isBlank()
                    ? "Unknown card" : card.getName();
            CardFragment fragment = new CardFragment(card, label, stateLabel, permanent);
            renderer.paint(g, fragment, 4, 2, getHeight() - 4, null, false);
        } finally {
            g.dispose();
        }
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
