package app.replay;

import app.enrichment.CardImageCache;
import app.model.card.CardInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Owns the transient card-preview window and asynchronous image presentation.
 * Replay rendering supplies the header and remains independent of this lifecycle.
 */
final class CardPreviewController {
    private final CardImageCache imageCache;
    private JWindow window;

    CardPreviewController(CardImageCache imageCache) {
        this.imageCache = imageCache;
    }

    void show(Component owner, CardInfo card, JComponent header, MouseEvent mouse) {
        hide();
        JWindow preview = new JWindow(SwingUtilities.getWindowAncestor(owner));
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(separatorColor()),
                BorderFactory.createEmptyBorder(9, 9, 9, 9)));

        int imageCount = Math.max(1, card.previewImageUrls().size());
        JPanel images = new JPanel();
        images.setOpaque(false);
        images.setLayout(new BoxLayout(images, BoxLayout.X_AXIS));
        List<JLabel> labels = createLabels(images, card, imageCount);
        panel.add(header, BorderLayout.NORTH);
        panel.add(images, BorderLayout.CENTER);

        preview.setContentPane(panel);
        preview.pack();
        position(preview, owner, mouse);
        preview.setVisible(true);
        window = preview;
        loadImages(preview, card, labels, imageCount);
    }

    void hide() {
        if (window == null) return;
        window.dispose();
        window = null;
    }

    private List<JLabel> createLabels(JPanel images, CardInfo card, int imageCount) {
        List<JLabel> labels = new ArrayList<>();
        for (int index = 0; index < imageCount; index++) {
            JLabel image = new JLabel("Loading image\u2026", SwingConstants.CENTER);
            image.setPreferredSize(imageSize(card, imageCount));
            labels.add(image);
            images.add(image);
            if (index + 1 < imageCount) images.add(Box.createHorizontalStrut(8));
        }
        return labels;
    }

    private void position(JWindow preview, Component owner, MouseEvent mouse) {
        Point screen = mouse.getLocationOnScreen();
        Rectangle bounds = owner.getGraphicsConfiguration().getBounds();
        preview.setLocation(
                Math.min(screen.x + 18, bounds.x + bounds.width - preview.getWidth() - 8),
                Math.min(screen.y + 18, bounds.y + bounds.height - preview.getHeight() - 8));
    }

    private void loadImages(JWindow preview, CardInfo card,
                            List<JLabel> labels, int imageCount) {
        for (int index = 0; index < imageCount; index++) {
            int imageIndex = index;
            JLabel label = labels.get(index);
            imageCache.get(card, imageIndex).thenAccept(optional ->
                    SwingUtilities.invokeLater(() -> {
                        if (window != preview || !preview.isVisible()) return;
                        if (optional.isEmpty()) {
                            label.setText("No image available");
                            return;
                        }
                        BufferedImage raw = optional.get();
                        if (isSplitLayout(card) && imageCount == 1) raw = rotateClockwise(raw);
                        Dimension scaled = scaledSize(raw, imageSize(card, imageCount));
                        label.setText("");
                        label.setIcon(new ImageIcon(raw.getScaledInstance(
                                scaled.width, scaled.height, Image.SCALE_SMOOTH)));
                        preview.pack();
                    }));
        }
    }

    static Dimension imageSize(CardInfo card, int imageCount) {
        if (isSplitLayout(card) && imageCount == 1) return new Dimension(340, 244);
        return imageCount > 1 ? new Dimension(220, 307) : new Dimension(244, 340);
    }

    static boolean isSplitLayout(CardInfo card) {
        String layout = card == null || card.getLayout() == null
                ? "" : card.getLayout().toLowerCase(Locale.ROOT);
        return layout.equals("split") || layout.equals("aftermath");
    }

    private static Dimension scaledSize(BufferedImage image, Dimension target) {
        int width = target.width;
        int height = Math.max(1, image.getHeight() * width / image.getWidth());
        if (height > target.height) {
            height = target.height;
            width = Math.max(1, image.getWidth() * height / image.getHeight());
        }
        return new Dimension(width, height);
    }

    private static BufferedImage rotateClockwise(BufferedImage source) {
        BufferedImage rotated = new BufferedImage(
                source.getHeight(), source.getWidth(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = rotated.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.translate(rotated.getWidth(), 0);
            graphics.rotate(Math.PI / 2);
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rotated;
    }

    private Color separatorColor() {
        Color color = UIManager.getColor("Separator.foreground");
        return color == null ? Color.GRAY : color;
    }
}
