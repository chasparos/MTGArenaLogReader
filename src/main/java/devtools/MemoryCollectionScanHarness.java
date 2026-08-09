package devtools;

import app.collection.memory.MemoryCollectionService;
import app.collection.CollectionUpdate;
import app.collection.memory.extraction.ScanEvidenceConfigLoader;
import app.collection.memory.extraction.ArenaKnownIdCatalogProducer;
import app.collection.memory.extraction.StructuralEvidenceSnapshotStore;
import app.settings.ThemeService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** MSC-01 click-test harness; intentionally not part of production navigation. */
public final class MemoryCollectionScanHarness {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final JFrame frame = new JFrame("Memory Collection Scan Harness — Region Inventory");
    private final JButton attempt = new JButton("Attempt scan");
    private final JTextArea messages = area();
    private final JTextArea output = area();
    private final JTextField knownIdsFile = new JTextField(42);
    private final JTextArea anchors = new JTextArea("# arenaId=copies (at least two)\n", 3, 24);
    private final MemoryCollectionService ownership;
    private final AtomicReference<ScanEvidenceConfigLoader.Config> currentEvidence = new AtomicReference<>();
    private final JLabel busyLabel = new JLabel("Working…", SwingConstants.CENTER);
    private final JProgressBar busySpinner = new JProgressBar();
    private final JPanel busyGlass = createBusyGlass();
    private String evidenceHeader = "";

