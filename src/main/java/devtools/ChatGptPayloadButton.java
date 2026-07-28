package devtools;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** One-click helper for attaching the latest snapshot and test log to ChatGPT. */
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

        JButton button = new JButton("Copy snapshot + tests");
        button.setPreferredSize(new Dimension(240, 72));
        button.setFocusable(false);
        DragSupport dragSupport = new DragSupport(frame);
        button.addMouseListener(dragSupport);
        button.addMouseMotionListener(dragSupport);
        button.addActionListener(event -> {
            if (!dragSupport.consumeDrag()) {
                copyFilesToClipboard(button);
            }
        });

        frame.setContentPane(button);
        frame.pack();
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }


    private static final class DragSupport extends MouseAdapter {
        private static final int DRAG_THRESHOLD = 4;

        private final JFrame frame;
        private Point pressedOnScreen;
        private Point frameOrigin;
        private boolean dragged;

        private DragSupport(JFrame frame) {
            this.frame = frame;
        }

        @Override
        public void mousePressed(MouseEvent event) {
            pressedOnScreen = event.getLocationOnScreen();
            frameOrigin = frame.getLocation();
            dragged = false;
        }

        @Override
        public void mouseDragged(MouseEvent event) {
            if (pressedOnScreen == null || frameOrigin == null) {
                return;
            }
            Point current = event.getLocationOnScreen();
            int dx = current.x - pressedOnScreen.x;
            int dy = current.y - pressedOnScreen.y;
            if (!dragged && Math.abs(dx) + Math.abs(dy) < DRAG_THRESHOLD) {
                return;
            }
            dragged = true;
            frame.setLocation(frameOrigin.x + dx, frameOrigin.y + dy);
        }

        @Override
        public void mouseReleased(MouseEvent event) {
            pressedOnScreen = null;
            frameOrigin = null;
        }

        private boolean consumeDrag() {
            boolean result = dragged;
            dragged = false;
            return result;
        }
    }

    private static void copyFilesToClipboard(JButton button) {
        try {
            Path searchDirectory = Path.of("").toAbsolutePath().normalize();
            Path snapshot = findLatest(searchDirectory, SNAPSHOT_PREFIX, ".zip");
            Path testResults = findLatest(searchDirectory, TEST_RESULTS_PREFIX, ".log");
            List<File> files = List.of(snapshot.toFile(), testResults.toFile());

            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new FileListTransferable(files), null);
            button.setText("Copied 2 files");
        } catch (Exception exception) {
            button.setText("Files not found");
            exception.printStackTrace();
        }
    }

    private static Path findLatest(Path directory, String prefix, String suffix) throws IOException {
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
                throw new IllegalArgumentException("Clipboard payload must contain two existing files");
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
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return files;
        }
    }
}
