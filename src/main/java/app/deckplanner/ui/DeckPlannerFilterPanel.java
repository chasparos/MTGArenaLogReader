package app.deckplanner.ui;

import app.deckplanner.filter.*;
import app.ui.AppColors;
import app.ui.SvgIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.*;

/** Click-first, wrapping filter controls bound to a widget-independent {@link DeckPlannerFilterModel}. */
public final class DeckPlannerFilterPanel extends JPanel implements Scrollable {
    private static final List<String> DEFAULT_FORMATS = List.of("standard", "alchemy", "historic", "timeless", "explorer", "brawl");
    private static final int FILTER_WIDTH = 330;
    private final DeckPlannerFilterModel model;
    private final JComboBox<String> formatBox;
    private final Map<CardColor, FilterChip> colorChips = new EnumMap<>(CardColor.class);
    private final FilterChip colorlessChip = new FilterChip("Colorless", new SvgIcon("/svg/c.svg", 14));
    private final FilterChip phyrexianChip = new FilterChip("Phyrexian", new SvgIcon("/svg/p.svg", 14));
    private final FilterChip candidateOnlyChip =
            new FilterChip("Candidates only", new SvgIcon("/svg/chaos.svg", 14));
    private final JTextField tagFilter = new JTextField();
    private final Map<BaseCardType, FilterChip> typeChips = new EnumMap<>(BaseCardType.class);
    private final Map<SemanticTag, FilterChip> tagChips = new LinkedHashMap<>();
    private final ManaValueRangeControl manaRange = new ManaValueRangeControl();
    private boolean syncing;

