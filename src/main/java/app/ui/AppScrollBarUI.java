package app.ui;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** Narrow overlay-like scrollbar without arrow buttons or beveled tracks. */
public final class AppScrollBarUI extends BasicScrollBarUI {
    public static ComponentUI createUI(JComponent component) {
        return new AppScrollBarUI();
    }

    @Override
    protected void configureScrollBarColors() {
        trackColor = AppColors.color(
                "App.scrollTrack", new Color(0x202328));
        thumbColor = AppColors.color(
                "App.scrollThumb", new Color(0x626A75));
        thumbHighlightColor = thumbColor;
        thumbDarkShadowColor = thumbColor;
        thumbLightShadowColor = thumbColor;
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return zeroButton();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return zeroButton();
    }

    private JButton zeroButton() {
        JButton button = new JButton();
        Dimension zero = new Dimension(0, 0);
        button.setPreferredSize(zero);
        button.setMinimumSize(zero);
        button.setMaximumSize(zero);
        return button;
    }

    @Override
    protected void paintTrack(
            Graphics graphics, JComponent component, Rectangle bounds) {
        graphics.setColor(trackColor);
        graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    @Override
    protected void paintThumb(
            Graphics graphics, JComponent component, Rectangle bounds) {
        if (!component.isEnabled() || bounds.isEmpty()) return;
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            Color thumb = isThumbRollover()
                    ? AppColors.color(
                            "App.scrollThumbHover", new Color(0x7A8491))
                    : thumbColor;
            g.setColor(thumb);
            int inset = 2;
            g.fill(new RoundRectangle2D.Float(
                    bounds.x + inset, bounds.y + inset,
                    Math.max(4, bounds.width - inset * 2),
                    Math.max(4, bounds.height - inset * 2),
                    9, 9));
        } finally {
            g.dispose();
        }
    }

    @Override
    public Dimension getPreferredSize(JComponent component) {
        return scrollbar != null
                && scrollbar.getOrientation() == Adjustable.HORIZONTAL
                ? new Dimension(48, 10)
                : new Dimension(10, 48);
    }
}
