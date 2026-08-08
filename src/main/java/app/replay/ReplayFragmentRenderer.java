package app.replay;

import app.model.card.CardInfo;
import app.model.event.GameEvent;
import app.model.game.BoardPermanentSnapshot;
import app.model.game.CounterState;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.Locale;

/** Sizes and paints replay fragments and card-state chips. */
final class ReplayFragmentRenderer {
    interface Host {
        Font font();
        Color foreground();
        Color colorOr(String key, Color fallback);
        boolean isHovered(Rectangle bounds);
        void registerHitbox(Rectangle bounds, CardInfo card, GameEvent event,
                            BoardPermanentSnapshot permanent);
    }

    private static final int CHIP_X_PADDING = 12;
    private static final int CHIP_Y_PADDING = 2;
    private static final int CHIP_ARC = 14;
    private static final int SYMBOL_SIZE = 11;
    private static final int CARD_MANA_GAP = 14;
    private static final int CARD_TYPE_ICON_SIZE = 11;
    private static final int CARD_TYPE_GAP = 6;

    private final Host host;
    private final SvgAssetRenderer svgAssets = new SvgAssetRenderer();
    private final ManaCostPainter manaCostPainter =
            new ManaCostPainter(svgAssets, SYMBOL_SIZE - 2);
    private final ManaCostPainter miniManaCostPainter =
            new ManaCostPainter(svgAssets, 8);
    private final ActivatedAbilityParser activatedAbilityParser =
            new ActivatedAbilityParser();

    ReplayFragmentRenderer(Host host) {
        this.host = host;
    }

    boolean paintSvg(Graphics2D graphics, String resource,
                     int x, int y, int width, int height) {
        return svgAssets.paintTinted(graphics, resource, x, y, width, height,
                host.colorOr("Label.foreground", host.foreground()));
    }

    int width(Graphics2D g, ReplayFragment fragment) {
        FontMetrics fm = g.getFontMetrics(host.font());
        if (fragment instanceof TextFragment t) return fm.stringWidth(t.text());
        if (fragment instanceof ManaFragment) return SYMBOL_SIZE + 2;
        if (fragment instanceof PowerToughnessFragment pt) {
            return fm.stringWidth(pt.power() + "/" + pt.toughness()) + CHIP_X_PADDING * 2;
        }
        if (fragment instanceof KeywordFragment keyword) {
            return SYMBOL_SIZE + 4 + fm.stringWidth(keyword.label()) + CHIP_X_PADDING * 2;
        }
        CardFragment cardFragment = (CardFragment) fragment;
        CardInfo card = cardFragment.card();
        Font chipFont = cardNameFont(cardFragment.label());
        FontMetrics chipMetrics = g.getFontMetrics(chipFont);
        int mana = manaCostPainter.width(card.getManaCost());
        int typeIcon = cardTypeResource(card) == null ? 0 : CARD_TYPE_ICON_SIZE + CARD_TYPE_GAP;
        int flipIcon = card.isMultiFaced() ? CARD_TYPE_ICON_SIZE + 3 : 0;
        boolean[] unlocks = roomUnlockSides(cardFragment);
        int lockWidth = (unlocks[0] ? SYMBOL_SIZE + 3 : 0)
                + (unlocks[1] ? SYMBOL_SIZE + 3 : 0);
        return typeIcon + flipIcon + chipMetrics.stringWidth(cardFragment.label()) + lockWidth
                + CHIP_X_PADDING * 2 + mana + (mana > 0 ? CARD_MANA_GAP : 0);
    }

