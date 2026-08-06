package app.deckplanner.ui;

import app.deckplanner.filter.*;
import app.ui.AppColors;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.*;

/** Click-first filter controls bound to a widget-independent {@link DeckPlannerFilterModel}. */
public final class DeckPlannerFilterPanel extends JPanel {
    private static final List<String> DEFAULT_FORMATS = List.of("standard", "alchemy", "historic", "timeless", "explorer", "brawl");
    private final DeckPlannerFilterModel model;
    private final JComboBox<String> formatBox;
    private final Map<CardColor, FilterChip> colorChips = new EnumMap<>(CardColor.class);
    private final FilterChip colorlessChip = new FilterChip("Colorless");
    private final Map<BaseCardType, FilterChip> typeChips = new EnumMap<>(BaseCardType.class);
    private final Map<SemanticTag, FilterChip> tagChips = new LinkedHashMap<>();
    private final JSpinner manaMinimum = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 30.0, 0.5));
    private final JSpinner manaMaximum = new JSpinner(new SpinnerNumberModel(30.0, 0.0, 30.0, 0.5));
    private boolean syncing;

    public DeckPlannerFilterPanel(DeckPlannerFilterModel model, Collection<SemanticTag> availableTags) {
        this.model = Objects.requireNonNull(model);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(12, 12, 12, 12));
        setOpaque(true);
        formatBox = new JComboBox<>(formatsWithCurrent(model.state().format()).toArray(String[]::new));
        formatBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        buildControls(availableTags == null ? List.of() : availableTags);
        bindActions();
        model.addListener(state -> SwingUtilities.invokeLater(() -> syncFromModel(state)));
        syncFromModel(model.state());
    }

    @Override public void updateUI() {
        super.updateUI();
        setBackground(AppColors.color("Panel.background", new Color(0x202328)));
        setForeground(AppColors.color("Label.foreground", Color.WHITE));
    }

    private void buildControls(Collection<SemanticTag> tags) {
        add(section("Format", formatBox));
        JPanel colors = flow();
        for (CardColor color : CardColor.values()) {
            FilterChip chip = new FilterChip(color.name().substring(0, 1) + color.name().substring(1).toLowerCase(Locale.ROOT));
            colorChips.put(color, chip);
            colors.add(chip);
        }
        colors.add(colorlessChip);
        add(section("Colors", colors));

        JPanel semantics = flow();
        JRadioButton printed = new JRadioButton("Card colors");
        JRadioButton identity = new JRadioButton("Color identity");
        ButtonGroup sourceGroup = new ButtonGroup(); sourceGroup.add(printed); sourceGroup.add(identity);
        JRadioButton inclusive = new JRadioButton("Inclusive");
        JRadioButton exact = new JRadioButton("Exact");
        ButtonGroup modeGroup = new ButtonGroup(); modeGroup.add(inclusive); modeGroup.add(exact);
        printed.setActionCommand("source:card"); identity.setActionCommand("source:identity");
        inclusive.setActionCommand("mode:inclusive"); exact.setActionCommand("mode:exact");
        semantics.add(printed); semantics.add(identity); semantics.add(inclusive); semantics.add(exact);
        semantics.putClientProperty("printed", printed); semantics.putClientProperty("identity", identity);
        semantics.putClientProperty("inclusive", inclusive); semantics.putClientProperty("exact", exact);
        add(section("Color matching", semantics));

        JPanel types = flow();
        for (BaseCardType type : BaseCardType.values()) {
            FilterChip chip = new FilterChip(title(type.name()));
            typeChips.put(type, chip); types.add(chip);
        }
        add(section("Base types", types));

        JPanel mana = flow();
        mana.add(new JLabel("Minimum")); mana.add(manaMinimum);
        mana.add(new JLabel("Maximum")); mana.add(manaMaximum);
        JButton allMana = new JButton("Any mana value");
        allMana.setActionCommand("mana:any"); mana.add(allMana);
        mana.putClientProperty("allMana", allMana);
        add(section("Mana value", mana));

        Map<TagCategory, List<SemanticTag>> byCategory = new EnumMap<>(TagCategory.class);
        for (SemanticTag tag : new TreeSet<>(tags)) byCategory.computeIfAbsent(tag.category(), ignored -> new ArrayList<>()).add(tag);
        for (Map.Entry<TagCategory, List<SemanticTag>> entry : byCategory.entrySet()) {
            JPanel tagPanel = flow();
            for (SemanticTag tag : entry.getValue()) {
                FilterChip chip = new FilterChip(tag.label()); tagChips.put(tag, chip); tagPanel.add(chip);
            }
            add(section(title(entry.getKey().name()), tagPanel));
        }
        JButton reset = new JButton("Reset filters");
        reset.setAlignmentX(Component.LEFT_ALIGNMENT);
        reset.setActionCommand("reset");
        add(Box.createVerticalStrut(8)); add(reset);
        putClientProperty("semantics", semantics); putClientProperty("mana", mana); putClientProperty("reset", reset);
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
        manaMinimum.addChangeListener(e -> applyMana()); manaMaximum.addChangeListener(e -> applyMana());
        JPanel mana = (JPanel) getClientProperty("mana");
        ((JButton) mana.getClientProperty("allMana")).addActionListener(e -> { if (!syncing) model.setManaValueRange(null); });
        ((JButton) getClientProperty("reset")).addActionListener(e -> model.resetFilters());
    }

    private void applyMana() {
        if (syncing) return;
        double min = ((Number) manaMinimum.getValue()).doubleValue();
        double max = ((Number) manaMaximum.getValue()).doubleValue();
        if (min <= max) model.setManaValueRange(new ManaValueRange(min, max));
    }

    private void syncFromModel(DeckPlannerFilterModel.State state) {
        syncing = true;
        try {
            formatBox.setSelectedItem(state.format());
            CardFilterState f = state.filters();
            colorChips.forEach((color, chip) -> chip.setSelected(f.colors().contains(color)));
            colorlessChip.setSelected(f.includeColorless());
            typeChips.forEach((type, chip) -> chip.setSelected(f.baseTypes().contains(type)));
            tagChips.forEach((tag, chip) -> chip.setSelected(f.selectedTags().contains(tag)));
            JPanel semantics = (JPanel) getClientProperty("semantics");
            ((JRadioButton) semantics.getClientProperty(f.colorSource() == ColorSource.CARD_COLORS ? "printed" : "identity")).setSelected(true);
            ((JRadioButton) semantics.getClientProperty(f.colorMatchMode() == ColorMatchMode.INCLUSIVE ? "inclusive" : "exact")).setSelected(true);
            if (f.manaValueRange() == null) { manaMinimum.setValue(0.0); manaMaximum.setValue(30.0); }
            else { manaMinimum.setValue(f.manaValueRange().minimum()); manaMaximum.setValue(f.manaValueRange().maximum()); }
        } finally { syncing = false; }
    }

    private JPanel section(String title, Component content) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false); panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel label = new JLabel(title); label.setFont(label.getFont().deriveFont(Font.BOLD));
        panel.add(label, BorderLayout.NORTH); panel.add(content, BorderLayout.CENTER);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, content.getPreferredSize().height + 30));
        panel.setBorder(new EmptyBorder(0, 0, 10, 0));
        return panel;
    }

    private JPanel flow() { JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4)); p.setOpaque(false); return p; }
    private List<String> formatsWithCurrent(String current) {
        LinkedHashSet<String> formats = new LinkedHashSet<>(); formats.add(current); formats.addAll(DEFAULT_FORMATS); return List.copyOf(formats);
    }
    private static String title(String value) {
        String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
