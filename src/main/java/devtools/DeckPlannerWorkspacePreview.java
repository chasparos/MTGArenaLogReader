package devtools;

import app.deckplanner.application.DeckPlannerFilterCoordinator;
import app.deckplanner.catalog.FormatCatalogRepository;
import app.deckplanner.filter.CatalogFilterIndex;
import app.deckplanner.filter.DeckPlannerFilterModel;
import app.deckplanner.ui.CardBrowserPanel;
import app.deckplanner.ui.DeckPlannerWorkspace;
import app.model.card.CardInfo;
import app.settings.ThemeService;
import app.ui.AppColors;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Standalone human-review harness for the composed DP-05 filter workspace. */
public final class DeckPlannerWorkspacePreview {
    private DeckPlannerWorkspacePreview() { }

    public static void main(String[] args) {
        new ThemeService().applySaved();
        SwingUtilities.invokeLater(() -> {
            PreviewSession session = createSession();
            JFrame frame = new JFrame("Deck Planner Filter Workspace Review");
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.setContentPane(session.content());
            frame.setSize(1280, 820);
            frame.setLocationByPlatform(true);
            frame.addWindowListener(new WindowAdapter() {
                @Override public void windowClosed(WindowEvent event) { session.close(); }
            });
            frame.setVisible(true);
            session.workspace().start();
        });
    }

    static PreviewSession createSession() {
        assertEdt();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "planner-preview-scheduler"));
        ExecutorService worker = Executors.newSingleThreadExecutor(r -> daemon(r, "planner-preview-worker"));
        CatalogFilterIndex index = new CatalogFilterIndex(sampleSnapshot(72));
        DeckPlannerWorkspace workspace = new DeckPlannerWorkspace(
                new DeckPlannerFilterModel("standard"), index,
                DeckPlannerWorkspacePreview::requestPreviewImage,
                scheduler, worker, Duration.ofMillis(120),
                DeckPlannerFilterCoordinator.Availability.READY);
        workspace.browser().setUnderConsiderationIdentities(Set.of(
                "preview-oracle-2", "preview-oracle-7", "preview-oracle-13"));

        JLabel instructions = new JLabel("Use filters rapidly; verify live tag counts, keyboard focus, reset, stable scrolling, and explicit cache states.");
        instructions.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        instructions.setOpaque(true);
        instructions.setBackground(AppColors.color("Panel.background", new Color(0x202328)));
        instructions.setForeground(AppColors.color("Label.foreground", Color.WHITE));

        JPanel stateButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        stateButtons.setOpaque(false);
        JButton ready = new JButton("Ready");
        JButton partial = new JButton("Partial cache");
        JButton offline = new JButton("Offline cache");
        ready.addActionListener(e -> workspace.setAvailability(DeckPlannerFilterCoordinator.Availability.READY));
        partial.addActionListener(e -> workspace.setAvailability(DeckPlannerFilterCoordinator.Availability.PARTIAL_CACHE));
        offline.addActionListener(e -> workspace.setAvailability(DeckPlannerFilterCoordinator.Availability.OFFLINE));
        stateButtons.add(ready); stateButtons.add(partial); stateButtons.add(offline);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(true);
        header.setBackground(instructions.getBackground());
        header.add(instructions, BorderLayout.CENTER);
        header.add(stateButtons, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(true);
        content.setBackground(instructions.getBackground());
        content.add(header, BorderLayout.NORTH);
        content.add(workspace, BorderLayout.CENTER);
        return new PreviewSession(content, workspace, scheduler, worker);
    }

    static FormatCatalogRepository.Snapshot sampleSnapshot(int count) {
        List<FormatCatalogRepository.CardOutcome> outcomes = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            CardInfo card = new CardInfo();
            card.setId("preview-printing-" + index);
            card.setOracleId("preview-oracle-" + index);
            card.setArenaId(900000L + index);
            card.setName("Planner Card " + (index + 1));
            card.setTypeLine(typeLine(index));
            card.setCmc((double) (index % 8));
            card.setColors(colors(index));
            card.setColorIdentity(colors(index));
            card.setKeywords(index % 5 == 0 ? List.of("Flying") : index % 7 == 0 ? List.of("Trample") : List.of());
            card.setOracleText(oracleText(index));
            outcomes.add(new FormatCatalogRepository.CardOutcome(card, "SUCCESS", null));
        }
        Instant now = Instant.now();
        return new FormatCatalogRepository.Snapshot("preview", "standard",
                FormatCatalogRepository.SCHEMA_VERSION, now, now, List.copyOf(outcomes));
    }

    private static String typeLine(int index) {
        return switch (index % 6) {
            case 0 -> "Creature — Wizard";
            case 1 -> "Instant";
            case 2 -> "Sorcery";
            case 3 -> "Artifact";
            case 4 -> "Enchantment";
            default -> "Land";
        };
    }

    private static List<String> colors(int index) {
        return switch (index % 8) {
            case 0 -> List.of("W");
            case 1 -> List.of("U");
            case 2 -> List.of("B");
            case 3 -> List.of("R");
            case 4 -> List.of("G");
            case 5 -> List.of("U", "R");
            default -> List.of();
        };
    }

    private static String oracleText(int index) {
        return switch (index % 7) {
            case 0 -> "Target creature gains flying until end of turn.";
            case 1 -> "Mill three cards, then return a card from your graveyard to your hand.";
            case 2 -> "Sacrifice a creature: Draw a card.";
            case 3 -> "Exile target permanent until this leaves the battlefield.";
            case 4 -> "All creatures get +1/+1 until end of turn.";
            case 5 -> "Search your library for a basic land card.";
            default -> "Create a 1/1 colorless artifact creature token.";
        };
    }

    private static CompletableFuture<Optional<BufferedImage>> requestPreviewImage(CardBrowserPanel.BrowserCard card) {
        return CompletableFuture.supplyAsync(() -> {
            try { TimeUnit.MILLISECONDS.sleep(45L + Math.floorMod(card.identity().hashCode(), 140)); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return Optional.empty(); }
            return Optional.of(makeImage(card));
        });
    }

    private static BufferedImage makeImage(CardBrowserPanel.BrowserCard card) {
        BufferedImage image = new BufferedImage(280, 420, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            int ordinal = Math.floorMod(card.identity().hashCode(), 24);
            graphics.setColor(Color.getHSBColor(ordinal / 24f, 0.42f, 0.74f));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(new Color(20, 20, 24, 195));
            graphics.fillRoundRect(18, 300, 244, 92, 18, 18);
            graphics.setColor(Color.WHITE);
            graphics.setFont(graphics.getFont().deriveFont(Font.BOLD, 18f));
            graphics.drawString(card.name(), 30, 346);
        } finally { graphics.dispose(); }
        return image;
    }

    private static Thread daemon(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    private static void assertEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Preview content must be created on EDT");
    }

    record PreviewSession(JComponent content, DeckPlannerWorkspace workspace,
                          ScheduledExecutorService scheduler, ExecutorService worker) implements AutoCloseable {
        @Override public void close() {
            workspace.close();
            scheduler.shutdownNow();
            worker.shutdownNow();
        }
    }
}
