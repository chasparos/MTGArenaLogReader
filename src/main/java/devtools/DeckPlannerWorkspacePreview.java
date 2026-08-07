package devtools;

import app.deckplanner.application.DeckPlannerFilterCoordinator;
import app.deckplanner.catalog.FormatCatalogRepository;
import app.deckplanner.collection.CollectionQuantity;
import app.deckplanner.consideration.UnderConsiderationModel;
import app.deckplanner.consideration.UnderConsiderationRepository;
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
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Standalone human click-test harness for DP-06 acceptance. */
public final class DeckPlannerWorkspacePreview {
    private static final List<String> INITIAL_CONSIDERATION = List.of(
            "oracle:preview-oracle-2", "oracle:preview-oracle-7", "preview-stale-card");
    private static final String SAMPLE_ARENA_DECK = """
            Deck
            4 Planner Card 3
            2 Planner Card 8
            1 Planner Card 15

            Sideboard
            2 Planner Card 22
            1 Card That Does Not Exist
            """;
    private static final Path DEFAULT_DATABASE =
            Path.of("target", "deck-planner-dp06-preview", "consideration");

    private DeckPlannerWorkspacePreview() { }

    public static void main(String[] args) {
        new ThemeService().applySaved();
        SwingUtilities.invokeLater(() -> {
            PreviewSession session = createSession();
            JFrame frame = new JFrame("Deck Planner DP-06 Acceptance Review");
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.setContentPane(session.content());
            frame.setSize(1500, 900);
            frame.setLocationByPlatform(true);
            frame.addWindowListener(new WindowAdapter() {
                @Override public void windowClosed(WindowEvent event) { session.close(); }
            });
            frame.setVisible(true);
            session.workspace().start();
        });
    }

    static PreviewSession createSession() {
        return createSession(DEFAULT_DATABASE);
    }

    static PreviewSession createSession(Path databasePath) {
        assertEdt();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                r -> daemon(r, "planner-preview-scheduler"));
        ExecutorService worker = Executors.newSingleThreadExecutor(
                r -> daemon(r, "planner-preview-worker"));
        CatalogFilterIndex index = new CatalogFilterIndex(sampleSnapshot(72));

        UnderConsiderationRepository repository = new UnderConsiderationRepository(databasePath);
        // The acceptance harness uses a tiny local H2 store and synchronous writes so closing and
        // relaunching the preview deterministically exercises the same persisted state.
        UnderConsiderationModel consideration =
                UnderConsiderationModel.persisted(repository, Runnable::run);
        if (consideration.identities().isEmpty()) consideration.add(INITIAL_CONSIDERATION);

        DeckPlannerWorkspace workspace = new DeckPlannerWorkspace(
                new DeckPlannerFilterModel("standard"), index,
                DeckPlannerWorkspacePreview::requestPreviewImage,
                scheduler, worker, Duration.ofMillis(120),
                DeckPlannerFilterCoordinator.Availability.READY,
                consideration, DeckPlannerWorkspacePreview::previewCollectionQuantity);

        JTextArea checklist = new JTextArea("""
                DP-06 HUMAN CLICK ACCEPTANCE
                1. Double-click browser cards; verify they appear at right and get a consideration badge.
                2. Select candidates at right; verify Up/Down, Remove, and Clear work and ordering is visible.
                3. Apply filters after adding candidates; verify hidden candidates remain at right and badges return when filters reset.
                4. Verify the seeded "Unavailable card" row remains recoverable and removable.
                5. Copy the sample Arena deck below, click Import deck, paste it, and verify four unique cards import while the missing card is reported.
                6. Verify imported cards do not change collection ownership; preview rows intentionally show unknown, zero, and positive quantities.
                7. Close and relaunch this preview; verify candidate membership/order survives restart.
                8. Exercise Ready / Partial cache / Offline cache and normal resizing/scrolling before accepting DP-06.
                """);
        checklist.setEditable(false);
        checklist.setFocusable(false);
        checklist.setLineWrap(true);
        checklist.setWrapStyleWord(true);
        checklist.setRows(8);
        checklist.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        checklist.setBackground(AppColors.color("Panel.background", new Color(0x202328)));
        checklist.setForeground(AppColors.color("Label.foreground", Color.WHITE));

        JTextArea sampleDeck = new JTextArea(SAMPLE_ARENA_DECK);
        sampleDeck.setEditable(false);
        sampleDeck.setRows(7);
        sampleDeck.setColumns(28);
        sampleDeck.setBorder(BorderFactory.createTitledBorder("Sample Arena deck — copy this into Import deck"));

        JPanel stateButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        stateButtons.setOpaque(false);
        JButton ready = new JButton("Ready");
        JButton partial = new JButton("Partial cache");
        JButton offline = new JButton("Offline cache");
        JButton reset = new JButton("Reset acceptance state");
        ready.addActionListener(e -> workspace.setAvailability(DeckPlannerFilterCoordinator.Availability.READY));
        partial.addActionListener(e -> workspace.setAvailability(DeckPlannerFilterCoordinator.Availability.PARTIAL_CACHE));
        offline.addActionListener(e -> workspace.setAvailability(DeckPlannerFilterCoordinator.Availability.OFFLINE));
        reset.addActionListener(e -> {
            consideration.clear();
            consideration.add(INITIAL_CONSIDERATION);
        });
        stateButtons.add(reset);
        stateButtons.add(ready);
        stateButtons.add(partial);
        stateButtons.add(offline);

        JPanel review = new JPanel(new BorderLayout(8, 4));
        review.setOpaque(true);
        review.setBackground(checklist.getBackground());
        review.add(new JScrollPane(checklist,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);
        review.add(new JScrollPane(sampleDeck), BorderLayout.WEST);
        review.add(stateButtons, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(true);
        content.setBackground(checklist.getBackground());
        content.add(review, BorderLayout.NORTH);
        content.add(workspace, BorderLayout.CENTER);
        return new PreviewSession(content, workspace, scheduler, worker, repository);
    }

    static String sampleArenaDeck() {
        return SAMPLE_ARENA_DECK;
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

    private static int previewCollectionQuantity(CardInfo card) {
        int bucket = Math.floorMod(card.getName().hashCode(), 3);
        return switch (bucket) {
            case 0 -> CollectionQuantity.UNKNOWN;
            case 1 -> 0;
            default -> 2;
        };
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
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Preview content must be created on EDT");
        }
    }

    record PreviewSession(JComponent content, DeckPlannerWorkspace workspace,
                          ScheduledExecutorService scheduler, ExecutorService worker,
                          UnderConsiderationRepository repository) implements AutoCloseable {
        @Override public void close() {
            workspace.close();
            scheduler.shutdownNow();
            worker.shutdownNow();
            repository.close();
        }
    }
}
