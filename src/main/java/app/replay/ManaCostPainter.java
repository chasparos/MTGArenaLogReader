package app.replay;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Paints compact mana-cost symbols as one right-aligned visual unit.
 *
 * <p>This remains a presentation helper: mana parsing here is limited to
 * display tokens and does not interpret game rules.</p>
 */
final class ManaCostPainter {
    private static final Pattern TOKEN = Pattern.compile("\\{([^}]+)}");
    private final SvgAssetRenderer svgAssets;
    private final int symbolSize;

    ManaCostPainter(SvgAssetRenderer svgAssets, int symbolSize) {
        this.svgAssets = svgAssets;
        this.symbolSize = symbolSize;
    }

    int width(String manaCost) {
        int count = tokens(manaCost).size();
        return count == 0 ? 0 : count * symbolSize + Math.max(0, count - 1);
    }

    void paint(Graphics2D graphics, String manaCost, int x, int y, Color cardColor) {
        int currentX = x;
        for (String token : tokens(manaCost)) {
            paintSymbol(graphics, token, currentX, y, cardColor);
            currentX += symbolSize + 1;
        }
    }

    private void paintSymbol(Graphics2D graphics, String token, int x, int y, Color cardColor) {
        Color surround = blend(cardColor, Color.WHITE, .55f);
        graphics.setColor(surround);
        graphics.fillOval(x, y, symbolSize, symbolSize);
        graphics.setColor(blend(surround, Color.BLACK, .30f));
        graphics.drawOval(x, y, symbolSize, symbolSize);

        String resource = normalize(token).replace("/", "_");
        if (svgAssets.paint(graphics, "/mana-svg/" + resource + ".svg",
                x, y, symbolSize, symbolSize)) {
            return;
        }

        Font old = graphics.getFont();
        graphics.setFont(old.deriveFont(Font.BOLD, token.length() > 2 ? 7f : 8f));
        FontMetrics metrics = graphics.getFontMetrics();
        String text = token.length() > 3 ? token.substring(0, 3) : token;
        graphics.setColor(Color.DARK_GRAY);
        graphics.drawString(text, x + (symbolSize - metrics.stringWidth(text)) / 2,
                y + (symbolSize - metrics.getHeight()) / 2 + metrics.getAscent());
        graphics.setFont(old);
    }

    private List<String> tokens(String manaCost) {
        if (manaCost == null || manaCost.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(manaCost);
        while (matcher.find()) result.add(matcher.group(1));
        return result;
    }

    private String normalize(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private Color blend(Color left, Color right, float amount) {
        float n = Math.max(0, Math.min(1, amount));
        return new Color(
                Math.round(left.getRed() * (1 - n) + right.getRed() * n),
                Math.round(left.getGreen() * (1 - n) + right.getGreen() * n),
                Math.round(left.getBlue() * (1 - n) + right.getBlue() * n));
    }
}
