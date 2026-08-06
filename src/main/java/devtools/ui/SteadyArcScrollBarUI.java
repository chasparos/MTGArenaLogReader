package devtools.ui;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

/** Slim rounded scrollbar shared by Steady Arc output and assistant surfaces. */
public final class SteadyArcScrollBarUI extends BasicScrollBarUI {
    @Override
    protected void configureScrollBarColors() {
        trackColor = SteadyArcTheme.SURFACE_RECESSED;
        thumbColor = SteadyArcTheme.GOLD;
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return zeroButton();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return zeroButton();
    }

    @Override
    protected void paintTrack(Graphics graphics, JComponent component, Rectangle bounds) {
        paintRounded(graphics, bounds, trackColor);
    }

    @Override
    protected void paintThumb(Graphics graphics, JComponent component, Rectangle bounds) {
        if (bounds.isEmpty() || !scrollbar.isEnabled()) {
            return;
        }
        paintRounded(graphics, bounds, isDragging ? SteadyArcTheme.GOLD_HOVER : thumbColor);
    }

    private static void paintRounded(Graphics graphics, Rectangle bounds, Color color) {
        Graphics2D copy = (Graphics2D) graphics.create();
        try {
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            copy.setColor(color);
            copy.fillRoundRect(bounds.x + 2, bounds.y + 2,
                    Math.max(0, bounds.width - 4), Math.max(0, bounds.height - 4),
                    8, 8);
        } finally {
            copy.dispose();
        }
    }

    private static JButton zeroButton() {
        JButton button = new JButton();
        button.setPreferredSize(new Dimension(0, 0));
        return button;
    }
}
