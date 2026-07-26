package app.draft.ui;

import app.ui.AppColors;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/** Compact, theme-aware curve intended for an analyzed collection header. */
final class ManaCurvePanel extends JComponent {
    private Map<Integer, Integer> curve = Map.of();

    ManaCurvePanel() {
        setPreferredSize(new Dimension(520, 92));
        setMinimumSize(new Dimension(300, 78));
        setOpaque(false);
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
            Color text = AppColors.color(
                    "Label.foreground", new Color(0xE5E9EF));
            Color muted = AppColors.color(
                    "App.textMuted", new Color(0xAEB5BF));
            Color bars = AppColors.color(
                    "App.chartBar", new Color(0xD7AD45));
            Color highlight = AppColors.color(
                    "App.chartBarHighlight", new Color(0xEFCA68));

            Font base = getFont() == null
                    ? UIManager.getFont("Label.font") : getFont();
            g.setFont(base.deriveFont(Font.PLAIN, 10f));

            int left = 8;
            int top = 4;
            int width = Math.max(80, getWidth() - 16);
            int height = Math.max(42, getHeight() - top - 5);
            int max = Math.max(1, curve.values().stream()
                    .mapToInt(Integer::intValue).max().orElse(1));
            int gap = 4;
            int barWidth = Math.max(8, (width - gap * 7) / 8);
            for (int value = 0; value <= 7; value++) {
                int count = curve.getOrDefault(value, 0);
                int barHeight = count * Math.max(1, height - 22) / max;
                int x = left + value * (barWidth + gap);
                int baseline = top + height - 12;
                int y = baseline - barHeight;
                g.setColor(value >= 2 && value <= 4 ? highlight : bars);
                if (barHeight > 0) {
                    g.fillRoundRect(x, y, barWidth, barHeight, 6, 6);
                }

                String countLabel = Integer.toString(count);
                if (count > 0) {
                    g.setColor(text);
                    g.drawString(countLabel,
                            x + (barWidth - g.getFontMetrics()
                                    .stringWidth(countLabel)) / 2,
                            Math.max(top + 10, y - 2));
                }

                String label = value == 7 ? "7+" : Integer.toString(value);
                g.setColor(muted);
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
