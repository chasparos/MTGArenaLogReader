package app.draft.ui;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

final class ManaCurvePanel extends JComponent {
    private Map<Integer, Integer> curve = Map.of();

    ManaCurvePanel() {
        setPreferredSize(new Dimension(360, 135));
        setMinimumSize(new Dimension(240, 110));
        setBorder(BorderFactory.createTitledBorder("Mana curve"));
    }

    void setCurve(Map<Integer, Integer> curve) {
        this.curve = curve == null ? Map.of() : Map.copyOf(curve);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            Insets insets = getInsets();
            int left = insets.left + 12;
            int top = insets.top + 8;
            int width = getWidth() - left - insets.right - 8;
            int height = getHeight() - top - insets.bottom - 20;
            int max = Math.max(1, curve.values().stream()
                    .mapToInt(Integer::intValue).max().orElse(1));
            int gap = 5;
            int barWidth = Math.max(8, (width - gap * 7) / 8);
            for (int value = 0; value <= 7; value++) {
                int count = curve.getOrDefault(value, 0);
                int barHeight = count * Math.max(1, height - 18) / max;
                int x = left + value * (barWidth + gap);
                int y = top + height - 15 - barHeight;
                g.setColor(new Color(83, 130, 177));
                g.fillRoundRect(x, y, barWidth, barHeight, 7, 7);
                g.setColor(getForeground());
                String countLabel = Integer.toString(count);
                g.drawString(countLabel,
                        x + (barWidth - g.getFontMetrics()
                                .stringWidth(countLabel)) / 2,
                        Math.max(top + 11, y - 3));
                String label = value == 7 ? "7+" : Integer.toString(value);
                g.drawString(label,
                        x + (barWidth - g.getFontMetrics()
                                .stringWidth(label)) / 2,
                        top + height);
            }
        } finally {
            g.dispose();
        }
    }
}
