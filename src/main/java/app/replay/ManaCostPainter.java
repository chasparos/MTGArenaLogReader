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
        int diameter = symbolSize + surroundPadding * 2;
        List<Color> colors = symbolColors(token);
        if (colors.size() <= 1) {
            graphics.setColor(colors.isEmpty() ? new Color(0xB9B9B9) : colors.get(0));
            graphics.fillOval(x - surroundPadding, y - surroundPadding, diameter, diameter);
        } else {
            int arc = Math.max(1, 360 / colors.size());
            for (int i = 0; i < colors.size(); i++) {
                graphics.setColor(colors.get(i));
                graphics.fillArc(x - surroundPadding, y - surroundPadding,
                        diameter, diameter, 90 - i * arc, -arc);
            }
        }
        graphics.setColor(new Color(0x3A3A3A));
        graphics.drawOval(x - surroundPadding, y - surroundPadding, diameter, diameter);

        String resource = normalize(token).replace("/", "_");
        if (svgAssets.paint(graphics, "/mana-svg/" + resource + ".svg",
                x+1, y+1, symbolSize-1, symbolSize-1)) {
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



    private List<Color> symbolColors(String token) {
        String normalized = normalize(token);
        List<Color> result = new ArrayList<>();
        for (String part : normalized.split("/")) {
            Color color = switch (part) {
                case "W" -> new Color(0xF4E7B2);
                case "U" -> new Color(0x2F9BD8);
                case "B" -> (new Color(0x6A5A70)).brighter().brighter();
                case "R" -> new Color(0xE34B36);
                case "G" -> new Color(0x3FA45B);
                case "C" -> new Color(0xA9B7B8);
                default -> null;
            };
            if (color != null && !result.contains(color)) result.add(color);
        }
        return result;
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