    void paintCardOutline(Graphics2D g, CardFragment fragment, int x, int topY,
                          int lineHeight, Color color, float strokeWidth) {
        if (fragment == null || fragment.card() == null || color == null) return;
        FontMetrics fm = g.getFontMetrics(host.font());
        int width = width(g, fragment);
        int chipHeight = fm.getHeight() + CHIP_Y_PADDING * 2;
        int chipY = topY + (lineHeight - chipHeight) / 2;
        Shape cardShape = cardChipShape(fragment.card(), x, chipY, width, chipHeight);
        Stroke oldStroke = g.getStroke();
        Color oldColor = g.getColor();
        try {
            float widthPx = Math.max(1f, strokeWidth);
            BasicStroke stroke = new BasicStroke(widthPx,
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            Shape stroked = stroke.createStrokedShape(cardShape);
            java.awt.geom.Area overlay = new java.awt.geom.Area(stroked);
            if (fragment.card().getRarity() != null) {
                int badgeX = x - 3;
                int badgeY = chipY - 5;
                java.awt.geom.Ellipse2D.Float badge =
                        new java.awt.geom.Ellipse2D.Float(badgeX - 2, badgeY - 2, 17, 17);
                overlay.subtract(new java.awt.geom.Area(badge));
                g.setColor(color);
                g.fill(overlay);
                g.setStroke(stroke);
                g.draw(new java.awt.geom.Ellipse2D.Float(badgeX, badgeY, 13, 13));
            } else {
                g.setColor(color);
                g.fill(overlay);
            }
        } finally {
            g.setStroke(oldStroke);
            g.setColor(oldColor);
        }
    }

    void paint(Graphics2D g, ReplayFragment fragment, int x, int topY,
                               int lineHeight, GameEvent event) {
        paint(g, fragment, x, topY, lineHeight, event, true);
    }

    void paint(Graphics2D g, ReplayFragment fragment, int x, int topY,
                               int lineHeight, GameEvent event, boolean registerHitbox) {
        FontMetrics fm = g.getFontMetrics(host.font());
        int baseline = topY + (lineHeight - fm.getHeight()) / 2 + fm.getAscent();
        if (fragment instanceof TextFragment t) {
            g.setColor(host.colorOr("TextArea.foreground", host.foreground()));
            drawGlyphText(g, t.text(), x, baseline);
            return;
        }
        if (fragment instanceof ManaFragment mana) {
            paintMana(g, mana.symbol(), x, topY + (lineHeight - SYMBOL_SIZE) / 2);
            return;
        }
        if (fragment instanceof PowerToughnessFragment pt) {
            paintPowerToughnessChip(g, pt, x, topY, lineHeight);
            return;
        }
        if (fragment instanceof KeywordFragment keyword) {
            paintKeywordChip(g, keyword, x, topY, lineHeight);
            return;
        }

        CardFragment cardFragment = (CardFragment) fragment;
        CardInfo card = cardFragment.card();
        int width = width(g, fragment);
        int chipHeight = fm.getHeight() + CHIP_Y_PADDING * 2;
        int chipY = topY + (lineHeight - chipHeight) / 2;
        Rectangle bounds = new Rectangle(x, chipY, width, chipHeight);
        boolean hot = host.isHovered(bounds);

        Color base = cardColor(card);
        Color edge = blend(base, Color.BLACK, .28f);
        if (hot) base = blend(base, Color.WHITE, .18f);
        Shape cardShape = cardChipShape(card, x, chipY, width, chipHeight);
        g.setColor(base);
        g.fill(cardShape);
        g.setColor(edge);
        g.draw(cardShape);

        Color textColor = contrast(base);
        g.setColor(textColor);
        Font oldFont = g.getFont();
        Font chipFont = cardNameFont(cardFragment.label());
        g.setFont(chipFont);
        FontMetrics chipMetrics = g.getFontMetrics();
        int textX = x + CHIP_X_PADDING;
        String typeResource = cardTypeResource(card);
        if (typeResource != null) {
            int iconY = chipY + (chipHeight - CARD_TYPE_ICON_SIZE) / 2 + 1;
            if (svgAssets.paintTinted(g, "/svg/" + typeResource + ".svg",
                    textX, iconY, CARD_TYPE_ICON_SIZE, CARD_TYPE_ICON_SIZE, textColor)) {
                textX += CARD_TYPE_ICON_SIZE + CARD_TYPE_GAP;
            }
        }
        if (card.isMultiFaced()) {
            int iconY = chipY + (chipHeight - CARD_TYPE_ICON_SIZE) / 2 + 1;
            if (svgAssets.paintTinted(g, "/svg/ability-transform.svg",
                    textX, iconY, CARD_TYPE_ICON_SIZE, CARD_TYPE_ICON_SIZE, textColor)) {
                textX += CARD_TYPE_ICON_SIZE + 3;
            }
        }
        boolean[] unlocks = roomUnlockSides(cardFragment);
        int lockY = chipY + (chipHeight - SYMBOL_SIZE) / 2 + 1;
        if (unlocks[0] && svgAssets.paintTinted(g, "/svg/open-lock.svg",
                textX, lockY, SYMBOL_SIZE, SYMBOL_SIZE, textColor)) {
            textX += SYMBOL_SIZE + 3;
        }
        int textBaseline = chipY + (chipHeight - chipMetrics.getHeight()) / 2 + chipMetrics.getAscent();
        drawGlyphText(g, cardFragment.label(), textX, textBaseline);
        textX += chipMetrics.stringWidth(cardFragment.label());
        if (unlocks[1]) {
            int rightLockX = textX + 3;
            if (svgAssets.paintTinted(g, "/svg/open-lock.svg",
                    rightLockX, lockY, SYMBOL_SIZE, SYMBOL_SIZE, textColor)) {
                textX = rightLockX + SYMBOL_SIZE;
            }
        }
        if (false) {
            int badgeX = textX + CARD_TYPE_GAP;
            int badgeWidth = chipMetrics.stringWidth(cardFragment.stateLabel()) + 10;
            int badgeY = chipY + 3;
            int badgeHeight = chipHeight - 6;
            Color badge = blend(base, Color.WHITE, .28f);
            g.setColor(badge);
            g.fill(new RoundRectangle2D.Float(badgeX, badgeY, badgeWidth, badgeHeight, 10, 10));
            g.setColor(contrast(badge));
            Font badgeFont = chipFont.deriveFont(Font.PLAIN, Math.max(8f, chipFont.getSize2D() - 2f));
            g.setFont(badgeFont);
            FontMetrics badgeMetrics = g.getFontMetrics();
            drawGlyphText(g, cardFragment.stateLabel(), badgeX + 5,
                    badgeY + (badgeHeight - badgeMetrics.getHeight()) / 2 + badgeMetrics.getAscent());
            g.setFont(chipFont);
        }

        int manaWidth = manaCostPainter.width(card.getManaCost());
        if (manaWidth > 0) {
            int manaX = x + width - CHIP_X_PADDING - manaWidth;
            int manaY = chipY + (chipHeight - (SYMBOL_SIZE - 2)) / 2;
            manaCostPainter.paint(g, card.getManaCost(), manaX, manaY, base);
        }
        if (cardFragment.permanent() != null) {
            BoardPermanentSnapshot permanent = cardFragment.permanent();
            if (permanent.getPower() != null && permanent.getToughness() != null) {
                paintPowerToughnessChip(g,
                        new PowerToughnessFragment(
                                String.valueOf(permanent.getPower()),
                                String.valueOf(permanent.getToughness())),
                        x + width, topY, lineHeight);
            }
            paintActivatedAbilityMiniChip(g, card, x - 3, topY, lineHeight);
            paintPermanentAbilityMiniChip(
                    g, permanent, x + width, topY, lineHeight);
            paintRarityMiniChip(g, card, x, chipY);
            paintTappedMiniChip(g, permanent, x + 10, chipY);
            paintCounterMiniChip(g, permanent, x + width, chipY);
            paintSagaChapterMiniChip(g, permanent, x + width, chipY);
        } else {
            paintRarityMiniChip(g, card, x, chipY);
        }
        g.setFont(oldFont);
        if (registerHitbox) {
            host.registerHitbox(bounds, card, event, cardFragment.permanent());
        }
    }

    private Font cardNameFont(String name) {
        String value = nullToEmpty(name);
        float normal = Math.max(9f, host.font().getSize2D() - 1f);
        float reduction = 0f;
        if (value.contains(" // ")) reduction += 1f;
        if (value.length() > 28) reduction += 1f;
        if (value.length() > 42) reduction += 1f;
        return host.font().deriveFont(
                Font.BOLD, Math.max(8f, normal - reduction));
    }

    private Shape cardChipShape(
            CardInfo card, int x, int y, int width, int height) {
        String typeLine = card == null ? ""
                : nullToEmpty(card.effectiveTypeLine()).toLowerCase(Locale.ROOT);
        if (!typeLine.contains("legendary")) {
            return new RoundRectangle2D.Float(
                    x, y, width, height, CHIP_ARC, CHIP_ARC);
        }
        int shoulder = Math.min(7, Math.max(3, height / 4));
        Path2D path = new Path2D.Float();
        path.moveTo(x + 7, y);
        path.lineTo(x + width * .17, y);
        path.curveTo(x + width * .22, y,
                x + width * .18, y - shoulder,
                x + width * .26, y - shoulder);
        path.curveTo(x + width * .23, y - shoulder - 3,
                x + width * .31, y - shoulder - 3,
                x + width * .35, y - shoulder);
        path.lineTo(x + width * .65, y - shoulder);
        path.curveTo(x + width * .69, y - shoulder - 3,
                x + width * .77, y - shoulder - 3,
                x + width * .74, y - shoulder);
        path.curveTo(x + width * .82, y - shoulder,
                x + width * .78, y,
                x + width * .83, y);
        path.lineTo(x + width - 7, y);
        path.quadTo(x + width, y, x + width, y + 7);
        path.lineTo(x + width, y + height - 7);
        path.quadTo(x + width, y + height,
                x + width - 7, y + height);
        path.lineTo(x + 7, y + height);
        path.quadTo(x, y + height, x, y + height - 7);
        path.lineTo(x, y + 7);
        path.quadTo(x, y, x + 7, y);
        path.closePath();
        return path;
    }

    private void paintRarityMiniChip(
            Graphics2D g, CardInfo card, int leftX, int chipY) {
        if (card == null || card.getRarity() == null) return;
        Color rarity = switch (card.getRarity().toLowerCase(Locale.ROOT)) {
            case "mythic" -> new Color(0xD95B27);
            case "rare" -> new Color(0xC99B21);
            case "uncommon" -> new Color(0x8B9DA8);
            default -> new Color(0x404143);
        };
        int size = 9;
        int x = leftX - 3;
        int y = chipY - 5;
        g.setColor(new Color(250, 250, 245, 235));
        g.fillOval(x, y, size + 4, size + 4);
        g.setColor(rarity);
        g.drawOval(x, y, size + 4, size + 4);
        if (!svgAssets.paintTinted(g, "/svg/rarity.svg",
                x + 2, y + 2, size, size, rarity)) {
            g.fillOval(x + 4, y + 4, size - 4, size - 4);
        }
    }

    private void paintCounterMiniChip(
            Graphics2D g, BoardPermanentSnapshot permanent,
            int rightX, int chipY) {
        List<CounterState> counters = permanent.getCounters().stream()
                .filter(counter -> counter != null && counter.getCount() > 0)
                .filter(counter -> permanent.getSagaChapter() == null
                        || (counter.getArenaType() != 108
                        && !"Lore".equalsIgnoreCase(counter.getType())
                        && !"Counter#108".equalsIgnoreCase(counter.getType())))
                .limit(3)
                .toList();
        if (counters.isEmpty()) return;
        Font old = g.getFont();
        Font compact = old.deriveFont(Font.BOLD, 8f);
        FontMetrics metrics = g.getFontMetrics(compact);
        int width = 5;
        for (CounterState counter : counters) {
            width += 9 + metrics.stringWidth(
                    counter.getCount() > 1 ? String.valueOf(counter.getCount()) : "");
        }
        int x = rightX - width + 3;
        int y = chipY - 5;
        Color base = blend(host.colorOr("TextArea.background", Color.WHITE),
                host.colorOr("List.selectionBackground", new Color(0x6D7F9B)), .22f);
        paintStateMiniChip(g, x, y, width, 14, base);
        int cursor = x + 3;
        g.setFont(compact);
        for (CounterState counter : counters) {
            String resource = counterResource(counter);
            if (!svgAssets.paintTinted(g, resource, cursor, y + 3, 8, 8, contrast(base))) {
                paintFallbackSymbol(g, "•", cursor, y + 3, 8);
            }
            cursor += 8;
            if (counter.getCount() > 1) {
                String count = String.valueOf(counter.getCount());
                g.setColor(contrast(base));
                drawGlyphText(g, count, cursor,
                        y + (14 - metrics.getHeight()) / 2 + metrics.getAscent());
                cursor += metrics.stringWidth(count);
            }
            cursor += 1;
        }
        g.setFont(old);
    }


    private void paintSagaChapterMiniChip(
            Graphics2D g, BoardPermanentSnapshot permanent,
            int rightX, int chipY) {
        Integer chapter = permanent.getSagaChapter();
        if (chapter == null || chapter <= 0) return;
        String value = chapter <= 10 ? romanNumeral(chapter) : String.valueOf(chapter);
        Font old = g.getFont();
        Font compact = old.deriveFont(Font.BOLD, 8f);
        FontMetrics metrics = g.getFontMetrics(compact);
        int width = Math.max(18, metrics.stringWidth(value) + 10);
        int x = rightX - width + 3;
        int y = chipY - 5;
        Color base = blend(host.colorOr("TextArea.background", Color.WHITE),
                host.colorOr("List.selectionBackground", new Color(0x6D7F9B)), .36f);
        paintStateMiniChip(g, x, y, width, 14, base);
        g.setFont(compact);
        g.setColor(contrast(base));
        drawGlyphText(g, value, x + (width - metrics.stringWidth(value)) / 2,
                y + (14 - metrics.getHeight()) / 2 + metrics.getAscent());
        g.setFont(old);
    }

    private String romanNumeral(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(value);
        };
    }

