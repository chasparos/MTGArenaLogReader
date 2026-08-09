package app.collection.ui;

import app.collection.CollectionUpdate;
import app.model.log.RawLogEntry;
import app.ui.ApplicationModule;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Lazy production composition for the application-owned collection synchronization experience. */
public final class CollectionModule extends JPanel implements ApplicationModule, AutoCloseable {
    private final JButton synchronize = new JButton("Synchronize with Arena");
    private final JLabel state = new JLabel("Preparing Arena card information…");
    private final CollectionNavigationObserver navigation;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Prepared prepared;
    private volatile CollectionSyncPanel activeWizard;

    public CollectionModule(Supplier<Prepared> preparation, Executor worker) {
        super(new BorderLayout(12, 12));
        this.navigation = new CollectionNavigationObserver(step -> {
            CollectionSyncPanel wizard = activeWizard;
            if (wizard != null) wizard.navigationChanged(step);
        });
        setBorder(new EmptyBorder(28, 32, 28, 32));
        JLabel heading = new JLabel("Arena Collection");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 24f));
        add(heading, BorderLayout.NORTH);
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        state.setAlignmentX(Component.LEFT_ALIGNMENT);
        synchronize.setAlignmentX(Component.LEFT_ALIGNMENT);
        synchronize.setEnabled(false);
        synchronize.addActionListener(event -> openWizard());
        center.add(state);
        center.add(Box.createVerticalStrut(14));
        center.add(synchronize);
        add(center, BorderLayout.CENTER);

        CompletableFuture.supplyAsync(preparation, worker)
                .whenComplete((prepared, error) -> SwingUtilities.invokeLater(() -> ready(prepared, error)));
    }

    private void ready(Prepared prepared, Throwable error) {
        if (error != null) {
            state.setText("Collection synchronization is unavailable: " + rootMessage(error));
            return;
        }
        if (closed.get()) {
            prepared.close();
            return;
        }
        this.prepared = prepared;
        state.setText("Ready with " + prepared.options() + " Arena card printings");
        synchronize.setEnabled(true);
    }

    private void openWizard() {
        Prepared current = prepared;
        if (current == null) return;
        CollectionSyncPanel panel = new CollectionSyncPanel(
                current.update(), current.artwork(), current.presentation());
        CollectionNavigationObserver.Step latest = navigation.latest();
        if (latest != null) panel.navigationChanged(latest);
        activeWizard = panel;
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, "Synchronize Arena Collection",
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setContentPane(panel);
        dialog.setSize(900, 700);
        dialog.setLocationRelativeTo(owner);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent event) {
                if (activeWizard == panel) activeWizard = null;
            }
        });
        dialog.setVisible(true);
    }

    public void acceptRawLog(RawLogEntry entry) { navigation.accept(entry); }
    @Override public String id() { return "collection"; }
    @Override public String displayName() { return "Collection"; }
    @Override public JComponent component() { return this; }
    @Override public String shellStatus() { return prepared == null ? "Preparing Collection" : "Collection ready"; }

    @Override public void close() {
        closed.set(true);
        Prepared current = prepared;
        if (current != null) current.close();
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    public record Prepared(CollectionUpdate update, CollectionSyncPanel.CardArtworkSource artwork,
                           CollectionSyncPanel.CardPresentationSource presentation,
                           int options, AutoCloseable lifecycle) implements AutoCloseable {
        @Override public void close() {
            try { lifecycle.close(); } catch (Exception ignored) { }
        }
    }
}
