package app.draft.ui;

import app.draft.model.DraftTier;
import app.model.card.CardInfo;
import app.replay.ActivatedAbilityParser;
import app.replay.ManaCostPainter;
import app.replay.SvgAssetRenderer;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class DraftCardChip extends JPanel {
    private static final SvgAssetRenderer SVG = new SvgAssetRenderer();
    private static final ManaCostPainter MANA = new ManaCostPainter(SVG, 15);
    private static final ActivatedAbilityParser ABILITIES =
            new ActivatedAbilityParser();

    DraftCardChip(CardInfo card, long arenaId, int quantity, DraftTier tier,
                  boolean selected, DraftCardPreview preview) {
        super(new BorderLayout(7, 2));
        Color cardColor = color(card);
        setOpaque(true);
        setBackground(cardColor);
        setBorder(new CompoundBorder(
                new LineBorder(selected ? new Color(45, 105, 190)
                        : new Color(0, 0, 0, 55), selected ? 2 : 1, true),
                new EmptyBorder(5, 7, 5, 7)));
        setPreferredSize(new Dimension(310, 66));

        String name = card == null || card.getName() == null
                ? "Arena card " + arenaId : card.getName();
        JPanel heading = new JPanel(new BorderLayout(5, 0));
        heading.setOpaque(false);
        heading.add(new SvgIcon(typeIcon(card), 20), BorderLayout.WEST);
        JLabel title = new JLabel((quantity > 1 ? quantity + "×  " : "") + name);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        heading.add(title, BorderLayout.CENTER);
        heading.add(new ManaIcon(card == null ? null : card.getManaCost(),
                cardColor), BorderLayout.EAST);
        add(heading, BorderLayout.NORTH);

        JPanel details = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        details.setOpaque(false);
        addRarity(details, card);
        addKeywords(details, card);
        addAbility(details, card, cardColor);
        add(details, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.setOpaque(false);
        if (tier != null) right.add(badge(tier.name(), tierColor(tier), true));
        if (card != null && card.getPower() != null
                && card.getToughness() != null) {
            JLabel pt = badge(card.getPower() + "/" + card.getToughness(),
                    new Color(248, 247, 234), true);
            pt.setFont(pt.getFont().deriveFont(Font.BOLD, 16f));
            right.add(pt);
        }
        add(right, BorderLayout.EAST);
        setToolTipText(toolTip(card, name, arenaId));

        MouseAdapter hover = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent event) {
                preview.show(card, DraftCardChip.this);
            }
            @Override public void mouseExited(MouseEvent event) {
                preview.hide();
            }
        };
        addMouseListener(hover);
        installHover(this, hover);
    }

    private void installHover(Container parent, MouseAdapter hover) {
        for (Component child : parent.getComponents()) {
            child.addMouseListener(hover);
            if (child instanceof Container container) installHover(container, hover);
        }
    }

    private void addRarity(JPanel details, CardInfo card) {
        if (card == null || card.getRarity() == null) return;
        String rarity = card.getRarity().toLowerCase(Locale.ROOT);
        Color color = switch (rarity) {
            case "mythic" -> new Color(226, 116, 48);
            case "rare" -> new Color(216, 180, 63);
            case "uncommon" -> new Color(167, 177, 184);
            default -> new Color(235, 235, 225);
        };
        details.add(badge(rarity.substring(0, 1).toUpperCase(Locale.ROOT),
                color, true));
    }

    private void addKeywords(JPanel details, CardInfo card) {
        if (card == null || card.getKeywords() == null) return;
        Set<String> supported = Set.of("deathtouch", "defender", "double strike",
                "first strike", "flying", "haste", "hexproof",
                "indestructible", "lifelink", "menace", "reach", "trample",
                "vigilance", "ward");
        for (String keyword : card.getKeywords()) {
            String normalized = keyword.toLowerCase(Locale.ROOT);
            if (!supported.contains(normalized)) continue;
            details.add(new SvgIcon("/keyword-svg/ability-"
                    + normalized.replace(" ", "") + ".svg", 18, keyword));
        }
    }

    private void addAbility(JPanel details, CardInfo card, Color color) {
        ActivatedAbilityParser.Badge ability = ABILITIES.parse(card);
        if (ability == null) return;
        JPanel chip = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 1));
        chip.setOpaque(true);
        chip.setBackground(new Color(255, 255, 255, 145));
        chip.setBorder(new LineBorder(new Color(0, 0, 0, 55), 1, true));
        if (ability.tap()) chip.add(new SvgIcon("/svg/tap.svg", 16, "Tap"));
        if (!ability.manaCost().isBlank()) {
            chip.add(new ManaIcon(ability.manaCost(), color));
        }
        if (!ability.textCost().isBlank()) chip.add(new JLabel(ability.textCost()));
        for (String mana : ability.manaOptions()) {
            chip.add(new ManaIcon("{" + mana + "}", color));
        }
        details.add(chip);
    }

    private JLabel badge(String text, Color background, boolean bold) {
        JLabel label = new JLabel(text);
        if (bold) label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setOpaque(true);
        label.setBackground(background);
        label.setBorder(new CompoundBorder(
                new LineBorder(new Color(0, 0, 0, 55), 1, true),
                new EmptyBorder(2, 5, 2, 5)));
        return label;
    }

    private String typeIcon(CardInfo card) {
        String type = card == null ? "" : String.valueOf(card.effectiveTypeLine());
        for (String candidate : List.of("land", "creature", "planeswalker",
                "artifact", "enchantment", "instant", "sorcery")) {
            if (type.toLowerCase(Locale.ROOT).contains(candidate)) {
                return "/svg/" + candidate + ".svg";
            }
        }
        return null;
    }

    private String toolTip(CardInfo card, String name, long arenaId) {
        StringBuilder out = new StringBuilder("<html><b>")
                .append(escape(name)).append("</b> [").append(arenaId).append(']');
        if (card != null && card.effectiveTypeLine() != null) {
            out.append("<br><i>").append(escape(card.effectiveTypeLine()))
                    .append("</i>");
        }
        return out.append("</html>").toString();
    }

    private Color color(CardInfo card) {
        if (card == null) return new Color(218, 216, 205);
        List<String> identity = card.getColorIdentity();
        if (identity == null || identity.isEmpty()) return new Color(218, 216, 205);
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

    private Color tierColor(DraftTier tier) {
        return switch (tier) {
            case S -> new Color(239, 190, 80);
            case A -> new Color(141, 205, 145);
            case B -> new Color(150, 195, 225);
            case C -> new Color(210, 210, 210);
            case D -> new Color(225, 180, 145);
            case F -> new Color(215, 135, 135);
        };
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static final class SvgIcon extends JComponent {
        private final String resource;
        private final int size;
        SvgIcon(String resource, int size) { this(resource, size, null); }
        SvgIcon(String resource, int size, String tooltip) {
            this.resource = resource;
            this.size = size;
            setToolTipText(tooltip);
            setPreferredSize(new Dimension(resource == null ? 0 : size, size));
        }
        @Override protected void paintComponent(Graphics graphics) {
            if (resource != null) SVG.paint((Graphics2D) graphics, resource,
                    0, 0, size, size);
        }
    }

    private static final class ManaIcon extends JComponent {
        private final String cost;
        private final Color color;
        ManaIcon(String cost, Color color) {
            this.cost = cost == null ? "" : cost;
            this.color = color;
            setPreferredSize(new Dimension(MANA.width(this.cost) + 2, 19));
        }
        @Override protected void paintComponent(Graphics graphics) {
            MANA.paint((Graphics2D) graphics, cost, 1, 2, color);
        }
    }
}