    private String counterResource(CounterState counter) {
        String type = nullToEmpty(counter.getType()).toLowerCase(Locale.ROOT);
        if (type.contains("+1/+1")) return "/svg/counter-plus.svg";
        if (type.contains("-1/-1") || type.contains("−1/−1")) {
            return "/svg/counter-minus.svg";
        }
        for (String known : List.of("charge", "doom", "flood", "fungus",
                "gold", "lore", "loyalty", "shield", "stun", "time")) {
            if (type.contains(known)) return "/svg/counter-" + known + ".svg";
        }
        return "/svg/counter-pin.svg";
    }

    private void paintStateMiniChip(
            Graphics2D g, int x, int y, int width, int height, Color base) {
        Shape chip = new RoundRectangle2D.Float(
                x, y, width, height, height - 2, height - 2);
        g.setColor(base);
        g.fill(chip);
        g.setColor(blend(base, Color.BLACK, .28f));
        g.draw(chip);
    }

    private boolean[] roomUnlockSides(CardFragment fragment) {
        boolean[] result = new boolean[2];
        String name = fragment.card() == null ? "" : nullToEmpty(fragment.card().getName());
        if (!name.contains(" // ") || fragment.stateLabel().isBlank()) return result;
        String[] halves = name.split(" // ", 2);
        String state = fragment.stateLabel().toLowerCase(Locale.ROOT);
        if ("unlock".equals(state)) {
            result[fragment.label().equalsIgnoreCase(halves[1]) ? 1 : 0] = true;
            return result;
        }
        result[0] = state.contains(halves[0].toLowerCase(Locale.ROOT));
        result[1] = state.contains(halves[1].toLowerCase(Locale.ROOT));
        return result;
    }

