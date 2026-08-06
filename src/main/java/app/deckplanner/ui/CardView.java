package app.deckplanner.ui;

import app.replay.SvgAssetRenderer;
import app.ui.AppColors;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.Font;
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
    private static final SvgAssetRenderer SVG = new SvgAssetRenderer();
    private static final Color FOCUSED = new Color(112, 184, 255);
    private static final Color HOVERED = new Color(255, 255, 255, 120);

    private String name = "Unknown card";
    private BufferedImage image;
    private boolean hovered;
    private boolean selected;
    private boolean underConsideration;
    private boolean focused;

    public CardView() {
        setOpaque(false);
    }

    public void configure(String name,
                          BufferedImage image,
                          boolean hovered,
                          boolean selected,
                          boolean underConsideration,
                          boolean focused) {
        this.name = name == null || name.isBlank() ? "Unknown card" : name;
        this.image = image;
        this.hovered = hovered;
        this.selected = selected;
        this.underConsideration = underConsideration;
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
            if (selected) paintSelectedBadge(g, width, height);
            if (underConsideration) paintConsiderationBadge(g, width);
            if (focused) stroke(g, width, height, FOCUSED, 2f);
        } finally {
            g.dispose();
        }
    }


    static Rectangle selectedBadgeBounds(int width, int height) {
        int badgeHeight = 36;
        int badgeWidth = Math.min(Math.max(132, width - 34), 190);
        return new Rectangle(Math.max(0, (width - badgeWidth) / 2), Math.max(0, height - badgeHeight), badgeWidth, badgeHeight);
    }

    static Rectangle considerationBadgeBounds(int width) {
        int size = 38;
        return new Rectangle(Math.max(0, width - size), 0, size, size);
    }

    private static void paintSelectedBadge(Graphics2D g, int width, int height) {
        Rectangle badge = selectedBadgeBounds(width, height);
        Color background = new Color(82, 86, 92, 204);
        Color foreground = new Color(250, 250, 250);
        String text = "selected";
        Font oldFont = g.getFont();
        g.setFont(oldFont.deriveFont(Font.BOLD, Math.max(13f, oldFont.getSize2D() + 2f)));
        FontMetrics metrics = g.getFontMetrics();
        int icon = 20;
        int contentWidth = icon + 7 + metrics.stringWidth(text);
        int contentX = badge.x + (badge.width - contentWidth) / 2;
        g.setColor(background);
        g.fillRoundRect(badge.x, badge.y, badge.width, badge.height + 8, badge.height, badge.height);
        g.setColor(new Color(25, 25, 25, 180));
        g.drawRoundRect(badge.x, badge.y, badge.width - 1, badge.height + 7, badge.height, badge.height);
        SVG.paintTinted(g, "/svg/tap.svg", contentX, badge.y + (badge.height - icon) / 2, icon, icon, foreground);
        g.setColor(foreground);
        g.drawString(text, contentX + icon + 7,
                badge.y + (badge.height - metrics.getHeight()) / 2 + metrics.getAscent());
        g.setFont(oldFont);
    }

    private static void paintConsiderationBadge(Graphics2D g, int width) {
        Rectangle badge = considerationBadgeBounds(width);
        Color background = AppColors.color("Component.focusColor", new Color(0x6B55B5));
        Color foreground = AppColors.color("Label.foreground", Color.WHITE);
        g.setColor(background);
        g.fillOval(badge.x, badge.y, badge.width, badge.height);
        g.setColor(AppColors.blend(background, Color.BLACK, .3f));
        g.drawOval(badge.x, badge.y, badge.width - 1, badge.height - 1);
        SVG.paintTinted(g, "/svg/chaos.svg", badge.x + 8, badge.y + 8,
                badge.width - 16, badge.height - 16, foreground);
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
