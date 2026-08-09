package app.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** The production application shell. Domain services remain owned by Application. */
public final class MainFrame extends JFrame {
    private static final String APPLICATION_TITLE = "MTG Arena Log Reader";
    private final JLabel status = new JLabel("Ready");
    private final ModuleHost moduleHost;

    public MainFrame(List<? extends ApplicationModule> modules,
                     Consumer<Window> settingsAction,
                     Runnable closeAction) {
        super(APPLICATION_TITLE);
        Objects.requireNonNull(settingsAction, "settingsAction");
        Objects.requireNonNull(closeAction, "closeAction");
        moduleHost = new ModuleHost(modules, this::moduleSelected);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(1100, 760);
        setLocationByPlatform(true);
        add(moduleHost, BorderLayout.CENTER);

        JButton settings = new JButton("Settings");
        settings.addActionListener(event -> settingsAction.accept(this));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.add(settings);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(new EmptyBorder(5, 8, 8, 8));
        footer.add(status, BorderLayout.WEST);
        footer.add(actions, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) { closeAction.run(); }
        });
    }

    public ModuleHost moduleHost() { return moduleHost; }

    private void moduleSelected(ApplicationModule module) {
        setTitle(APPLICATION_TITLE + " — " + module.shellTitle());
        status.setText(module.shellStatus());
    }
}
