package devtools;

import devtools.ui.PaintedControl;
import devtools.ui.SteadyArcScrollBarUI;
import devtools.ui.SteadyArcTheme;
import devtools.ui.SvgIconPainter;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.geom.RoundRectangle2D;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Drag helper for attaching the latest snapshot, test log, and manifest to ChatGPT. */
public final class ChatGptPayloadButton {
    private static final String SNAPSHOT_PREFIX = "latest snapshot";
    private static final String TEST_RESULTS_PREFIX = "latest test results";
    private static final String SNAPSHOT_MANIFEST_PREFIX = "latest snapshot manifest";
    private static final Color BACKGROUND = SteadyArcTheme.SURFACE;
    private static final Color FOREGROUND = SteadyArcTheme.TEXT;

    private ChatGptPayloadButton() {
    }

    public static void main(String[] args) {
        Path repositoryRoot = resolveRepositoryRoot(Path.of(""));
        SwingUtilities.invokeLater(() -> showWidget(repositoryRoot));
    }

    private static void showWidget(Path repositoryRoot) {
        JFrame frame = new JFrame("ChatGPT payload: " + repositoryLabel(repositoryRoot));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setAlwaysOnTop(true);
        frame.setUndecorated(true);

        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setPreferredSize(new Dimension(190, 132));
        panel.setBackground(BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(6, 8, 7, 8));

        JLabel label = new JLabel(repositoryLabel(repositoryRoot), SwingConstants.CENTER);
        label.setForeground(FOREGROUND);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        label.setToolTipText(repositoryRoot.toString());
        panel.add(label, BorderLayout.NORTH);

        FileDragSource dragSource = new FileDragSource(repositoryRoot);
        panel.add(dragSource, BorderLayout.CENTER);

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        tools.setOpaque(false);
        AtomicReference<SupportRelay> relay = new AtomicReference<>();
        tools.add(toolButton(
                "/devtools/ui/chevron-terminal.svg",
                "Run PowerShell command from clipboard",
                () -> runClipboardCommand(frame, repositoryRoot)));
        tools.add(toolButton(
                "/devtools/ui/chevron-forward.svg",
                "Open PowerShell terminal in repository",
                () -> openTerminal(frame, repositoryRoot)));
        tools.add(toolButton(
                "/devtools/ui/chevron-relay.svg",
                "Start or stop the Codex support relay",
                () -> toggleSupportRelay(frame, repositoryRoot, relay)));
        panel.add(tools, BorderLayout.SOUTH);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                SupportRelay active = relay.getAndSet(null);
                if (active != null) {
                    active.close();
                }
            }
        });

        PanelMoveSupport moveSupport = new PanelMoveSupport(frame);
        panel.addMouseListener(moveSupport);
        panel.addMouseMotionListener(moveSupport);
        label.addMouseListener(moveSupport);
        label.addMouseMotionListener(moveSupport);

        frame.setContentPane(panel);
        frame.pack();
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    private static PaintedControl toolButton(
            String iconResource,
            String tooltip,
            Runnable action) {
        SvgIconPainter icon = new SvgIconPainter(iconResource);
        PaintedControl.Painter background = PaintedControl.flatPainter(
                SteadyArcTheme.SURFACE_RECESSED,
                SteadyArcTheme.SURFACE_RAISED,
                SteadyArcTheme.GOLD);
        return new PaintedControl(
                new Dimension(34, 24),
                tooltip,
                tooltip,
                (width, height) -> new RoundRectangle2D.Float(
                        0.5f, 0.5f, width - 1f, height - 1f, 8f, 8f),
                (graphics, shape, state) -> {
                    background.paint(graphics, shape, state);
                    icon.paint(null, graphics, 8, 4, 18, 16);
                },
                action);
    }

    private static void runClipboardCommand(JFrame owner, Path repositoryRoot) {
        String command;
        try {
            Object content = java.awt.Toolkit.getDefaultToolkit()
                    .getSystemClipboard().getData(DataFlavor.stringFlavor);
            command = content instanceof String value ? value.trim() : "";
        } catch (UnsupportedFlavorException | IOException | IllegalStateException exception) {
            showMessage(owner, "Clipboard unavailable", exception.getMessage(),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (command.isEmpty()) {
            showMessage(owner, "Clipboard command", "The clipboard contains no text command.",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        if (!isPatchSequenceCommand(command)) {
            try {
                launchPowerShellTerminal(repositoryRoot, command);
            } catch (IOException exception) {
                showMessage(owner, "Unable to open terminal", exception.getMessage(),
                        JOptionPane.ERROR_MESSAGE);
            }
            return;
        }

        RunFeedbackPopup feedback = new RunFeedbackPopup(owner, repositoryRoot);
        feedback.showRunning();
        CompletableFuture
                .supplyAsync(() -> executeCapturedPowerShell(
                        repositoryRoot,
                        command,
                        feedback::appendOutputLine))
                .thenAccept(result -> SwingUtilities.invokeLater(() -> {
                    feedback.completeAndFade(result.processExit());
                }));
    }

    private static void openTerminal(JFrame owner, Path repositoryRoot) {
        try {
            launchPowerShellTerminal(repositoryRoot, null);
        } catch (IOException exception) {
            showMessage(owner, "Unable to open terminal", exception.getMessage(),
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void toggleSupportRelay(
            JFrame owner,
            Path repositoryRoot,
            AtomicReference<SupportRelay> relayReference) {
        SupportRelay active = relayReference.getAndSet(null);
        if (active != null) {
            active.close();
            showToast(owner, "Relay stopped");
            return;
        }

        try {
            SupportRelay relay = new SupportRelay(
                    repositoryRoot,
                    message -> System.out.println("[support relay] " + message));
            relay.start();
            relayReference.set(relay);
            String channelInformation = relayClipboardText(repositoryRoot, relay);
            boolean copied = copyTextToClipboard(channelInformation);
            showToast(owner, copied
                    ? "Relay started — channel info copied"
                    : "Relay started — clipboard unavailable");
        } catch (IOException exception) {
            showMessage(owner, "Unable to start support relay", exception.getMessage(),
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public static String relayClipboardText(Path repositoryRoot, SupportRelay relay) {
        String operations = java.util.Arrays.stream(SupportRelay.Operation.values())
                .map(SupportRelay.Operation::wireName)
                .collect(java.util.stream.Collectors.joining(", "));
        return String.join(
                System.lineSeparator(),
                "Steady Arc support relay is active.",
                "Repository: " + repositoryRoot.toAbsolutePath().normalize(),
                "Channel: " + relay.relayRoot(),
                "Idle timeout: " + relay.sessionLifetime().toMinutes()
                        + " minutes; each accepted operation resets it.",
                "Current expiry: " + relay.expiresAt(),
                "Operations: " + operations,
                "Client: powershell.exe -NoProfile -ExecutionPolicy Bypass"
                        + " -File .\\InvokeSteadyArcRelay.ps1 <operation>");
    }

    private static boolean copyTextToClipboard(String text) {
        try {
            java.awt.Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .setContents(new StringSelection(text), null);
            return true;
        } catch (IllegalStateException | SecurityException exception) {
            return false;
        }
    }

    private static void showToast(JFrame owner, String message) {
        final int lingerMilliseconds = 2_500;
        final int fadeIntervalMilliseconds = 50;
        final float fadeStep = 0.06f;

        JWindow toast = new JWindow(owner);
        toast.setAlwaysOnTop(true);
        JLabel label = new JLabel(message);
        label.setOpaque(true);
        label.setBackground(BACKGROUND);
        label.setForeground(FOREGROUND);
        label.setBorder(BorderFactory.createEmptyBorder(9, 13, 9, 13));
        toast.setContentPane(label);
        toast.pack();
        toast.setLocationRelativeTo(owner);

        boolean opacitySupported;
        try {
            toast.setOpacity(0.90f);
            opacitySupported = true;
        } catch (UnsupportedOperationException | IllegalArgumentException exception) {
            opacitySupported = false;
        }
        toast.setVisible(true);

        boolean canFade = opacitySupported;
        javax.swing.Timer linger = new javax.swing.Timer(lingerMilliseconds, event -> {
            if (!canFade) {
                toast.dispose();
                return;
            }
            javax.swing.Timer fade = new javax.swing.Timer(
                    fadeIntervalMilliseconds,
                    null);
            fade.addActionListener(fadeEvent -> {
                float nextOpacity = Math.max(0f, toast.getOpacity() - fadeStep);
                if (nextOpacity <= 0f) {
                    fade.stop();
                    toast.dispose();
                } else {
                    toast.setOpacity(nextOpacity);
                }
            });
            fade.start();
        });
        linger.setRepeats(false);
        linger.start();
    }

    public static Path resolveRepositoryRoot(Path start) {
        Path normalized = start.toAbsolutePath().normalize();
        try {
            Process process = new ProcessBuilder(
                    "git", "-C", normalized.toString(), "rev-parse", "--show-toplevel")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8).trim();
            if (process.waitFor() == 0 && !output.isEmpty()) {
                return Path.of(output).toAbsolutePath().normalize();
            }
        } catch (IOException exception) {
            // Fall back to the supplied directory when Git is unavailable.
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        return normalized;
    }

    public static String repositoryLabel(Path repositoryRoot) {
        Path fileName = repositoryRoot.getFileName();
        return fileName == null ? repositoryRoot.toString() : fileName.toString();
    }

    public static boolean isPatchSequenceCommand(String command) {
        return command.toLowerCase(Locale.ROOT).contains("patchsequence.ps1");
    }

    private static CommandResult executeCapturedPowerShell(
            Path repositoryRoot,
            String command,
            Consumer<String> outputLineConsumer) {
        List<String> invocation = capturedPowerShellInvocation(command);
        ProcessBuilder builder = new ProcessBuilder(invocation)
                .directory(repositoryRoot.toFile())
                .redirectErrorStream(true);
        try {
            Process process = builder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                    outputLineConsumer.accept(line);
                }
            }
            int processExit = process.waitFor();
            return new CommandResult(processExit, output.toString());
        } catch (IOException exception) {
            outputLineConsumer.accept(exception.toString());
            return new CommandResult(-1, exception.toString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            outputLineConsumer.accept("Command interrupted.");
            return new CommandResult(-1, "Command interrupted.");
        }
    }

    public static List<String> capturedPowerShellInvocation(String command) {
        /*
         * Passing an arbitrary quoted command as the argument after -Command is
         * not stable through Java's Windows process quoting. Encode the exact
         * UTF-16LE script instead, just as the visible-terminal path does, so a
         * quoted PatchSequence commit message remains one PowerShell argument.
         */
        String encodedCommand = Base64.getEncoder().encodeToString(
                command.getBytes(StandardCharsets.UTF_16LE));
        return List.of(
                "powershell.exe",
                "-NoLogo",
                "-NoProfile",
                "-ExecutionPolicy", "Bypass",
                "-EncodedCommand", encodedCommand);
    }

    private static void launchPowerShellTerminal(Path repositoryRoot, String command)
            throws IOException {
        new ProcessBuilder(terminalInvocation(repositoryRoot, command))
                .directory(repositoryRoot.toFile())
                .start();
    }

    public static List<String> terminalInvocation(Path repositoryRoot, String command) {
        String encodedCommand = Base64.getEncoder().encodeToString(
                terminalPowerShellScript(repositoryRoot, command)
                        .getBytes(StandardCharsets.UTF_16LE));
        return List.of(
                "cmd.exe",
                "/d",
                "/s",
                "/c",
                "start",
                "",
                "powershell.exe",
                "-NoLogo",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-NoExit",
                "-EncodedCommand",
                encodedCommand);
    }

    public static String terminalPowerShellScript(Path repositoryRoot, String command) {
        String escapedRoot = repositoryRoot.toAbsolutePath().normalize().toString()
                .replace("'", "''");
        StringBuilder script = new StringBuilder(
                "Set-Location -LiteralPath '").append(escapedRoot).append("'");
        if (command != null && !command.isBlank()) {
            script.append(System.lineSeparator()).append(command);
        }
        return script.toString();
    }

    public static String outputTail(String output, int maximumLines) {
        if (output == null || output.isBlank() || maximumLines <= 0) {
            return "";
        }
        String[] lines = output.strip().split("\\R");
        int first = Math.max(0, lines.length - maximumLines);
        return String.join(System.lineSeparator(),
                java.util.Arrays.copyOfRange(lines, first, lines.length));
    }


    public static String appendOutputTail(
            String currentOutput,
            String nextLine,
            int maximumLines) {
        if (maximumLines <= 0) {
            return "";
        }
        String current = currentOutput == null ? "" : currentOutput;
        String line = nextLine == null ? "" : nextLine;
        String combined = current.isBlank()
                ? line
                : current + System.lineSeparator() + line;
        return outputTail(combined, maximumLines);
    }

    private static void showMessage(JFrame owner, String title, String message, int type) {
        JOptionPane.showMessageDialog(owner, message, title, type);
    }

    private static final class RunFeedbackPopup {
        private static final int MAXIMUM_LINES = 1_000;
        private static final int COMPLETION_LINGER_MILLISECONDS = 5_000;
        private static final int FADE_INTERVAL_MILLISECONDS = 50;
        private static final float FADE_STEP = 0.05f;
        private final JWindow window;
        private final JLabel status;
        private final JTextArea outputArea;
        private boolean opacitySupported;
        private boolean completed;
        private boolean pinnedByClick;
        private javax.swing.Timer lingerTimer;
        private javax.swing.Timer fadeTimer;
        private Point dragStartOnScreen;
        private Point dragWindowOrigin;

        private RunFeedbackPopup(JFrame owner, Path repositoryRoot) {
            window = new JWindow(owner);
            window.setAlwaysOnTop(true);

            JPanel content = new JPanel(new BorderLayout(4, 4));
            content.setBackground(BACKGROUND);
            content.setBorder(BorderFactory.createEmptyBorder(7, 9, 7, 9));

            status = new JLabel("Running clipboard command…");
            status.setForeground(FOREGROUND);
            status.setFont(status.getFont().deriveFont(Font.BOLD, 11f));
            status.setToolTipText(repositoryRoot.toString());
            status.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            content.add(status, BorderLayout.NORTH);

            outputArea = new JTextArea(15, 92);
            outputArea.setEditable(false);
            outputArea.setFocusable(false);
            outputArea.setLineWrap(true);
            outputArea.setWrapStyleWord(false);
            outputArea.setBackground(BACKGROUND);
            outputArea.setForeground(FOREGROUND);
            outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
            outputArea.setBorder(BorderFactory.createEmptyBorder());

            JScrollPane scrollPane = new JScrollPane(outputArea);
            scrollPane.setHorizontalScrollBarPolicy(
                    JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            scrollPane.getVerticalScrollBar().setUI(new SteadyArcScrollBarUI());
            scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
            scrollPane.setBorder(BorderFactory.createEmptyBorder());
            scrollPane.getViewport().setBackground(BACKGROUND);
            content.add(scrollPane, BorderLayout.CENTER);

            window.setContentPane(content);
            window.pack();
            try {
                window.setOpacity(0.88f);
                opacitySupported = true;
            } catch (UnsupportedOperationException | IllegalArgumentException exception) {
                // Opacity is a progressive enhancement; feedback remains usable without it.
                opacitySupported = false;
            }

            /*
             * Clicking the output is an explicit request to inspect it. Cancel
             * any pending fade and keep the window until focus later leaves it.
             * The countdown then starts from that focus-loss event, giving the
             * user five full seconds after finishing inspection.
             */
            MouseAdapter pinSupport = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    pinnedByClick = true;
                    cancelFadeTimers();
                    restoreOpacity();
                    window.requestFocus();
                }
            };
            content.addMouseListener(pinSupport);
            status.addMouseListener(pinSupport);
            outputArea.addMouseListener(pinSupport);
            scrollPane.addMouseListener(pinSupport);
            scrollPane.getViewport().addMouseListener(pinSupport);
            scrollPane.getVerticalScrollBar().addMouseListener(pinSupport);

            /*
             * Mouse-wheel events are delivered independently of mouse-button
             * events. Treat scrolling as inspection activity too; otherwise a
             * completion timer can fade the window while the user is reading.
             */
            java.awt.event.MouseWheelListener wheelPinSupport = event -> {
                pinnedByClick = true;
                cancelFadeTimers();
                restoreOpacity();
                window.requestFocus();
            };
            outputArea.addMouseWheelListener(wheelPinSupport);
            scrollPane.addMouseWheelListener(wheelPinSupport);
            scrollPane.getViewport().addMouseWheelListener(wheelPinSupport);
            scrollPane.getVerticalScrollBar().addMouseWheelListener(wheelPinSupport);
            window.addWindowFocusListener(new WindowAdapter() {
                @Override
                public void windowLostFocus(WindowEvent event) {
                    if (completed && pinnedByClick) {
                        scheduleFadeAfterDelay();
                    }
                }
            });

            /*
             * The status strip is the drag handle. Keeping movement away from
             * the text area preserves normal scrolling and text inspection.
             * Persist only on release so preference storage is not rewritten
             * for every mouse-motion event.
             */
            MouseAdapter moveSupport = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    dragStartOnScreen = event.getLocationOnScreen();
                    dragWindowOrigin = window.getLocation();
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    if (dragStartOnScreen == null || dragWindowOrigin == null) {
                        return;
                    }
                    Point current = event.getLocationOnScreen();
                    window.setLocation(
                            dragWindowOrigin.x + current.x - dragStartOnScreen.x,
                            dragWindowOrigin.y + current.y - dragStartOnScreen.y);
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    rememberOutputLocation(window.getLocation());
                    dragStartOnScreen = null;
                    dragWindowOrigin = null;
                }
            };
            status.addMouseListener(moveSupport);
            status.addMouseMotionListener(moveSupport);

            /*
             * Escape is the unconditional exit hatch for a pinned always-on-top
             * window. It starts fading immediately, whether the command is
             * still running or already complete, without cancelling the child
             * process itself.
             */
            window.getRootPane()
                    .getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(javax.swing.KeyStroke.getKeyStroke(
                            java.awt.event.KeyEvent.VK_ESCAPE, 0), "fade-output");
            window.getRootPane().getActionMap().put(
                    "fade-output",
                    new javax.swing.AbstractAction() {
                        @Override
                        public void actionPerformed(java.awt.event.ActionEvent event) {
                            cancelFadeTimers();
                            startFade();
                        }
                    });
        }

        private void showRunning() {
            Point remembered = rememberedOutputLocation();
            if (remembered != null && isVisibleScreenLocation(remembered, window.getSize())) {
                window.setLocation(remembered);
            } else {
                window.setLocationRelativeTo(window.getOwner());
            }
            window.setVisible(true);
        }

        private void appendOutputLine(String line) {
            SwingUtilities.invokeLater(() -> {
                outputArea.setText(appendOutputTail(
                        outputArea.getText(), line, MAXIMUM_LINES));
                outputArea.setCaretPosition(outputArea.getDocument().getLength());
            });
        }

        private void completeAndFade(int processExit) {
            if (!window.isDisplayable()) {
                return;
            }
            completed = true;
            status.setText("Command completed — exit code " + processExit);
            outputArea.setCaretPosition(outputArea.getDocument().getLength());

            /*
             * Keep the final raw output readable for five seconds. Only then do
             * we fade the undecorated window; no parser attempts to reinterpret
             * PatchSequence output or replace it with a modal summary.
             */
            if (!pinnedByClick) {
                scheduleFadeAfterDelay();
            }
        }

        private void scheduleFadeAfterDelay() {
            if (lingerTimer != null) {
                lingerTimer.stop();
            }
            lingerTimer = new javax.swing.Timer(
                    COMPLETION_LINGER_MILLISECONDS,
                    event -> startFade());
            lingerTimer.setRepeats(false);
            lingerTimer.start();
        }

        private void startFade() {
            lingerTimer = null;
            if (!opacitySupported) {
                window.dispose();
                return;
            }
            fadeTimer = new javax.swing.Timer(
                    FADE_INTERVAL_MILLISECONDS,
                    null);
            fadeTimer.addActionListener(event -> {
                float nextOpacity = Math.max(0f, window.getOpacity() - FADE_STEP);
                if (nextOpacity <= 0f) {
                    fadeTimer.stop();
                    fadeTimer = null;
                    window.dispose();
                } else {
                    window.setOpacity(nextOpacity);
                }
            });
            fadeTimer.start();
        }

        private void cancelFadeTimers() {
            if (lingerTimer != null) {
                lingerTimer.stop();
                lingerTimer = null;
            }
            if (fadeTimer != null) {
                fadeTimer.stop();
                fadeTimer = null;
            }
        }

        private void restoreOpacity() {
            if (opacitySupported) {
                window.setOpacity(0.88f);
            }
        }

        private static Point rememberedOutputLocation() {
            try {
                java.util.prefs.Preferences preferences =
                        java.util.prefs.Preferences.userNodeForPackage(
                                ChatGptPayloadButton.class);
                int missing = Integer.MIN_VALUE;
                int x = preferences.getInt("outputWindowX", missing);
                int y = preferences.getInt("outputWindowY", missing);
                return x == missing || y == missing ? null : new Point(x, y);
            } catch (SecurityException exception) {
                // Preference persistence is useful but not required to show output.
                return null;
            }
        }

        private static void rememberOutputLocation(Point location) {
            try {
                java.util.prefs.Preferences preferences =
                        java.util.prefs.Preferences.userNodeForPackage(
                                ChatGptPayloadButton.class);
                preferences.putInt("outputWindowX", location.x);
                preferences.putInt("outputWindowY", location.y);
            } catch (SecurityException exception) {
                // A locked-down runtime may forbid preferences; movement still works.
            }
        }

        private static boolean isVisibleScreenLocation(
                Point location,
                Dimension windowSize) {
            Rectangle candidate = new Rectangle(location, windowSize);
            for (java.awt.GraphicsDevice device
                    : java.awt.GraphicsEnvironment
                            .getLocalGraphicsEnvironment()
                            .getScreenDevices()) {
                for (java.awt.GraphicsConfiguration configuration
                        : device.getConfigurations()) {
                    if (configuration.getBounds().intersects(candidate)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private static final class FileDragSource extends JComponent {
        private final Path repositoryRoot;

        private FileDragSource(Path repositoryRoot) {
            this.repositoryRoot = repositoryRoot;
            setPreferredSize(new Dimension(72, 72));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(
                    this,
                    DnDConstants.ACTION_COPY,
                    this::startDrag);
        }

        private void startDrag(DragGestureEvent event) {
            try {
                List<File> files = payloadFiles(repositoryRoot);
                event.startDrag(
                        DragSource.DefaultCopyDrop,
                        new FileListTransferable(files),
                        new DragSourceAdapter() { });
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                int diameter = Math.min(getWidth(), getHeight()) - 24;
                int x = (getWidth() - diameter) / 2;
                int y = (getHeight() - diameter) / 2;
                g.setColor(SteadyArcTheme.GOLD);
                g.fillOval(x, y, diameter, diameter);
                g.setColor(SteadyArcTheme.SURFACE_RECESSED);
                g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
                int centerX = getWidth() / 2;
                int centerY = getHeight() / 2;
                g.drawLine(centerX - 12, centerY, centerX + 12, centerY);
                g.drawLine(centerX + 4, centerY - 8, centerX + 12, centerY);
                g.drawLine(centerX + 4, centerY + 8, centerX + 12, centerY);
            } finally {
                g.dispose();
            }
        }
    }

    private static final class PanelMoveSupport extends MouseAdapter {
        private final JFrame frame;
        private Point pressedOnScreen;
        private Point frameOrigin;

        private PanelMoveSupport(JFrame frame) {
            this.frame = frame;
        }

        @Override
        public void mousePressed(MouseEvent event) {
            pressedOnScreen = event.getLocationOnScreen();
            frameOrigin = frame.getLocation();
        }

        @Override
        public void mouseDragged(MouseEvent event) {
            if (pressedOnScreen == null || frameOrigin == null) return;
            Point current = event.getLocationOnScreen();
            frame.setLocation(
                    frameOrigin.x + current.x - pressedOnScreen.x,
                    frameOrigin.y + current.y - pressedOnScreen.y);
        }

        @Override
        public void mouseReleased(MouseEvent event) {
            pressedOnScreen = null;
            frameOrigin = null;
        }
    }

    public static List<File> payloadFiles(Path directory) throws IOException {
        Path snapshot = findLatest(directory, SNAPSHOT_PREFIX, ".zip");
        Path testResults = findLatest(directory, TEST_RESULTS_PREFIX, ".log");
        Path snapshotManifest = findLatest(directory, SNAPSHOT_MANIFEST_PREFIX, ".json");
        return List.of(snapshot.toFile(), testResults.toFile(), snapshotManifest.toFile());
    }

    private static Path findLatest(Path directory, String prefix, String suffix)
            throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.startsWith(prefix) && name.endsWith(suffix);
                    })
                    .max(Comparator.comparingLong(ChatGptPayloadButton::lastModified))
                    .map(Path::toAbsolutePath)
                    .map(Path::normalize)
                    .orElseThrow(() -> new IOException(
                            "No " + prefix + "*" + suffix + " file found in " + directory));
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return Long.MIN_VALUE;
        }
    }

    private record CommandResult(int processExit, String output) {
    }

    private record FileListTransferable(List<File> files) implements Transferable {
        private FileListTransferable {
            files = List.copyOf(files);
            if (files.size() != 3 || files.stream().anyMatch(file -> !file.isFile())) {
                throw new IllegalArgumentException(
                        "Drag payload must contain three existing files");
            }
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.javaFileListFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.javaFileListFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor)
                throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return files;
        }
    }
}
