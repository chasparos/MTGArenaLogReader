package app.deckplanner.ui;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;

/**
 * Reusable lightweight Swing view for one planning card.
 *
 * <p>The browser supplies immutable display state before each paint. The view never mutates or
 * decorates cached card images; hover, selection, and focus are painted as transient overlays.</p>
 */
public final class CardView extends JComponent {
    private static final Color PLACEHOLDER = new Color(38, 42, 48);
    private static final Color PLACEHOLDER_EDGE = new Color(86, 92, 101);
    private static final Color SELECTED = new Color(255, 196, 64);
    private static final Color FOCUSED = new Color(112, 184, 255);
    private static final Color HOVERED = new Color(255, 255, 255, 120);

    private String name = "Unknown card";
    private BufferedImage image;
    private boolean hovered;
    private boolean selected;
    private boolean focused;

    public CardView() {
        setOpaque(false);
    }

    public void configure(String name,
                          BufferedImage image,
                          boolean hovered,
                          boolean selected,
                          boolean focused) {
        this.name = name == null || name.isBlank() ? "Unknown card" : name;
        this.image = image;
        this.hovered = hovered;
        this.selected = selected;
        this.focused = focused;
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            int width = getWidth();
            int height = getHeight();
            if (image == null) {
                g.setColor(PLACEHOLDER);
                g.fillRoundRect(0, 0, width, height, 14, 14);
                g.setColor(PLACEHOLDER_EDGE);
                g.drawRoundRect(0, 0, width - 1, height - 1, 14, 14);
                g.setColor(Color.WHITE);
                FontMetrics metrics = g.getFontMetrics();
                String label = ellipsize(name, metrics, Math.max(20, width - 20));
                g.drawString(label, (width - metrics.stringWidth(label)) / 2, height / 2);
            } else {
                g.drawImage(image, 0, 0, width, height, null);
            }
            if (hovered) stroke(g, width, height, HOVERED, 2f);
            if (selected) stroke(g, width, height, SELECTED, 4f);
            if (focused) stroke(g, width, height, FOCUSED, 2f);
        } finally {
            g.dispose();
        }
    }

    private static void stroke(Graphics2D g, int width, int height, Color color, float strokeWidth) {
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(strokeWidth));
        g.setColor(color);
        int inset = Math.max(1, Math.round(strokeWidth / 2));
        g.drawRoundRect(inset, inset, width - inset * 2 - 1, height - inset * 2 - 1, 14, 14);
        g.setStroke(old);
    }

    private static String ellipsize(String text, FontMetrics metrics, int width) {
        if (metrics.stringWidth(text) <= width) return text;
        String suffix = "…";
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end) + suffix) > width) end--;
        return text.substring(0, end) + suffix;
    }
}