    private void paintPermanentAbilityMiniChip(Graphics2D g, BoardPermanentSnapshot permanent,
                                               int rightX, int topY,
                                               int lineHeight) {
        List<String> abilities = permanent.getEvergreenAbilities();
        if (abilities == null || abilities.isEmpty()) return;
        int visible = Math.min(4, abilities.size());
        int iconSize = 8;
        int padding = 2;
        int gap = 1;
        int width = padding * 2 + visible * iconSize + Math.max(0, visible - 1) * gap;
        int height = iconSize + padding * 2;
        int x = rightX - width - 4;
        int y = topY + lineHeight - height - 1;
        Color base = blend(host.colorOr("TextArea.background", Color.WHITE),
                host.colorOr("List.selectionBackground", new Color(0x6D7F9B)), .18f);
        Shape chip = new RoundRectangle2D.Float(x, y, width, height, 10, 10);
        g.setColor(base);
        g.fill(chip);
        g.setColor(blend(base, Color.BLACK, .28f));
        g.draw(chip);
        int iconX = x + padding;
        for (int i = 0; i < visible; i++) {
            paintKeyword(g, abilities.get(i), iconX, y + padding, iconSize);
            iconX += iconSize + gap;
        }
    }


    private void paintTappedMiniChip(Graphics2D g,
                                     BoardPermanentSnapshot permanent,
                                     int leftX, int chipY) {
        if (!Boolean.TRUE.equals(permanent.getTapped())) return;

        int iconSize = 8;
        int padding = 2;
        int diameter = iconSize + padding * 2 + 1;
        int x = leftX;
        int y = chipY - 5;

        Color base = blend(host.colorOr("TextArea.background", Color.WHITE),
                new Color(0xC94F4F), .55f);
        g.setColor(base);
        g.fillOval(x, y, diameter, diameter);
        g.setColor(blend(base, Color.BLACK, .28f));
        g.drawOval(x, y, diameter, diameter);

        if (!svgAssets.paintTinted(g, "/svg/tap.svg",
                x + padding, y + padding, iconSize, iconSize, contrast(base))) {
            paintFallbackSymbol(g, "T", x + padding, y + padding, iconSize);
        }
    }


