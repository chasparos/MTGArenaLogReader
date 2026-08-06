package app.deckplanner.ui;

import app.ui.AppColors;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** Compact fixed-width filter control whose selected state never changes its geometry. */
final class FilterChip extends JToggleButton {
    private final String baseLabel;
    private final int fixedWidth;
    private Long count;

    FilterChip(String label) {
        this(label, null, 92);
    }

    FilterChip(String label, Icon icon, int fixedWidth) {
        super(label, icon);
        this.baseLabel = label;
        this.fixedWidth = fixedWidth;
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setRolloverEnabled(true);
        setHorizontalAlignment(SwingConstants.CENTER);
        setIconTextGap(5);
        setMargin(new Insets(3, 7, 3, 7));
        setFont(getFont().deriveFont(Font.BOLD, 11f));
        getAccessibleContext().setAccessibleName(label);
        getModel().addChangeListener(event -> updateAccessibleState());
    }

    void setCount(long count) {
        this.count = Math.max(0L, count);
        setToolTipText(this.count + (this.count == 1L ? " matching card" : " matching cards"));
        repaint();
    }

    String baseLabel() { return baseLabel; }

    private void updateAccessibleState() {
        getAccessibleContext().setAccessibleDescription(isSelected() ? "Selected" : "Not selected");
        repaint();
    }

    @Override public Dimension getPreferredSize() {
        return new Dimension(fixedWidth, 28);
    }

    @Override public Dimension getMinimumSize() { return getPreferredSize(); }
    @Override public Dimension getMaximumSize() { return getPreferredSize(); }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color surface = AppColors.color("App.control", new Color(0x343941));
            if (isSelected()) surface = AppColors.color("App.controlSelected", new Color(0x765529));
            else if (getModel().isRollover()) surface = AppColors.color("App.controlHover", new Color(0x414852));
            Shape shape = new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 12, 12);
            g.setColor(surface);
            g.fill(shape);
            g.setColor(isSelected()
                    ? AppColors.color("App.accent", new Color(0xC69B52))
                    : AppColors.color("App.border", new Color(0x626873)));
            g.draw(shape);
            if (isFocusOwner()) {
                g.setColor(AppColors.color("App.focus", new Color(0x67A8E4)));
                g.setStroke(new BasicStroke(2f));
                g.draw(new RoundRectangle2D.Float(3, 3, getWidth() - 6, getHeight() - 6, 9, 9));
            }
            if (isSelected()) {
                g.setColor(AppColors.color("Label.foreground", Color.WHITE));
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(6, getHeight() / 2, 9, getHeight() / 2 + 3);
                g.drawLine(9, getHeight() / 2 + 3, 14, getHeight() / 2 - 3);
            }
        } finally {
            g.dispose();
        }
        super.paintComponent(graphics);
        if (count != null) paintCount(graphics);
    }

    private void paintCount(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            String value = count > 999 ? "999+" : Long.toString(count);
            Font font = getFont().deriveFont(Font.PLAIN, 9f);
            g.setFont(font);
            FontMetrics metrics = g.getFontMetrics();
            int width = Math.max(16, metrics.stringWidth(value) + 7);
            int height = 14;
            int x = getWidth() - width - 4;
            int y = 3;
            g.setColor(new Color(0, 0, 0, 145));
            g.fillRoundRect(x, y, width, height, height, height);
            g.setColor(Color.WHITE);
            g.drawString(value, x + (width - metrics.stringWidth(value)) / 2, y + 11);
        } finally {
            g.dispose();
        }
    }
}
