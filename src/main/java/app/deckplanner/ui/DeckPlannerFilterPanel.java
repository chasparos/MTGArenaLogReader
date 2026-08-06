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
    private static final int FILTER_WIDTH = 410;
    private final DeckPlannerFilterModel model;
    private final JComboBox<String> formatBox;
    private final Map<CardColor, FilterChip> colorChips = new EnumMap<>(CardColor.class);
    private final FilterChip colorlessChip = new FilterChip("Colorless", new SvgIcon("/svg/artifact.svg", 14), 94);
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
    }

    @Override public void updateUI() {
        super.updateUI();
        setBackground(AppColors.color("Panel.background", new Color(0x202328)));
        setForeground(AppColors.color("Label.foreground", Color.WHITE));
        if (colorChips != null) applyControlColors();
    }

    private void buildControls(Collection<SemanticTag> tags) {
        add(section("Format", formatBox));
        JPanel colors = flow();
        for (CardColor color : CardColor.values()) {
            FilterChip chip = new FilterChip(title(color.name()), new SvgIcon("/svg/" + color.symbol().toLowerCase(Locale.ROOT) + ".svg", 14), 82);
            colorChips.put(color, chip);
            colors.add(chip);
        }
        colors.add(colorlessChip);
        add(section("Colors", colors));

        JPanel semantics = flow();
        JRadioButton printed = compactRadio("Printed colors");
        JRadioButton identity = compactRadio("Color identity");
        ButtonGroup sourceGroup = new ButtonGroup(); sourceGroup.add(printed); sourceGroup.add(identity);
        JRadioButton inclusive = compactRadio("Inclusive");
        JRadioButton exact = compactRadio("Exact");
        ButtonGroup modeGroup = new ButtonGroup(); modeGroup.add(inclusive); modeGroup.add(exact);
        semantics.add(printed); semantics.add(identity); semantics.add(inclusive); semantics.add(exact);
        semantics.putClientProperty("printed", printed); semantics.putClientProperty("identity", identity);
        semantics.putClientProperty("inclusive", inclusive); semantics.putClientProperty("exact", exact);
        add(section("Color matching", semantics));

        JPanel types = flow();
        for (BaseCardType type : BaseCardType.values()) {
            FilterChip chip = new FilterChip(title(type.name()), typeIcon(type), 116);
            typeChips.put(type, chip);
            types.add(chip);
        }
        add(section("Base types", types));

        add(section("Mana value", manaRange));

        Map<TagCategory, List<SemanticTag>> byCategory = new EnumMap<>(TagCategory.class);
        for (SemanticTag tag : new TreeSet<>(tags)) byCategory.computeIfAbsent(tag.category(), ignored -> new ArrayList<>()).add(tag);
        for (Map.Entry<TagCategory, List<SemanticTag>> entry : byCategory.entrySet()) {
            JPanel tagPanel = flow();
            for (SemanticTag tag : entry.getValue()) {
                FilterChip chip = new FilterChip(tag.label(), tagIcon(entry.getKey()), 116);
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
        typeChips.forEach((type, chip) -> chip.addActionListener(event -> { if (!syncing) model.toggleBaseType(type); }));
        tagChips.forEach((tag, chip) -> chip.addActionListener(event -> { if (!syncing) model.toggleTag(tag); }));
        JPanel semantics = (JPanel) getClientProperty("semantics");
        ((JRadioButton) semantics.getClientProperty("printed")).addActionListener(e -> { if (!syncing) model.setColorSource(ColorSource.CARD_COLORS); });
        ((JRadioButton) semantics.getClientProperty("identity")).addActionListener(e -> { if (!syncing) model.setColorSource(ColorSource.COLOR_IDENTITY); });
        ((JRadioButton) semantics.getClientProperty("inclusive")).addActionListener(e -> { if (!syncing) model.setColorMatchMode(ColorMatchMode.INCLUSIVE); });
        ((JRadioButton) semantics.getClientProperty("exact")).addActionListener(e -> { if (!syncing) model.setColorMatchMode(ColorMatchMode.EXACT); });
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
            CardFilterState filters = state.filters();
            colorChips.forEach((color, chip) -> chip.setSelected(filters.colors().contains(color)));
            colorlessChip.setSelected(filters.includeColorless());
            typeChips.forEach((type, chip) -> chip.setSelected(filters.baseTypes().contains(type)));
            tagChips.forEach((tag, chip) -> chip.setSelected(filters.selectedTags().contains(tag)));
            JPanel semantics = (JPanel) getClientProperty("semantics");
            ((JRadioButton) semantics.getClientProperty(filters.colorSource() == ColorSource.CARD_COLORS ? "printed" : "identity")).setSelected(true);
            ((JRadioButton) semantics.getClientProperty(filters.colorMatchMode() == ColorMatchMode.INCLUSIVE ? "inclusive" : "exact")).setSelected(true);
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

    private JRadioButton compactRadio(String label) {
        JRadioButton button = new JRadioButton(label);
        button.setOpaque(false);
        button.setMargin(new Insets(1, 2, 1, 4));
        button.setFont(button.getFont().deriveFont(11f));
        return button;
    }

    private void applyControlColors() {
        if (colorChips.isEmpty()) return;
        colorChips.get(CardColor.WHITE).setForeground(new Color(0xF6E7C1));
        colorChips.get(CardColor.BLUE).setForeground(new Color(0x62B5E5));
        colorChips.get(CardColor.BLACK).setForeground(new Color(0xB9A7C8));
        colorChips.get(CardColor.RED).setForeground(new Color(0xE76F51));
        colorChips.get(CardColor.GREEN).setForeground(new Color(0x72B879));
        colorlessChip.setForeground(new Color(0xC8CDD3));
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

    private Icon tagIcon(TagCategory category) {
        return new SvgIcon(switch (category) {
            case KEYWORD -> "/svg/rarity.svg";
            case ACTION -> "/svg/tap.svg";
            case ZONE -> "/svg/land.svg";
            case CONCEPT -> "/svg/chaos.svg";
        }, 13);
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
