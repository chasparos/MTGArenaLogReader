package app.ui;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.border.EmptyBorder;

/** Owns shell navigation and the single visible application-module component. */
public final class ModuleHost extends JPanel {
    private final JPanel navigation = new JPanel();
    private final JPanel content = new JPanel(new BorderLayout());
    private final Map<String, ApplicationModule> modules = new LinkedHashMap<>();
    private final Map<String, JToggleButton> navigationButtons = new LinkedHashMap<>();
    private final ButtonGroup navigationGroup = new ButtonGroup();
    private final Consumer<ApplicationModule> selectionListener;
    private ApplicationModule selected;

    public ModuleHost(List<? extends ApplicationModule> initialModules,
                      Consumer<ApplicationModule> selectionListener) {
        super(new BorderLayout());
        this.selectionListener = Objects.requireNonNull(selectionListener, "selectionListener");
        navigation.setName("module-navigation");
        navigation.setLayout(new FlowLayout(FlowLayout.LEFT, 7, 6));
        navigation.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0,
                        AppColors.color("App.border", new Color(0x555C66))),
                new EmptyBorder(1, 5, 1, 5)));
        navigation.setBackground(AppColors.color("App.surfaceRaised", new Color(0x30353C)));
        add(navigation, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        initialModules.forEach(this::addModule);
        if (!modules.isEmpty()) selectModule(modules.keySet().iterator().next());
    }

    public void addModule(ApplicationModule module) {
        Objects.requireNonNull(module, "module");
        if (module.id().isBlank()) throw new IllegalArgumentException("Module id must not be blank");
        if (modules.putIfAbsent(module.id(), module) != null) {
            throw new IllegalArgumentException("Duplicate module id: " + module.id());
        }
        JToggleButton button = new ModuleButton(module.displayName(), navigationButtons.size());
        button.setName("module-" + module.id());
        button.addActionListener(event -> selectModule(module.id()));
        navigationButtons.put(module.id(), button);
        navigationGroup.add(button);
        navigation.add(button);
        navigation.revalidate();
    }

    public void selectModule(String id) {
        ApplicationModule next = modules.get(id);
        if (next == null) throw new IllegalArgumentException("Unknown module id: " + id);
        if (next == selected) {
            navigationButtons.get(id).setSelected(true);
            return;
        }
        if (selected != null) selected.deactivated();
        content.removeAll();
        selected = next;
        content.add(next.component(), BorderLayout.CENTER);
        navigationButtons.get(id).setSelected(true);
        next.activated();
        selectionListener.accept(next);
        content.revalidate();
        content.repaint();
    }

    public String selectedModuleId() { return selected == null ? null : selected.id(); }
    public ApplicationModule selectedModule() { return selected; }
    public List<String> moduleIds() { return List.copyOf(modules.keySet()); }
    public JComponent selectedComponent() {
        Component[] components = content.getComponents();
        return components.length == 1 ? (JComponent) components[0] : null;
    }
    public boolean isNavigationSelected(String id) {
        JToggleButton button = navigationButtons.get(id);
        return button != null && button.isSelected();
    }

    JComponent navigationComponent() { return navigation; }

    private static final class ModuleButton extends JToggleButton {
        private static final Color[] TINTS = {
                new Color(0x5B83A6), new Color(0x856F9E), new Color(0x688F79),
                new Color(0xA17B62), new Color(0x8D7180), new Color(0x718B99)
        };
        private final Color tint;

        private ModuleButton(String text, int index) {
            super(text);
            tint = TINTS[Math.floorMod(index, TINTS.length)];
            setOpaque(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorder(new EmptyBorder(7, 14, 7, 14));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Color surface = AppColors.color("App.surfaceRaised", getBackground());
                float amount = isSelected() ? 0.66f : getModel().isRollover() ? 0.46f : 0.30f;
                g.setColor(AppColors.blend(surface, tint, amount));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g.setColor(AppColors.blend(
                        AppColors.color("App.border", new Color(0x555C66)), tint,
                        isSelected() ? 0.78f : 0.42f));
                g.drawRoundRect(0, 0, Math.max(0, getWidth() - 1),
                        Math.max(0, getHeight() - 1), 10, 10);
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }
    }
}