    private MemoryCollectionScanHarness() {
        Path database = Path.of("target", "memory-collection-harness", "ownership");
        ownership = MemoryCollectionService.windowsAnchorDiscoveryHarness(
                database, currentEvidence::get, this::publishProgress, this::publishOutput,
                result -> recordStructuralEvidence(result.structuralEvidence()));
        attempt.addActionListener(event -> attemptScan());

        knownIdsFile.setText(Path.of("target", "memory-collection-harness",
                "known-arena-ids.json").toString());
        JButton browse = new JButton("Choose known-ID file…");
        browse.addActionListener(event -> chooseKnownIdsFile());
        JButton buildCatalog = new JButton("Build from Arena install");
        buildCatalog.addActionListener(event -> buildKnownIdCatalog(buildCatalog));

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBorder(new EmptyBorder(7, 8, 7, 8));
        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actionRow.add(buildCatalog);
        actionRow.add(attempt);
        actionRow.add(new JLabel("MSC-03 validates evidence then inventories; collection bytes are not searched yet"));
        JPanel fileRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        fileRow.add(new JLabel("Known IDs:"));
        fileRow.add(knownIdsFile);
        fileRow.add(browse);
        JPanel schemaRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        schemaRow.add(new JLabel("Known-ID JSON: {\"version\":\"client/catalog identity\", \"arenaIds\":[12345,...]}"));
        JPanel anchorRow = new JPanel(new BorderLayout(6, 2));
        anchorRow.add(new JLabel("Confirmed anchors:"), BorderLayout.WEST);
        anchorRow.add(new JScrollPane(anchors), BorderLayout.CENTER);
        controls.add(actionRow);
        controls.add(fileRow);
        controls.add(schemaRow);
        controls.add(anchorRow);

        JScrollPane messageScroll = new JScrollPane(messages);
        messageScroll.setBorder(BorderFactory.createTitledBorder("Progress messages"));
        JScrollPane outputScroll = new JScrollPane(output);
        outputScroll.setBorder(BorderFactory.createTitledBorder("Scan output"));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, messageScroll, outputScroll);
        split.setResizeWeight(0.45);
        split.setDividerLocation(280);

        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setSize(900, 700);
        frame.setLocationByPlatform(true);
        frame.setGlassPane(busyGlass);
        frame.add(controls, BorderLayout.NORTH);
        frame.add(split, BorderLayout.CENTER);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent event) { ownership.close(); }
        });
    }

    private void recordStructuralEvidence(java.util.Map<Long, Integer> evidence) {
        try {
            var result = new StructuralEvidenceSnapshotStore(
                    Path.of("target", "memory-collection-harness")).record(evidence);
            if (result.outcome() == StructuralEvidenceSnapshotStore.Outcome.BASELINE_CREATED) {
                publishProgress("Structural evidence baseline saved: " + result.entries()
                        + " entries -> " + result.path());
            } else if (result.outcome() == StructuralEvidenceSnapshotStore.Outcome.COMPARISON_CREATED) {
                publishProgress("Structural evidence comparison saved: " + result.changes().size()
                        + " changed IDs -> " + result.path());
                for (var change : result.changes()) publishProgress("Evidence change: "
                        + change.arenaId() + " " + change.before() + " -> " + change.after());
            }
        } catch (Exception error) {
            publishProgress("Structural evidence snapshot failed: " + error.getMessage());
        }
    }

    public static void main(String[] args) {
        new ThemeService().applySaved();
        SwingUtilities.invokeLater(() -> new MemoryCollectionScanHarness().frame.setVisible(true));
    }

    private void attemptScan() {
        Path configuredPath;
        try {
            configuredPath = Path.of(knownIdsFile.getText().trim()).toAbsolutePath().normalize();
        } catch (Exception error) {
            publishProgress("Evidence preflight stopped: known-ID path is invalid");
            output.setText("EVIDENCE PREFLIGHT STOPPED\nknownIdPath=" + knownIdsFile.getText()
                    + "\nreason=" + error.getMessage() + "\n");
            return;
        }
        publishProgress("Evidence preflight started");
        publishProgress("Known-ID file: " + configuredPath);
        if (java.nio.file.Files.isRegularFile(configuredPath)) {
            try {
                publishProgress("Known-ID file found: " + java.nio.file.Files.size(configuredPath)
                        + " bytes");
            } catch (Exception ignored) {
                publishProgress("Known-ID file found; size unavailable");
            }
        } else {
            publishProgress("Known-ID file not found; no process access attempted");
        }
        ScanEvidenceConfigLoader.Config config;
        try {
            config = new ScanEvidenceConfigLoader().load(
                    configuredPath, anchors.getText());
        } catch (Exception error) {
            publishProgress("Evidence preflight stopped: " + error.getMessage());
            output.setText("EVIDENCE PREFLIGHT STOPPED\nknownIdPath=" + configuredPath
                    + "\nprocessAccessAttempted=false\nreason=" + error.getMessage() + "\n");
            return;
        }
        evidenceHeader = "EVIDENCE CONFIGURATION\nversion=" + config.version()
                + "\nknownArenaIds=" + config.knownArenaIds().size()
                + "\nanchors=" + config.anchors().size() + "\n\n";
        currentEvidence.set(config);
        publishProgress("Evidence configuration accepted: " + config.knownArenaIds().size()
                + " known IDs, " + config.anchors().size() + " anchors");
        attempt.setEnabled(false);
        setBusy(true, "Scanning MTGA memory…");
        output.setText("");
        CollectionUpdate.Session session = ownership.begin(event -> {
            if (event instanceof CollectionUpdate.Status status) publishProgress(status.message());
            if (event instanceof CollectionUpdate.Completed completed) SwingUtilities.invokeLater(() -> {
                    attempt.setEnabled(true);
                    setBusy(false, "");
                    publishProgress(completed.updated()
                            ? "Attempt finished successfully" : "Attempt finished without publication");
                });
        });
        session.respond(new CollectionUpdate.Continue());
    }

    private void publishProgress(String message) {
        SwingUtilities.invokeLater(() -> {
            messages.append("[" + TIME.format(LocalTime.now()) + "] " + message + System.lineSeparator());
            messages.setCaretPosition(messages.getDocument().getLength());
        });
    }

    private void publishOutput(String text) {
        SwingUtilities.invokeLater(() -> {
            output.setText(evidenceHeader + (text == null ? "" : text));
            output.setCaretPosition(0);
        });
    }

    private void chooseKnownIdsFile() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            knownIdsFile.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void buildKnownIdCatalog(JButton button) {
        button.setEnabled(false);
        Path arenaRoot = Path.of("C:\\Program Files\\Wizards of the Coast\\MTGA");
        Path requestedOutput;
        try {
            requestedOutput = Path.of(knownIdsFile.getText().trim()).toAbsolutePath().normalize();
        } catch (Exception error) {
            publishProgress("Known-ID catalog build stopped: invalid output path");
            button.setEnabled(true);
            return;
        }
        publishProgress("Known-ID catalog build started");
        publishProgress("Arena install: " + arenaRoot);
        publishProgress("Catalog output: " + requestedOutput);
        setBusy(true, "Building known-ID catalog…");
        CompletableFuture.runAsync(() -> {
            try {
                var result = new ArenaKnownIdCatalogProducer().produce(
                        arenaRoot, requestedOutput);
                publishProgress("Arena CardDatabase acquired: " + result.source());
                publishProgress("Known-ID catalog built from Arena: " + result.knownIds()
                        + " IDs -> " + result.output());
                SwingUtilities.invokeLater(() -> knownIdsFile.setText(result.output().toString()));
            } catch (Exception error) {
                publishProgress("Known-ID catalog build failed: " + error.getMessage());
            } finally {
                SwingUtilities.invokeLater(() -> {
                    button.setEnabled(true);
                    setBusy(false, "");
                });
            }
        });
    }

    private JPanel createBusyGlass() {
        JPanel glass = new JPanel(new GridBagLayout()) {
            @Override protected void paintComponent(Graphics graphics) {
                graphics.setColor(new Color(0, 0, 0, 145));
                graphics.fillRect(0, 0, getWidth(), getHeight());
                super.paintComponent(graphics);
            }
        };
        glass.setOpaque(false);
        glass.addMouseListener(new java.awt.event.MouseAdapter() { });
        glass.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() { });
        glass.setFocusTraversalKeysEnabled(false);
        JPanel card = new JPanel();
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground")),
                new EmptyBorder(18, 24, 18, 24)));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        busyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        busySpinner.setIndeterminate(true);
        busySpinner.setAlignmentX(Component.CENTER_ALIGNMENT);
        busySpinner.setPreferredSize(new Dimension(260, 18));
        card.add(busyLabel);
        card.add(Box.createVerticalStrut(12));
        card.add(busySpinner);
        glass.add(card);
        return glass;
    }

    private void setBusy(boolean busy, String message) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setBusy(busy, message));
            return;
        }
        busyLabel.setText(message);
        busyGlass.setVisible(busy);
        if (busy) busyGlass.requestFocusInWindow();
    }

    private static JTextArea area() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        return area;
    }
}
