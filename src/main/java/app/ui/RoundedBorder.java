package app.ui;

import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** Thin semantic border used for fields, scroll surfaces and titled sections. */
public final class RoundedBorder extends AbstractBorder {
    private final int radius;
    private final Insets insets;

    public RoundedBorder(int radius, Insets insets) {
        this.radius = radius;
        this.insets = (Insets) insets.clone();
    }

    @Override
    public Insets getBorderInsets(Component component, Insets target) {
        target.set(insets.top, insets.left, insets.bottom, insets.right);
        return target;
    }

    @Override
    public void paintBorder(
            Component component, Graphics graphics,
            int x, int y, int width, int height) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(AppColors.color(
                    "App.border", new Color(0x7B818A)));
            g.draw(new RoundRectangle2D.Float(
                    x + .5f, y + .5f, width - 1f, height - 1f,
                    radius, radius));
        } finally {
            g.dispose();
        }
    }
}
