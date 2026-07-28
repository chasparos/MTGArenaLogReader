package devtools;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Drag helper for attaching the latest snapshot and test log to ChatGPT. */
public final class ChatGptPayloadButton {
    private static final String SNAPSHOT_PREFIX = "latest snapshot";
    private static final String TEST_RESULTS_PREFIX = "latest test results";

    private ChatGptPayloadButton() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatGptPayloadButton::showWidget);
    }

    private static void showWidget() {
        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setAlwaysOnTop(true);
        frame.setUndecorated(true);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(112, 112));
        panel.setBackground(new Color(42, 42, 42));
        PanelMoveSupport moveSupport = new PanelMoveSupport(frame);
        panel.addMouseListener(moveSupport);
        panel.addMouseMotionListener(moveSupport);

        FileDragSource dragSource = new FileDragSource();
        panel.add(dragSource, BorderLayout.CENTER);

        frame.setContentPane(panel);
        frame.pack();
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    private static final class FileDragSource extends JComponent {
        private FileDragSource() {
            setPreferredSize(new Dimension(80, 80));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(
                    this,
                    DnDConstants.ACTION_COPY,
                    this::startDrag);
        }

        private void startDrag(DragGestureEvent event) {
            try {
                List<File> files = payloadFiles();
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
                g.setColor(new Color(235, 235, 235));
                g.fillOval(x, y, diameter, diameter);
                g.setColor(new Color(55, 55, 55));
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

    private static List<File> payloadFiles() throws IOException {
        Path directory = Path.of("").toAbsolutePath().normalize();
        Path snapshot = findLatest(directory, SNAPSHOT_PREFIX, ".zip");
        Path testResults = findLatest(directory, TEST_RESULTS_PREFIX, ".log");
        return List.of(snapshot.toFile(), testResults.toFile());
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

    private record FileListTransferable(List<File> files) implements Transferable {
        private FileListTransferable {
            files = List.copyOf(files);
            if (files.size() != 2 || files.stream().anyMatch(file -> !file.isFile())) {
                throw new IllegalArgumentException(
                        "Drag payload must contain two existing files");
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