    private void paintActivatedAbilityMiniChip(Graphics2D g, CardInfo card,
                                                 int cardNameX, int topY, int lineHeight) {
        List<ActivatedAbilityParser.Badge> badges = activatedAbilityParser.parseAll(card);
        if (badges.isEmpty()) return;
        int cursor = cardNameX;
        for (ActivatedAbilityParser.Badge badge : badges) {
            int painted = paintActivatedAbilityBadge(g, badge, cursor, topY, lineHeight);
            if (painted > 0) cursor += painted + 3;
        }
    }

    private int paintActivatedAbilityBadge(Graphics2D g, ActivatedAbilityParser.Badge badge,
                                            int x, int topY, int lineHeight) {
        Font old = g.getFont();
        Font compact = old.deriveFont(Font.PLAIN, Math.max(6f, old.getSize2D() - 5f));
        FontMetrics metrics = g.getFontMetrics(compact);
        int tapSize = 8;
        int padding = 2;
        int gap = 2;
        int contentWidth = 0;
        if (badge.tap()) contentWidth += tapSize;
        if (!badge.manaCost().isBlank()) {
            if (contentWidth > 0) contentWidth += gap;
            contentWidth += miniManaCostPainter.width(badge.manaCost());
        }
        if (!badge.textCost().isBlank()) {
            if (contentWidth > 0) contentWidth += gap;
            contentWidth += metrics.stringWidth(badge.textCost());
        }
        if (!badge.manaOptions().isEmpty()) contentWidth += manaOptionsWidth(g, badge.manaOptions(), compact);
        if (contentWidth == 0) return 0;

        int width = padding * 2 + contentWidth;
        int height = Math.max(12, tapSize + padding * 2);
        int y = topY + lineHeight - height - 1;
        Color base = blend(host.colorOr("TextArea.background", Color.WHITE),
                host.colorOr("List.selectionBackground", new Color(0x6D7F9B)), .18f);
        Shape chip = new RoundRectangle2D.Float(x, y, width, height, 10, 10);
        g.setColor(base); g.fill(chip);
        g.setColor(blend(base, Color.BLACK, .28f)); g.draw(chip);

        int cursor = x + padding;
        if (badge.tap()) {
            if (!svgAssets.paintTinted(g, "/svg/tap.svg", cursor, y + padding,
                    tapSize, tapSize, contrast(base))) {
                paintFallbackSymbol(g, "T", cursor, y + padding, tapSize);
            }
            cursor += tapSize;
        }
        if (!badge.manaCost().isBlank()) {
            if (cursor > x + padding) cursor += gap;
            miniManaCostPainter.paint(g, badge.manaCost(), cursor, y + (height - 8) / 2, base);
            cursor += miniManaCostPainter.width(badge.manaCost());
        }
        if (!badge.textCost().isBlank()) {
            if (cursor > x + padding) cursor += gap;
            g.setColor(contrast(base)); g.setFont(compact);
            drawGlyphText(g, badge.textCost(), cursor,
                    y + (height - metrics.getHeight()) / 2 + metrics.getAscent());
            cursor += metrics.stringWidth(badge.textCost());
        }
        if (!badge.manaOptions().isEmpty()) {
            paintManaOptions(g, badge.manaOptions(), cursor, y, height, compact, base);
        }
        g.setFont(old);
        return width;
    }

