package app.draft.ui;

import app.enrichment.CardImageCache;
import app.model.card.CardInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** One shared, asynchronous card-art hover window for the draft workspace. */
final class DraftCardPreview {
    private final CardImageCache images;
    private final JWindow window = new JWindow();
    private long request;

    DraftCardPreview(CardImageCache images) {
        this.images = images;
        window.setAlwaysOnTop(true);
    }

    void show(CardInfo card, Component anchor) {
        if (card == null || card.previewImageUrls().isEmpty()) return;
        long token = ++request;
        List<CompletableFuture<Optional<BufferedImage>>> loads =
                java.util.stream.IntStream.range(0, card.previewImageUrls().size())
                        .mapToObj(index -> images.get(card, index))
                        .toList();
        CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new))
                .thenRun(() -> SwingUtilities.invokeLater(() -> {
                    if (request != token || !anchor.isShowing()) return;
                    List<BufferedImage> loaded = loads.stream()
                            .map(CompletableFuture::join)
                            .flatMap(Optional::stream)
                            .toList();
                    if (loaded.isEmpty()) return;
                    JPanel faces = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
                    faces.setBackground(new Color(28, 28, 28));
                    for (BufferedImage image : loaded) {
                        int height = 350;
                        int width = Math.max(1,
                                (int) Math.round(image.getWidth() * height
                                        / (double) image.getHeight()));
                        faces.add(new JLabel(new ImageIcon(image.getScaledInstance(
                                width, height, Image.SCALE_SMOOTH))));
                    }
                    window.setContentPane(faces);
                    window.pack();
                    position(anchor);
                    window.setVisible(true);
                }));
    }

    void hide() {
        request++;
        window.setVisible(false);
    }

    private void position(Component anchor) {
        Point point = anchor.getLocationOnScreen();
        Rectangle screen = anchor.getGraphicsConfiguration().getBounds();
        int x = point.x + anchor.getWidth() + 8;
        if (x + window.getWidth() > screen.x + screen.width) {
            x = point.x - window.getWidth() - 8;
        }
        int y = Math.max(screen.y,
                Math.min(point.y, screen.y + screen.height - window.getHeight()));
        window.setLocation(x, y);
    }
}