    public DeckPlannerFilterPanel(DeckPlannerFilterModel model, Collection<SemanticTag> availableTags) {
        this.model = Objects.requireNonNull(model);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setOpaque(true);
        formatBox = new JComboBox<>(formatsWithCurrent(model.state().format()).toArray(String[]::new));
        formatBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        buildControls(availableTags == null ? List.of() : availableTags);
        bindActions();
        model.addListener(state -> SwingUtilities.invokeLater(() -> syncFromModel(state)));
        syncFromModel(model.state());
        applyControlColors();
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                SwingUtilities.invokeLater(this::refreshWrappedSectionHeights);
            }
        });
    }

    @Override public void addNotify() {
        super.addNotify();
        SwingUtilities.invokeLater(this::refreshWrappedSectionHeights);
    }

    @Override public void updateUI() {
        super.updateUI();
        setBackground(AppColors.color("Panel.background", new Color(0x202328)));
        setForeground(AppColors.color("Label.foreground", Color.WHITE));
        if (colorChips != null) applyControlColors();
    }

    private void buildControls(Collection<SemanticTag> tags) {
        add(section("Format", formatBox));

        JPanel workspaceLayer = flow();
        candidateOnlyChip.setToolTipText(
                "Restrict the catalog to cards currently candidates without changing other filters");
        workspaceLayer.add(candidateOnlyChip);
        add(section("Workspace", workspaceLayer));
        JPanel colors = flow();
        for (CardColor color : CardColor.values()) {
            FilterChip chip = new FilterChip(title(color.name()), new SvgIcon("/svg/" + color.symbol().toLowerCase(Locale.ROOT) + ".svg", 14));
            colorChips.put(color, chip);
            colors.add(chip);
        }
        colors.add(colorlessChip);
        colors.add(phyrexianChip);

        JPanel semantics = flow();
        JRadioButton printed = manaRadio("Printed");
        JRadioButton identity = manaRadio("Identity");
        ButtonGroup sourceGroup = new ButtonGroup(); sourceGroup.add(printed); sourceGroup.add(identity);
        JRadioButton inclusive = manaRadio("Inclusive");
        JRadioButton exact = manaRadio("Exact");
        ButtonGroup modeGroup = new ButtonGroup(); modeGroup.add(inclusive); modeGroup.add(exact);
        semantics.add(printed); semantics.add(identity); semantics.add(inclusive); semantics.add(exact);
        JPanel colorSection = new JPanel();
        colorSection.setOpaque(false);
        colorSection.setLayout(new BoxLayout(colorSection, BoxLayout.Y_AXIS));
        colors.setAlignmentX(Component.LEFT_ALIGNMENT);
        semantics.setAlignmentX(Component.LEFT_ALIGNMENT);
        colorSection.add(colors);
        colorSection.add(Box.createVerticalStrut(2));
        colorSection.add(semantics);
        semantics.putClientProperty("printed", printed); semantics.putClientProperty("identity", identity);
        semantics.putClientProperty("inclusive", inclusive); semantics.putClientProperty("exact", exact);
        add(section("Colors", colorSection));

        JPanel types = flow();
        for (BaseCardType type : BaseCardType.values()) {
            FilterChip chip = new FilterChip(title(type.name()), typeIcon(type));
            typeChips.put(type, chip);
            types.add(chip);
        }
        add(section("Base types", types));

        add(section("Mana value", manaRange));

        tagFilter.putClientProperty("JTextField.placeholderText", "Filter tags…");
        tagFilter.setToolTipText("Filter the visible tag list by name");
        tagFilter.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        add(section("Tags", tagFilter));

        Map<TagCategory, List<SemanticTag>> byCategory = new EnumMap<>(TagCategory.class);
        for (SemanticTag tag : new TreeSet<>(tags)) {
            if (!tag.equals(CardTagRules.PHYREXIAN_MANA)) byCategory.computeIfAbsent(tag.category(), ignored -> new ArrayList<>()).add(tag);
        }
        for (Map.Entry<TagCategory, List<SemanticTag>> entry : byCategory.entrySet()) {
            JPanel tagPanel = flow();
            for (SemanticTag tag : entry.getValue()) {
                FilterChip chip = new FilterChip(tag.label(), tagIcon(tag), true);
                tagChips.put(tag, chip);
                tagPanel.add(chip);
            }
            add(section(title(entry.getKey().name()), tagPanel));
        }
        JButton reset = new JButton("Reset filters", new SvgIcon("/svg/untap.svg", 14));
        reset.setAlignmentX(Component.LEFT_ALIGNMENT);
        reset.setActionCommand("reset");
        reset.setMaximumSize(new Dimension(150, 30));
        add(Box.createVerticalStrut(6));
        add(reset);
        putClientProperty("semantics", semantics);
        putClientProperty("reset", reset);
    }

    AbstractButton candidateOnlyControl() {
        return candidateOnlyChip;
    }

    /** Updates visible faceted counts without changing selected tag state or chip geometry. */
    public void setTagCloud(Map<SemanticTag, Long> counts) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setTagCloud(counts));
            return;
        }
        Map<SemanticTag, Long> effective = counts == null ? Map.of() : counts;
        tagChips.forEach((tag, chip) -> {
            long count = Math.max(0L, effective.getOrDefault(tag, 0L));
            chip.setCount(count);
            chip.setEnabled(count > 0L || chip.isSelected());
        });
    }

    private void bindActions() {
        formatBox.addActionListener(event -> { if (!syncing) model.setFormat((String) formatBox.getSelectedItem()); });
        colorChips.forEach((color, chip) -> chip.addActionListener(event -> { if (!syncing) model.toggleColor(color); }));
        colorlessChip.addActionListener(event -> { if (!syncing) model.setIncludeColorless(colorlessChip.isSelected()); });
        phyrexianChip.addActionListener(event -> { if (!syncing) model.toggleTag(CardTagRules.PHYREXIAN_MANA); });
        candidateOnlyChip.addActionListener(event -> {
            if (!syncing) model.setCandidateOnly(candidateOnlyChip.isSelected());
        });
        tagFilter.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent event) { filterVisibleTags(); }
            public void removeUpdate(javax.swing.event.DocumentEvent event) { filterVisibleTags(); }
            public void changedUpdate(javax.swing.event.DocumentEvent event) { filterVisibleTags(); }
        });
        typeChips.forEach((type, chip) -> chip.addActionListener(event -> { if (!syncing) model.toggleBaseType(type); }));
        tagChips.forEach((tag, chip) -> chip.addActionListener(event -> { if (!syncing) model.toggleTag(tag); }));
        JPanel semantics = (JPanel) getClientProperty("semantics");
        ((AbstractButton) semantics.getClientProperty("printed")).addActionListener(e -> { if (!syncing) model.setColorSource(ColorSource.CARD_COLORS); });
        ((AbstractButton) semantics.getClientProperty("identity")).addActionListener(e -> { if (!syncing) model.setColorSource(ColorSource.COLOR_IDENTITY); });
        ((AbstractButton) semantics.getClientProperty("inclusive")).addActionListener(e -> { if (!syncing) model.setColorMatchMode(ColorMatchMode.INCLUSIVE); });
        ((AbstractButton) semantics.getClientProperty("exact")).addActionListener(e -> { if (!syncing) model.setColorMatchMode(ColorMatchMode.EXACT); });
        manaRange.setRangeListener((minimum, maximum) -> {
            if (syncing) return;
            if (minimum == 0 && maximum == ManaValueRangeControl.MAX_BUCKET) model.setManaValueRange(null);
            else model.setManaValueRange(new ManaValueRange(minimum, maximum == ManaValueRangeControl.MAX_BUCKET ? 30 : maximum));
        });
        ((JButton) getClientProperty("reset")).addActionListener(e -> model.resetFilters());
    }

    private void syncFromModel(DeckPlannerFilterModel.State state) {
        syncing = true;
        try {
            formatBox.setSelectedItem(state.format());
            candidateOnlyChip.setSelected(state.candidateOnly());
            CardFilterState filters = state.filters();
            colorChips.forEach((color, chip) -> chip.setSelected(filters.colors().contains(color)));
            colorlessChip.setSelected(filters.includeColorless());
            phyrexianChip.setSelected(filters.selectedTags().contains(CardTagRules.PHYREXIAN_MANA));
            typeChips.forEach((type, chip) -> chip.setSelected(filters.baseTypes().contains(type)));
            tagChips.forEach((tag, chip) -> chip.setSelected(filters.selectedTags().contains(tag)));
            JPanel semantics = (JPanel) getClientProperty("semantics");
            ((AbstractButton) semantics.getClientProperty(filters.colorSource() == ColorSource.CARD_COLORS ? "printed" : "identity")).setSelected(true);
            ((AbstractButton) semantics.getClientProperty(filters.colorMatchMode() == ColorMatchMode.INCLUSIVE ? "inclusive" : "exact")).setSelected(true);
            ManaValueRange range = filters.manaValueRange();
            manaRange.setRange(range == null ? 0 : bucket(range.minimum()), range == null ? ManaValueRangeControl.MAX_BUCKET : bucket(range.maximum()));
        } finally {
            syncing = false;
        }
    }

    private JPanel section(String title, Component content) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        panel.add(label, BorderLayout.NORTH);
        panel.add(content, BorderLayout.CENTER);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        panel.setBorder(new EmptyBorder(0, 0, 8, 0));
        return panel;
    }

    private JPanel flow() {
        JPanel panel = new JPanel(new WrapLayout(FlowLayout.LEFT, 5, 4));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private JRadioButton manaRadio(String label) {
        JRadioButton button = new JRadioButton(label);
        button.setOpaque(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(true);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 10.5f));
        button.setIcon(new RadioBulletIcon(false));
        button.setSelectedIcon(new RadioBulletIcon(true));
        button.setIconTextGap(5);
        button.setMargin(new Insets(1, 2, 1, 4));
        button.getAccessibleContext().setAccessibleName(label);
        return button;
    }

    /** Theme-aware radio indicator that stays visually light beneath the color chips. */
    private static final class RadioBulletIcon implements Icon {
        private final boolean selected;

        private RadioBulletIcon(boolean selected) {
            this.selected = selected;
        }

        @Override public int getIconWidth() { return 13; }
        @Override public int getIconHeight() { return 13; }

        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color border = AppColors.color("App.border", new Color(0x727985));
                Color accent = AppColors.color("App.accent", new Color(0xC69B52));
                g.setColor(selected ? accent : border);
                g.setStroke(new BasicStroke(1.5f));
                g.drawOval(x + 1, y + 1, 10, 10);
                if (selected) g.fillOval(x + 4, y + 4, 5, 5);
            } finally {
                g.dispose();
            }
        }
    }

    private void filterVisibleTags() {
        String query = tagFilter.getText() == null ? "" : tagFilter.getText().strip().toLowerCase(Locale.ROOT);
        tagChips.forEach((tag, chip) -> {
            boolean matches = query.isEmpty() || tag.label().toLowerCase(Locale.ROOT).contains(query)
                    || tag.key().toLowerCase(Locale.ROOT).contains(query)
                    || tag.category().name().toLowerCase(Locale.ROOT).contains(query);
            chip.setVisible(matches || chip.isSelected());
        });
        refreshWrappedSectionHeights();
    }

    private void refreshWrappedSectionHeights() {
        invalidate();
        for (Component component : getComponents()) {
            if (component instanceof Container container) invalidateWrapLayouts(container);
        }
        revalidate();
        repaint();
    }

    private void invalidateWrapLayouts(Container container) {
        if (container.getLayout() instanceof WrapLayout) container.invalidate();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) invalidateWrapLayouts(nested);
        }
    }

    private void applyControlColors() {
        if (colorChips.isEmpty()) return;
        colorChips.get(CardColor.WHITE).setForeground(new Color(0xF6E7C1));
        colorChips.get(CardColor.BLUE).setForeground(new Color(0x62B5E5));
        colorChips.get(CardColor.BLACK).setForeground(new Color(0xB9A7C8));
        colorChips.get(CardColor.RED).setForeground(new Color(0xE76F51));
        colorChips.get(CardColor.GREEN).setForeground(new Color(0x72B879));
        colorlessChip.setForeground(new Color(0xC8CDD3));
        phyrexianChip.setForeground(new Color(0xB99AE8));
    }

    private Icon typeIcon(BaseCardType type) {
        String resource = switch (type) {
            case CREATURE -> "/svg/creature.svg";
            case ARTIFACT -> "/svg/artifact.svg";
            case ENCHANTMENT -> "/svg/enchantment.svg";
            case INSTANT -> "/svg/instant.svg";
            case SORCERY -> "/svg/sorcery.svg";
            case LAND -> "/svg/land.svg";
            case PLANESWALKER -> "/svg/planeswalker.svg";
            case BATTLE -> "/svg/chaos.svg";
        };
        return new SvgIcon(resource, 14);
    }

    private Icon tagIcon(SemanticTag tag) {
        String resource = switch (tag.category()) {
            case KEYWORD -> evergreenAbilityIcon(tag.key());
            case ACTION -> "/svg/tap.svg";
            case ZONE -> "/svg/land.svg";
            case CONCEPT -> "/svg/chaos.svg";
        };
        return new SvgIcon(resource, 13);
    }

    private String evergreenAbilityIcon(String key) {
        return switch (key) {
            case "deathtouch" -> "/svg/ability-deathtouch.svg";
            case "defender" -> "/svg/ability-defender.svg";
            case "double-strike", "double strike" -> "/svg/ability-doublestrike.svg";
            case "first-strike", "first strike" -> "/svg/ability-firststrike.svg";
            case "flash" -> "/svg/ability-flash.svg";
            case "flying" -> "/svg/ability-flying.svg";
            case "haste" -> "/svg/ability-haste.svg";
            case "hexproof" -> "/svg/ability-hexproof.svg";
            case "indestructible" -> "/svg/ability-indestructible.svg";
            case "lifelink" -> "/svg/ability-lifelink.svg";
            case "menace" -> "/svg/ability-menace.svg";
            case "prowess" -> "/svg/ability-prowess.svg";
            case "reach" -> "/svg/ability-reach.svg";
            case "trample" -> "/svg/ability-trample.svg";
            case "vigilance" -> "/svg/ability-vigilance.svg";
            case "ward" -> "/svg/ability-ward.svg";
            default -> "/svg/rarity.svg";
        };
    }

    private int bucket(double value) {
        return Math.max(0, Math.min(ManaValueRangeControl.MAX_BUCKET, (int) Math.round(value)));
    }

    private List<String> formatsWithCurrent(String current) {
        LinkedHashSet<String> formats = new LinkedHashSet<>();
        formats.add(current);
        formats.addAll(DEFAULT_FORMATS);
        return List.copyOf(formats);
    }

    private static String title(String value) {
        String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(FILTER_WIDTH, 640); }
    @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 28; }
    @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return Math.max(28, visibleRect.height - 28); }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
