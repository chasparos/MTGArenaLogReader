package app.ui;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** Flat rounded button painter with explicit hover, focus and disabled states. */
public final class AppButtonUI extends BasicButtonUI {
    public static ComponentUI createUI(JComponent component) {
        return new AppButtonUI();
    }

    @Override
    protected void installDefaults(AbstractButton button) {
        super.installDefaults(button);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setRolloverEnabled(true);
        button.setMargin(new Insets(6, 12, 6, 12));
    }

    @Override
    public Dimension getPreferredSize(JComponent component) {
        Dimension result = super.getPreferredSize(component);
        result.height = Math.max(30, result.height + 4);
        result.width += 10;
        return result;
    }

    @Override
    public void paint(Graphics graphics, JComponent component) {
        AbstractButton button = (AbstractButton) component;
        ButtonModel model = button.getModel();
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            Color surface = AppColors.color(
                    "App.control", new Color(0x343941));
            if (!model.isEnabled()) {
                surface = AppColors.color(
                        "App.controlDisabled", new Color(0x2B2E33));
            } else if (model.isPressed()) {
                surface = AppColors.color(
                        "App.controlPressed", new Color(0x476A8E));
            } else if (model.isRollover()) {
                surface = AppColors.color(
                        "App.controlHover", new Color(0x414852));
            }
            Shape shape = new RoundRectangle2D.Float(
                    1, 1, component.getWidth() - 2,
                    component.getHeight() - 2, 11, 11);
            g.setColor(surface);
            g.fill(shape);
            g.setColor(AppColors.color(
                    "App.border", new Color(0x626873)));
            g.draw(shape);

            if (button.isFocusOwner()) {
                g.setColor(AppColors.color(
                        "App.focus", new Color(0x67A8E4)));
                g.setStroke(new BasicStroke(1.5f));
                g.draw(new RoundRectangle2D.Float(
                        3, 3, component.getWidth() - 6,
                        component.getHeight() - 6, 8, 8));
            }
        } finally {
            g.dispose();
        }
        super.paint(graphics, component);
    }
}
