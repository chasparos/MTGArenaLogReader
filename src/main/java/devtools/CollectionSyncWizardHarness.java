package devtools;

import app.collection.CollectionUpdate;
import app.collection.ui.CollectionSyncPanel;
import app.settings.ThemeService;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Click-review harness for the application-owned, provider-neutral synchronization wizard. */
public final class CollectionSyncWizardHarness {
    public static void main(String[] args) {
        new ThemeService().applySaved();
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Collection Synchronization Wizard — UI Harness");
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.setSize(760, 620);
            frame.setLocationByPlatform(true);
            frame.add(new CollectionSyncPanel(new FakeUpdate(), card -> {
                BufferedImage image = new BufferedImage(124, 172, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                int hue = Math.floorMod(Long.hashCode(card.arenaId()), 360);
                graphics.setPaint(new GradientPaint(0, 0, Color.getHSBColor(hue / 360f, .55f, .8f),
                        124, 172, Color.getHSBColor(hue / 360f, .75f, .3f)));
                graphics.fillRect(0, 0, 124, 172);
                graphics.setColor(new Color(255, 255, 255, 220));
                graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
                graphics.drawString(card.setCode().toUpperCase(), 12, 150);
                graphics.dispose();
                return CompletableFuture.completedFuture(Optional.of(image));
            }));
            frame.setVisible(true);
        });
    }

    private static final class FakeUpdate implements CollectionUpdate {
        @Override public Session begin(Observer observer) {
            observer.onEvent(new Status("Thank you! I can see the running Arena client now."));
            return new Session() {
                private boolean cardsShown;
                @Override public void respond(Response response) {
                    if (response instanceof Continue && !cardsShown) {
                        cardsShown = true;
                        observer.onEvent(new CardsRequired(
                                "Great! Click Decks, then Collection in Arena. Choose two cards you can verify.",
                                2, 5, List.of(
                                new CardOption(67692, "Ajani's Welcome", "M19", "Core Set 2019", "6"),
                                new CardOption(104942, "Bruce Banner", "MSH", "Marvel Super Heroes", "49"),
                                new CardOption(101332, "Volatile Rift", "Y26", "Alchemy: Lorwyn Eclipsed", "28"),
                                new CardOption(92156, "Balemurk Leech", "DSK", "Duskmourn", "84"),
                                new CardOption(80225, "Deal Gone Bad", "SNC", "Streets of New Capenna", "74"))));
                    } else if (response instanceof VerifiedCards cards) {
                        observer.onEvent(new Status(cards.copies().size() + " cards verified"));
                    } else if (response instanceof Continue) {
                        observer.onEvent(new Status("Looking for your Arena collection"));
                        Timer timer = new Timer(900, event -> observer.onEvent(new Completed(true,
                                new Summary(3322, 150, 347,
                                        java.util.Map.of("White", 58, "Blue", 51, "Black", 67,
                                                "Red", 74, "Green", 63, "Colorless", 34),
                                        java.util.Map.of("Common", 71, "Uncommon", 48,
                                                "Rare", 25, "Mythic", 6),
                                        java.util.Map.of("Duskmourn", 42, "Marvel", 31,
                                                "Core Set 2019", 27)),
                                "Collection synchronized safely")));
                        timer.setRepeats(false);
                        timer.start();
                    }
                }
                @Override public void cancel() {
                    observer.onEvent(new Completed(false, 0, "Synchronization cancelled"));
                }
            };
        }
    }
}
