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
public final class ManaCostPainter {
    private static final Pattern TOKEN = Pattern.compile("\\{([^}]+)}");
    private final SvgAssetRenderer svgAssets;
    private final int symbolSize;

    public ManaCostPainter(SvgAssetRenderer svgAssets, int symbolSize) {
        this.svgAssets = svgAssets;
        this.symbolSize = symbolSize;
    }

    public int width(String manaCost) {
        List<String> parts = costParts(manaCost);
        if (parts.isEmpty()) return 0;
        int symbols = parts.stream().mapToInt(part -> tokens(part).size()).sum();
        int separators = Math.max(0, parts.size() - 1);
        return symbols * symbolSize + Math.max(0, symbols - parts.size())
                + separators * (symbolSize - 1);
    }

    public void paint(Graphics2D graphics, String manaCost, int x, int y, Color cardColor) {
        int currentX = x;
        List<String> parts = costParts(manaCost);
        for (int partIndex = 0; partIndex < parts.size(); partIndex++) {
            if (partIndex > 0) {
                Font old = graphics.getFont();
                graphics.setFont(old.deriveFont(Font.BOLD, 10f));
                graphics.setColor(blend(cardColor, Color.BLACK, .55f));
                graphics.drawString("/", currentX + 2, y + symbolSize - 1);
                graphics.setFont(old);
                currentX += symbolSize - 1;
            }
            List<String> partTokens = tokens(parts.get(partIndex));
            for (int tokenIndex = 0; tokenIndex < partTokens.size(); tokenIndex++) {
                paintSymbol(graphics, partTokens.get(tokenIndex), currentX, y, cardColor);
                currentX += symbolSize;
                if (tokenIndex + 1 < partTokens.size()) currentX++;
            }
        }
    }

    private void paintSymbol(Graphics2D graphics, String token, int x, int y, Color cardColor) {
        int surroundPadding = 1;
        Color surround = blend(cardColor, Color.WHITE, .34f);
        graphics.setColor(surround);
        graphics.fillOval(x - surroundPadding, y - surroundPadding,
                symbolSize + surroundPadding * 2, symbolSize + surroundPadding * 2);
        graphics.setColor(blend(surround, Color.BLACK, .38f));
        graphics.drawOval(x - surroundPadding, y - surroundPadding,
                symbolSize + surroundPadding * 2, symbolSize + surroundPadding * 2);

        String resource = normalize(token).replace("/", "_");
        if (svgAssets.paint(graphics, "/mana-svg/" + resource + ".svg",
                x, y + 1, symbolSize, symbolSize)) {
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

    private List<String> costParts(String manaCost) {
        if (manaCost == null || manaCost.isBlank()) return List.of();
        String[] raw = manaCost.split("\\s*//\\s*");
        List<String> result = new ArrayList<>();
        for (String part : raw) {
            if (!tokens(part).isEmpty()) result.add(part);
        }
        return result;
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
