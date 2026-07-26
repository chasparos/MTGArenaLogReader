package app.draft.ui;

import app.draft.model.DraftTier;
import app.model.card.CardInfo;
import app.replay.ActivatedAbilityParser;
import app.replay.ManaCostPainter;
import app.replay.SvgAssetRenderer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Draft-sized version of the GameView's inline card chip. The card header is
 * one painted shape; metadata is carried by small chips attached to its edges.
 */
final class DraftCardChip extends JComponent {
    private static final int WIDTH = 310;
    private static final int HEIGHT = 70;
    private static final int HEADER_Y = 8;
    private static final int HEADER_HEIGHT = 45;
    private static final int TYPE_SIZE = 17;
    private static final int KEYWORD_SIZE = 11;
    private static final SvgAssetRenderer SVG = new SvgAssetRenderer();
    private static final ManaCostPainter MANA = new ManaCostPainter(SVG, 14);
    private static final ManaCostPainter MINI_MANA =
            new ManaCostPainter(SVG, 9);
    private static final ActivatedAbilityParser ABILITIES =
            new ActivatedAbilityParser();

    private final CardInfo card;
    private final long arenaId;
    private final int quantity;
    private final DraftTier tier;
    private final boolean selected;
    private boolean hovered;

    DraftCardChip(CardInfo card, long arenaId, int quantity, DraftTier tier,
                  boolean selected, DraftCardPreview preview) {
        this.card = card;
        this.arenaId = arenaId;
        this.quantity = quantity;
        this.tier = tier;
        this.selected = selected;
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setMinimumSize(new Dimension(235, HEIGHT));
        setToolTipText(toolTip());

        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent event) {
                hovered = true;
                repaint();
                preview.show(card, DraftCardChip.this);
            }

