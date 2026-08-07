package app.deckplanner.ui;

import app.ui.AppColors;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** Compact content-sized filter control with geometry reserved for icon, selection mark, and optional count. */
final class FilterChip extends JToggleButton {
    private static final int CHECK_SPACE = 18;
    private static final int COUNT_SPACE = 28;
    private final String baseLabel;
    private final boolean countCapable;
    private Dimension stableSize;
    private Long count;

    FilterChip(String label) { this(label, null, false); }
    FilterChip(String label, Icon icon) { this(label, icon, false); }
    FilterChip(String label, Icon icon, boolean countCapable) {
        super(label, icon);
        this.baseLabel = label;
        this.countCapable = countCapable;
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setRolloverEnabled(true);
        setHorizontalAlignment(SwingConstants.LEFT);
        setIconTextGap(5);
        setMargin(new Insets(3, CHECK_SPACE + 4, 3, countCapable ? COUNT_SPACE + 5 : 8));
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
        if (stableSize == null) {
            Dimension natural = super.getPreferredSize();
            stableSize = new Dimension(natural.width + 4, 28);
        }
        return new Dimension(stableSize);
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
        } finally {
            g.dispose();
        }
        super.paintComponent(graphics);
        if (isSelected()) paintCheck(graphics);
        if (countCapable && count != null) paintCount(graphics);
    }

    private void paintCheck(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(AppColors.color("Label.foreground", Color.WHITE));
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int cx = checkCenterX();
            int cy = getHeight() / 2;
            g.drawLine(cx - 4, cy, cx - 1, cy + 3);
            g.drawLine(cx - 1, cy + 3, cx + 4, cy - 3);
        } finally {
            g.dispose();
        }
    }

    /** Centers the mark in the reserved gap before the icon, then nudges it away from the label. */
    private int checkCenterX() {
        Rectangle view = new Rectangle(0, 0, getWidth(), getHeight());
        Rectangle iconRect = new Rectangle();
        Rectangle textRect = new Rectangle();
        SwingUtilities.layoutCompoundLabel(this, getFontMetrics(getFont()), getText(), getIcon(),
                getVerticalAlignment(), getHorizontalAlignment(),
                getVerticalTextPosition(), getHorizontalTextPosition(),
                view, iconRect, textRect, getIconTextGap());
        int iconStart = iconRect.width > 0 ? iconRect.x : Math.max(CHECK_SPACE + 4, getInsets().left);
        return Math.max(13, ((6 + iconStart) / 2) - 1);
    }

    private void paintCount(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            String value = count > 999 ? "999+" : Long.toString(count);
            Font font = getFont().deriveFont(Font.PLAIN, 9f);
            g.setFont(font);
            FontMetrics metrics = g.getFontMetrics();
            int width = Math.max(15, metrics.stringWidth(value) + 6);
            int height = 13;
            int x = getWidth() - width - 5;
            int baseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            int y = baseline - metrics.getAscent() - 1;
            Color foreground = AppColors.color("Label.foreground", Color.WHITE);
            g.setColor(new Color(foreground.getRed(), foreground.getGreen(), foreground.getBlue(), 28));
            g.fillRoundRect(x, y, width, height, 8, 8);
            g.setColor(new Color(foreground.getRed(), foreground.getGreen(), foreground.getBlue(), 175));
            g.drawString(value, x + (width - metrics.stringWidth(value)) / 2, baseline);
        } finally {
            g.dispose();
        }
    }
}
