package app.deckplanner.ui;

import app.ui.AppColors;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** Accessible click-first toggle control whose active state is visible without relying on color. */
final class FilterChip extends JToggleButton {
    private final String baseLabel;

    FilterChip(String label) {
        super(label);
        baseLabel = label;
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setRolloverEnabled(true);
        setMargin(new Insets(6, 10, 6, 10));
        getAccessibleContext().setAccessibleName(label);
        getModel().addChangeListener(event -> updateLabel());
    }

    private void updateLabel() {
        setText(isSelected() ? "✓ " + baseLabel : baseLabel);
        getAccessibleContext().setAccessibleDescription(isSelected() ? "Selected" : "Not selected");
    }

    @Override public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(Math.max(42, d.width + 14), Math.max(32, d.height + 4));
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color surface = AppColors.color("App.control", new Color(0x343941));
            if (isSelected()) surface = AppColors.color("App.controlSelected", new Color(0x476A8E));
            else if (getModel().isRollover()) surface = AppColors.color("App.controlHover", new Color(0x414852));
            Shape shape = new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 14, 14);
            g.setColor(surface);
            g.fill(shape);
            g.setColor(AppColors.color("App.border", new Color(0x626873)));
            g.draw(shape);
            if (isFocusOwner()) {
                g.setColor(AppColors.color("App.focus", new Color(0x67A8E4)));
                g.setStroke(new BasicStroke(2f));
                g.draw(new RoundRectangle2D.Float(3, 3, getWidth() - 6, getHeight() - 6, 11, 11));
            }
        } finally {
            g.dispose();
        }
        super.paintComponent(graphics);
    }
}