            @Override public void mouseExited(MouseEvent event) {
                hovered = false;
                repaint();
                preview.hide();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            int width = getWidth();
            Color base = cardColor(card);
            if (hovered) base = blend(base, Color.WHITE, .16f);
            Shape header = legendaryHeader(width);

            g.setColor(base);
            g.fill(header);
            g.setStroke(new BasicStroke(selected ? 2.4f : 1.1f));
            g.setColor(selected ? new Color(45, 105, 190)
                    : blend(base, Color.BLACK, .32f));
            g.draw(header);
            g.setStroke(new BasicStroke(1f));

            paintHeading(g, width, base);
            paintRarity(g, 5, 2);
            paintTier(g, width);
            paintQuantity(g);
            paintAbilityChip(g, base);
            paintKeywordChip(g, width, base);
            paintPowerToughness(g, width, base);
        } finally {
            g.dispose();
        }
    }

    private Shape legendaryHeader(int width) {
        int left = 3;
        int right = width - 3;
        int y = HEADER_Y;
        int bottom = y + HEADER_HEIGHT;
        String typeLine = card == null ? ""
                : nullToEmpty(card.effectiveTypeLine());
        if (!typeLine.toLowerCase(Locale.ROOT).contains("legendary")) {
            return new RoundRectangle2D.Float(
                    left, y, right - left, HEADER_HEIGHT, 15, 15);
        }
        Path2D path = new Path2D.Float();
        path.moveTo(left + 10, y);
        path.lineTo(width * .17, y);
        path.curveTo(width * .22, y, width * .18, 2, width * .27, 2);
        path.curveTo(width * .23, 0, width * .31, 0, width * .36, 2);
        path.lineTo(width * .64, 2);
        path.curveTo(width * .69, 0, width * .77, 0, width * .73, 2);
        path.curveTo(width * .82, 2, width * .78, y, width * .83, y);
        path.lineTo(right - 10, y);
        path.quadTo(right, y, right, y + 10);
        path.lineTo(right, bottom - 10);
        path.quadTo(right, bottom, right - 10, bottom);
        path.lineTo(left + 10, bottom);
        path.quadTo(left, bottom, left, bottom - 10);
        path.lineTo(left, y + 10);
        path.quadTo(left, y, left + 10, y);
        path.closePath();
        return path;
    }

    private void paintHeading(Graphics2D g, int width, Color base) {
        String name = card == null || card.getName() == null
                ? "Arena card " + arenaId : card.getName();
        int x = 14;
        String type = typeResource(card);
        if (type != null && SVG.paint(g, type, x, 18, TYPE_SIZE, TYPE_SIZE)) {
            x += TYPE_SIZE + 7;
        }

        String cost = card == null ? "" : nullToEmpty(card.getManaCost());
        int manaWidth = MANA.width(cost);
        int manaX = getWidth() - 13 - manaWidth;
        if (manaWidth > 0) MANA.paint(g, cost, manaX, 19, base);

        Font old = g.getFont();
        int availableNameWidth = Math.max(30, manaX - x - 9);
        Font nameFont = fittingNameFont(
                g, old, name, availableNameWidth);
        g.setFont(nameFont);
        g.setColor(contrast(base));
        String label = fit(name, g.getFontMetrics(),
                availableNameWidth);
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(label, x, 19
                + (14 - metrics.getHeight()) / 2 + metrics.getAscent());

        String typeLine = card == null ? "" :
                nullToEmpty(card.effectiveTypeLine());
        g.setFont(old.deriveFont(Font.PLAIN, 10f));
        g.setColor(blend(contrast(base), base, .20f));
        g.drawString(fit(typeLine, g.getFontMetrics(), getWidth() - x - 16),
                x, 43);
        g.setFont(old);
    }

    private void paintRarity(Graphics2D g, int x, int y) {
        if (card == null || card.getRarity() == null) return;
        Color rarity = rarityColor(card.getRarity());
        int size = 17;
        g.setColor(new Color(250, 250, 245, 225));
        g.fillOval(x - 2, y - 2, size + 4, size + 4);
        g.setColor(blend(rarity, Color.BLACK, .30f));
        g.drawOval(x - 2, y - 2, size + 4, size + 4);
        SVG.paintTinted(g, "/svg/rarity.svg", x, y, size, size, rarity);
    }

    private void paintTier(Graphics2D g, int width) {
        if (tier == null) return;
        int stars = switch (tier) {
            case S, A -> 5;
            case B -> 4;
            case C -> 3;
            case D -> 2;
            case F -> 1;
        };
        Color color = switch (tier) {
            case S -> new Color(154, 82, 232);
            case A -> new Color(224, 169, 37);
            case B -> new Color(72, 143, 207);
            case C -> new Color(125, 132, 139);
            case D -> new Color(177, 112, 56);
            case F -> new Color(190, 65, 65);
        };
        int starGap = 11;
        int starRadius = 5;
        int groupWidth = (stars - 1) * starGap + starRadius * 2;
        int chipWidth = groupWidth + 8;
        int x = width - chipWidth - 5;
        int y = 0;
        paintMiniChip(g, x, y, chipWidth, 16,
                tier == DraftTier.S
                        ? new Color(236, 224, 255) : new Color(252, 247, 224));
        g.setColor(color);
        double firstCenter = x + (chipWidth - groupWidth) / 2.0 + starRadius;
        for (int i = 0; i < stars; i++) {
            g.fill(star(firstCenter + i * starGap,
                    y + 8, starRadius, 2.2));
        }
    }

    private void paintQuantity(Graphics2D g) {
        if (quantity <= 1) return;
        String text = quantity + "×";
        Font old = g.getFont();
        g.setFont(old.deriveFont(Font.BOLD, 11f));
        FontMetrics metrics = g.getFontMetrics();
        int width = metrics.stringWidth(text) + 8;
        paintMiniChip(g, 5, 48, width, 18, new Color(245, 245, 238));
        g.setColor(Color.DARK_GRAY);
        g.drawString(text, 9, 48 + (18 - metrics.getHeight()) / 2
                + metrics.getAscent());
        g.setFont(old);
    }

    private void paintKeywordChip(Graphics2D g, int width, Color base) {
        List<String> keywords = supportedKeywords();
        if (keywords.isEmpty()) return;
        int visible = Math.min(5, keywords.size());
        int chipWidth = 7 + visible * KEYWORD_SIZE
                + Math.max(0, visible - 1) * 2;
        int rightEdge = width - 6;
        if (card != null && card.getPower() != null
                && card.getToughness() != null) {
            Font font = g.getFont().deriveFont(Font.BOLD, 15f);
            String pt = card.getPower() + "/" + card.getToughness();
            rightEdge -= g.getFontMetrics(font).stringWidth(pt) + 19;
        }
        int x = rightEdge - chipWidth;
        int y = 50;
        paintMiniChip(g, x, y, chipWidth, 17,
                blend(Color.WHITE, base, .12f));
        int iconX = x + 4;
        for (int i = 0; i < visible; i++) {
            SVG.paint(g, keywordResource(keywords.get(i)),
                    iconX, y + 3, KEYWORD_SIZE, KEYWORD_SIZE);
            iconX += KEYWORD_SIZE + 2;
        }
    }

    private void paintAbilityChip(Graphics2D g, Color base) {
        ActivatedAbilityParser.Badge ability = ABILITIES.parse(card);
        if (ability == null) return;
        int content = ability.tap() ? 11 : 0;
        if (!ability.manaCost().isBlank()) {
            content += (content == 0 ? 0 : 3)
                    + MINI_MANA.width(ability.manaCost());
        }
        Font old = g.getFont();
        Font compact = old.deriveFont(Font.PLAIN, 8f);
        FontMetrics metrics = g.getFontMetrics(compact);
        if (!ability.textCost().isBlank()) {
            content += (content == 0 ? 0 : 3)
                    + metrics.stringWidth(ability.textCost());
        }
        for (String option : ability.manaOptions()) {
            content += 2 + MINI_MANA.width("{" + option + "}");
        }
        if (content == 0) return;
        int x = quantity > 1 ? 38 : 8;
        int y = 49;
        paintMiniChip(g, x, y, content + 8, 17,
                blend(Color.WHITE, base, .12f));
        int cursor = x + 4;
        if (ability.tap()) {
            SVG.paint(g, "/svg/tap.svg", cursor, y + 4, 9, 9);
            cursor += 12;
        }
        if (!ability.manaCost().isBlank()) {
            MINI_MANA.paint(g, ability.manaCost(), cursor, y + 4, base);
            cursor += MINI_MANA.width(ability.manaCost()) + 3;
        }
        if (!ability.textCost().isBlank()) {
            g.setFont(compact);
            g.setColor(contrast(base));
            g.drawString(ability.textCost(), cursor,
                    y + (17 - metrics.getHeight()) / 2 + metrics.getAscent());
            cursor += metrics.stringWidth(ability.textCost()) + 2;
        }
        for (String option : ability.manaOptions()) {
            MINI_MANA.paint(g, "{" + option + "}", cursor, y + 4, base);
            cursor += MINI_MANA.width("{" + option + "}") + 2;
        }
        g.setFont(old);
    }

    private void paintPowerToughness(Graphics2D g, int width, Color base) {
        if (card == null || card.getPower() == null
                || card.getToughness() == null) return;
        String text = card.getPower() + "/" + card.getToughness();
        Font old = g.getFont();
        Font font = old.deriveFont(Font.BOLD, 15f);
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        int chipWidth = metrics.stringWidth(text) + 12;
        int x = width - chipWidth - 7;
        int y = 43;
        paintMiniChip(g, x, y, chipWidth, 24,
                blend(Color.WHITE, base, .08f));
        g.setColor(contrast(base));
        g.drawString(text, x + 6,
                y + (24 - metrics.getHeight()) / 2 + metrics.getAscent());
        g.setFont(old);
    }

    private void paintMiniChip(
            Graphics2D g, int x, int y, int width, int height, Color base) {
        Shape chip = new RoundRectangle2D.Float(
                x, y, width, height, height - 2, height - 2);
        g.setColor(base);
        g.fill(chip);
        g.setColor(blend(base, Color.BLACK, .28f));
        g.draw(chip);
    }

    private Shape star(double cx, double cy, double outer, double inner) {
        Path2D path = new Path2D.Double();
        for (int point = 0; point < 10; point++) {
            double radius = point % 2 == 0 ? outer : inner;
            double angle = -Math.PI / 2 + point * Math.PI / 5;
            double x = cx + Math.cos(angle) * radius;
            double y = cy + Math.sin(angle) * radius;
            if (point == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.closePath();
        return path;
    }

    private List<String> supportedKeywords() {
        if (card == null || card.getKeywords() == null) return List.of();
        Set<String> supported = Set.of("deathtouch", "defender",
                "double strike", "first strike", "flying", "haste",
                "hexproof", "indestructible", "lifelink", "menace", "reach",
                "trample", "vigilance", "ward");
        return card.getKeywords().stream()
                .filter(java.util.Objects::nonNull)
                .filter(keyword -> supported.contains(
                        keyword.toLowerCase(Locale.ROOT)))
                .toList();
    }

    private String keywordResource(String keyword) {
        return "/keyword-svg/ability-"
                + keyword.toLowerCase(Locale.ROOT).replace(" ", "") + ".svg";
    }

    private String typeResource(CardInfo value) {
        String type = value == null ? ""
                : nullToEmpty(value.effectiveTypeLine()).toLowerCase(Locale.ROOT);
        for (String candidate : List.of("land", "creature", "planeswalker",
                "artifact", "enchantment", "instant", "sorcery")) {
            if (type.contains(candidate)) return "/svg/" + candidate + ".svg";
        }
        return null;
    }

    private Color rarityColor(String rarity) {
        return switch (rarity.toLowerCase(Locale.ROOT)) {
            case "mythic" -> new Color(217, 91, 39);
            case "rare" -> new Color(201, 155, 33);
            case "uncommon" -> new Color(139, 157, 168);
            default -> new Color(64, 65, 67);
        };
    }

    private Color cardColor(CardInfo value) {
        if (value == null) return new Color(218, 216, 205);
        List<String> identity = value.getColorIdentity();
        if (identity == null || identity.isEmpty()) {
            return new Color(218, 216, 205);
        }
        Set<String> colors = new HashSet<>(identity);
        if (colors.size() > 1) return new Color(224, 201, 111);
        return switch (colors.iterator().next()) {
            case "W" -> new Color(245, 239, 210);
            case "U" -> new Color(168, 211, 234);
            case "B" -> new Color(190, 184, 196);
            case "R" -> new Color(235, 166, 146);
            case "G" -> new Color(168, 207, 170);
            default -> new Color(218, 216, 205);
        };
    }

    private Color contrast(Color color) {
        double luminance = .299 * color.getRed()
                + .587 * color.getGreen() + .114 * color.getBlue();
        return luminance > 145 ? new Color(42, 42, 42) : Color.WHITE;
    }

    private Color blend(Color left, Color right, float amount) {
        float n = Math.max(0, Math.min(1, amount));
        return new Color(
                Math.round(left.getRed() * (1 - n) + right.getRed() * n),
                Math.round(left.getGreen() * (1 - n) + right.getGreen() * n),
                Math.round(left.getBlue() * (1 - n) + right.getBlue() * n));
    }

    private String fit(String value, FontMetrics metrics, int width) {
        if (metrics.stringWidth(value) <= width) return value;
        String ellipsis = "…";
        int end = value.length();
        while (end > 0 && metrics.stringWidth(
                value.substring(0, end) + ellipsis) > width) end--;
        return value.substring(0, end) + ellipsis;
    }

    private Font fittingNameFont(
            Graphics2D g, Font base, String name, int availableWidth) {
        float size = 13f;
        if (name.contains(" // ")) size -= 1f;
        while (size > 9f && g.getFontMetrics(
                base.deriveFont(Font.BOLD, size)).stringWidth(name)
                > availableWidth) {
            size -= .5f;
        }
        return base.deriveFont(Font.BOLD, size);
    }

    private String toolTip() {
        String name = card == null || card.getName() == null
                ? "Arena card " + arenaId : card.getName();
        String type = card == null ? "" :
                nullToEmpty(card.effectiveTypeLine());
        return "<html><b>" + escape(name) + "</b> [" + arenaId + "]"
                + (type.isBlank() ? "" : "<br><i>" + escape(type) + "</i>")
                + "</html>";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