    private int manaOptionsWidth(Graphics2D g, List<String> options, Font font) {
        FontMetrics metrics = g.getFontMetrics(font);
        int width = metrics.stringWidth(": ");
        for (int i = 0; i < options.size(); i++) {
            width += miniManaCostPainter.width("{" + options.get(i) + "}");
            if (i + 1 < options.size()) {
                width += metrics.stringWidth(" | ");
            }
        }
        return width;
    }

    private void paintManaOptions(Graphics2D g, List<String> options, int x, int y,
                                  int height, Font font, Color base) {
        Font old = g.getFont();
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        int baseline = y + (height - metrics.getHeight()) / 2 + metrics.getAscent();

        g.setColor(contrast(base));
        drawGlyphText(g, ": ", x, baseline);
        int cursor = x + metrics.stringWidth(": ");

        for (int i = 0; i < options.size(); i++) {
            String manaCost = "{" + options.get(i) + "}";
            miniManaCostPainter.paint(g, manaCost, cursor,
                    y + (height - 8) / 2, base);
            cursor += miniManaCostPainter.width(manaCost);

            if (i + 1 < options.size()) {
                g.setColor(contrast(base));
                drawGlyphText(g, " | ", cursor, baseline);
                cursor += metrics.stringWidth(" | ");
            }
        }
        g.setFont(old);
    }

