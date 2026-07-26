package app.ui;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** Quiet tabs with a raised selected surface and accent underline. */
public final class AppTabbedPaneUI extends BasicTabbedPaneUI {
    public static ComponentUI createUI(JComponent component) {
        return new AppTabbedPaneUI();
    }

    @Override
    protected void installDefaults() {
        super.installDefaults();
        tabInsets = new Insets(7, 13, 7, 13);
        selectedTabPadInsets = new Insets(0, 0, 0, 0);
        contentBorderInsets = new Insets(7, 0, 0, 0);
        tabAreaInsets = new Insets(3, 3, 0, 3);
        tabPane.setOpaque(true);
        tabPane.setBackground(new ColorUIResource(AppColors.color(
                "App.surface", new Color(0x24272C))));
        tabPane.setForeground(new ColorUIResource(AppColors.color(
                "Label.foreground", new Color(0xE5E9EF))));
    }

    @Override
    protected void paintTabBackground(
            Graphics graphics, int placement, int index,
            int x, int y, int width, int height, boolean selected) {
        if (!selected) return;
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(AppColors.color(
                    "App.surfaceRaised", new Color(0x343941)));
            g.fill(new RoundRectangle2D.Float(
                    x + 1, y + 1, width - 2, height - 2, 10, 10));
            g.setColor(AppColors.color(
                    "App.accent", new Color(0x5B94C8)));
            g.fillRoundRect(x + 8, y + height - 3,
                    Math.max(3, width - 16), 3, 3, 3);
        } finally {
            g.dispose();
        }
    }

    @Override
    protected void paintTabBorder(
            Graphics graphics, int placement, int index,
            int x, int y, int width, int height, boolean selected) {
    }

    @Override
    protected void paintContentBorder(
            Graphics graphics, int placement, int selectedIndex) {
    }

    @Override
    protected void paintFocusIndicator(
            Graphics graphics, int placement, Rectangle[] rectangles,
            int index, Rectangle icon, Rectangle text, boolean selected) {
        if (!tabPane.hasFocus() || index != getFocusIndex()) return;
        graphics.setColor(AppColors.color(
                "App.focus", new Color(0x67A8E4)));
        Rectangle bounds = rectangles[index];
        graphics.drawRoundRect(bounds.x + 4, bounds.y + 3,
                bounds.width - 8, bounds.height - 7, 8, 8);
    }
}