    private void paintPowerToughnessChip(Graphics2D g, PowerToughnessFragment pt,
                                         int x, int topY, int lineHeight) {
        FontMetrics fm = g.getFontMetrics(host.font());
        String value = pt.power() + "/" + pt.toughness();
        Font old = g.getFont();
        Font compact = old.deriveFont(Font.PLAIN, Math.max(7f, old.getSize2D() - 5f));
        FontMetrics compactMetrics = g.getFontMetrics(compact);
        int overlap = 8;
        x -= overlap;
        int width = compactMetrics.stringWidth(value) + 7;
        int height = Math.max(11, compactMetrics.getHeight()) + 2;
        int y = topY + lineHeight - height - 4;
        Color base = blend(host.colorOr("TextArea.background", Color.WHITE),
                new Color(0x9D785A), .24f);
        Shape chip = new RoundRectangle2D.Float(x, y, width, height, CHIP_ARC, CHIP_ARC);
        g.setColor(base);
        g.fill(chip);
        g.setColor(blend(base, Color.BLACK, .30f));
        g.draw(chip);
        g.setColor(contrast(base));
        g.setFont(compact);
        drawGlyphText(g, value, x + 4,
                y + (height - compactMetrics.getHeight()) / 2 + compactMetrics.getAscent());
        g.setFont(old);
    }

    private void paintKeywordChip(Graphics2D g, KeywordFragment keyword,
                                  int x, int topY, int lineHeight) {
        FontMetrics fm = g.getFontMetrics(host.font());
        int width = width(g, keyword);
        int height = fm.getHeight() + CHIP_Y_PADDING * 2;
        int y = topY + (lineHeight - height) / 2;
        Color base = blend(host.colorOr("TextArea.background", Color.WHITE),
                host.colorOr("List.selectionBackground", new Color(0x6D7F9B)), .14f);
        Shape chip = new RoundRectangle2D.Float(x, y, width, height, CHIP_ARC, CHIP_ARC);
        g.setColor(base);
        g.fill(chip);
        g.setColor(blend(base, Color.BLACK, .24f));
        g.draw(chip);
        int iconY = topY + (lineHeight - SYMBOL_SIZE) / 2;
        paintKeyword(g, keyword.keyword(), x + CHIP_X_PADDING, iconY);
        g.setColor(contrast(base));
        drawGlyphText(g, keyword.label(), x + CHIP_X_PADDING + SYMBOL_SIZE + 4,
                topY + (lineHeight - fm.getHeight()) / 2 + fm.getAscent());
    }

    private void paintMana(Graphics2D g, String symbol, int x, int y) {
        String normalized = normalizeSymbol(symbol).replace("/", "_");
        if (svgAssets.paint(g, "/mana-svg/" + normalized + ".svg",
                x, y, SYMBOL_SIZE, SYMBOL_SIZE)) return;
        paintFallbackSymbol(g, symbol, x, y, SYMBOL_SIZE);
    }

    private void paintKeyword(Graphics2D g, String keyword, int x, int y) {
        paintKeyword(g, keyword, x, y, SYMBOL_SIZE);
    }

    private void paintKeyword(
            Graphics2D g, String keyword, int x, int y, int size) {
        String resource = switch (keyword.toLowerCase(Locale.ROOT)) {
            case "double strike" -> "doublestrike";
            case "first strike" -> "firststrike";
            default -> keyword.toLowerCase(Locale.ROOT).replace(' ', '-');
        };
        if (svgAssets.paintTinted(g, "/keyword-svg/ability-" + resource + ".svg",
                x, y, size, size,
                host.colorOr("Label.foreground", host.foreground()))) return;
        paintFallbackSymbol(g, keyword.substring(0, 1).toUpperCase(Locale.ROOT),
                x, y, size);
    }

    private void paintFallbackSymbol(Graphics2D g, String text, int x, int y, int size) {
        Color fill = blend(host.colorOr("TextArea.background", Color.WHITE),
                host.colorOr("Label.foreground", Color.DARK_GRAY), .12f);
        g.setColor(fill);
        g.fillOval(x, y, size, size);
        g.setColor(blend(fill, Color.BLACK, .35f));
        g.drawOval(x, y, size, size);
        Font old = g.getFont();
        g.setFont(old.deriveFont(Font.BOLD, text.length() > 2 ? 8f : 10f));
        FontMetrics metrics = g.getFontMetrics();
        String clipped = text.length() > 3 ? text.substring(0, 3) : text;
        drawGlyphText(g, clipped, x + (size - metrics.stringWidth(clipped)) / 2,
                y + (size - metrics.getHeight()) / 2 + metrics.getAscent());
        g.setFont(old);
    }

    private String normalizeSymbol(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private String cardTypeResource(CardInfo card) {
        if (card == null) return null;
        String type = card.effectiveTypeLine();
        if (type == null || type.isBlank()) return null;
        if (type.contains("Land")) return "land";
        if (type.contains("Creature")) return "creature";
        if (type.contains("Planeswalker")) return "planeswalker";
        if (type.contains("Artifact")) return "artifact";
        if (type.contains("Enchantment")) return "enchantment";
        if (type.contains("Instant")) return "instant";
        if (type.contains("Sorcery")) return "sorcery";
        return null;
    }

    private Color cardColor(CardInfo card) {
        List<String> colors = card.getColors() == null || card.getColors().isEmpty()
                ? card.getColorIdentity() : card.getColors();
        if (colors == null || colors.isEmpty()) {
            String type = card.effectiveTypeLine();
            if (type != null && type.contains("Land")) return new Color(0xC9B18A);
            return new Color(0xC7CBD1);
        }
        if (colors.size() > 1) return new Color(0xD6B85A);
        return switch (colors.get(0).toUpperCase(Locale.ROOT)) {
            case "W" -> new Color(0xEEE6C8);
            case "U" -> new Color(0x8CB8D8);
            case "B" -> new Color(0x8E8791);
            case "R" -> new Color(0xD9957E);
            case "G" -> new Color(0x8FBE91);
            default -> new Color(0xC7CBD1);
        };
    }

    private Color contrast(Color color) {
        double luminance = .299 * color.getRed() + .587 * color.getGreen() + .114 * color.getBlue();
        return luminance < 128 ? Color.WHITE : new Color(0x202020);
    }

    private Color blend(Color a, Color b, float amount) {
        float n = Math.max(0, Math.min(1, amount));
        return new Color(
                Math.round(a.getRed() * (1 - n) + b.getRed() * n),
                Math.round(a.getGreen() * (1 - n) + b.getGreen() * n),
                Math.round(a.getBlue() * (1 - n) + b.getBlue() * n),
                Math.round(a.getAlpha() * (1 - n) + b.getAlpha() * n));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    static void drawGlyphText(Graphics2D graphics, String text, float x, float baseline) {
        String value = text == null ? "" : text;
        var glyphs = graphics.getFont().createGlyphVector(
                graphics.getFontRenderContext(), value);
        graphics.drawGlyphVector(glyphs, x, baseline);
    }

}
