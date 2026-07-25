package app.application;


import app.deck.model.DeckGameState;
import app.deck.persistence.DeckCache;
import app.deck.tracking.DeckTracker;
import app.deck.tracking.DeckTrackerListener;
import app.deck.ui.DeckTrackerFrame;
import app.draft.export.DraftAiExporter;
import app.draft.model.DraftUiModel;
import app.draft.parsing.DraftLogParser;
import app.draft.tracking.DraftTracker;
import app.draft.ui.DraftAssistantFrame;
import app.coaching.application.CoachingService;
import app.coaching.persistence.CoachingRepository;
import app.coaching.ui.CoachingFrame;
import app.export.MatchAiExporter;
import app.model.log.ModelObject;
import app.model.log.LogMessageInterface;
import app.model.log.RawLogEntry;
import app.enrichment.CardCache;
import app.enrichment.CardImageCache;
import app.enrichment.InformationCollector;
import app.enrichment.ScryfallClient;
import app.replay.MainFrame;
import app.settings.SettingsDialog;
import app.settings.WindowsDpapiApiKeyStore;
import app.log.LogMessageReader;
import app.log.LogTailReader;
import app.log.NamedThreadFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.swing.*;
import java.nio.file.Path;
import java.time.Instant;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Bootstraps the desktop application and wires the log-ingestion, enrichment, routing, deck-tracking, and Swing presentation pipelines.
 *
 * <p>It is the composition root: it owns lifecycle and dependency construction but delegates domain behavior to the corresponding subsystems.</p>
 *
 * <p>It must not interpret Arena game semantics or perform replay rendering itself.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the composition and lifecycle boundary; it wires subsystems without taking ownership of domain interpretation.</p>
 */
public final class Application implements AutoCloseable {
    private final BlockingQueue<RawLogEntry> filteredLogQueue = new LinkedBlockingQueue<>(10_000);
    private final BlockingQueue<LogMessageInterface> enrichmentQueue = new LinkedBlockingQueue<>(5_000);
    private final BlockingQueue<LogMessageInterface> uiQueue = new LinkedBlockingQueue<>(5_000);

    private final ExecutorService pipelineExecutor = Executors.newFixedThreadPool(
            3, new NamedThreadFactory("arena-pipeline"));
    private final ExecutorService restExecutor = Executors.newFixedThreadPool(
            2, new NamedThreadFactory("arena-rest"));

    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final ScryfallClient scryfallClient = new ScryfallClient(gson);
    private final CardCache cardCache = new CardCache(
            gson,
            Path.of(System.getProperty("user.home"), ".arena-log-viewer", "card-cache"));
    private final CardImageCache cardImageCache = new CardImageCache(
            Path.of(System.getProperty("user.home"), ".arena-log-viewer", "images"));

    private LogTailReader logTailReader;
    private LogMessageReader logMessageReader;
    private InformationCollector informationCollector;
    private DeckCache deckCache;
    private CoachingRepository coachingRepository;

    public static void main(String[] args) {
        installSystemLookAndFeel();
        SwingUtilities.invokeLater(() -> {
            Application application = new Application();
            application.start(args);
        });
    }

    private static void installSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception error) {
            System.err.println("Unable to apply the system look and feel: " + error.getMessage());
        }
    }

    private void start(String[] args) {
        Path logPath = args.length > 0 ? Path.of(args[0]) : defaultLogPath();

        // 1. Initialize and start the log reader first.
        logTailReader = new LogTailReader(
                logPath,
                filteredLogQueue,
                Duration.ofMillis(250),
                true,
                this::reportError);
        pipelineExecutor.submit(logTailReader);

        // 2. Convert filtered raw entries into LogMessageInterface instances.
        logMessageReader = new LogMessageReader(
                filteredLogQueue,
                enrichmentQueue,
                gson,
                this::reportError);
        pipelineExecutor.submit(logMessageReader);

        // 3. Immediately forward messages to the UI queue and attach Future<ModelObject> data asynchronously.
        informationCollector = new InformationCollector(
                enrichmentQueue,
                uiQueue,
                scryfallClient,
                cardCache,
                Duration.ofMillis(110),
                restExecutor,
                this::reportError);
        pipelineExecutor.submit(informationCollector);

        deckCache = new DeckCache(
                gson,
                cardCache,
                Path.of(System.getProperty("user.home"), ".arena-log-viewer", "card-cache"));
        DeckTrackerFrame deckFrame = new DeckTrackerFrame(cardImageCache);
        DeckTracker deckTracker = new DeckTracker(gson, cardCache, deckCache, scryfallClient, restExecutor, new DeckTrackerListener() {
            @Override public void gameStarted(DeckGameState state) {
                SwingUtilities.invokeLater(() -> deckFrame.updateState(state));
            }
            @Override public void gameUpdated(DeckGameState state) {
                SwingUtilities.invokeLater(() -> deckFrame.updateState(state));
            }
            @Override public void gameCompleted(String matchId, int gameNumber) {
                // Retained snapshots remain browsable after game completion.
            }
        });

        coachingRepository = new CoachingRepository(
                Path.of(System.getProperty("user.home"), ".arena-log-viewer", "coaching"));
        CoachingService coachingService =
                new CoachingService(coachingRepository, new MatchAiExporter());
        CoachingFrame coachingFrame = new CoachingFrame(coachingService);

        DraftUiModel draftUiModel = new DraftUiModel();
        DraftTracker draftTracker = new DraftTracker(new DraftLogParser(), draftUiModel);
        DraftAssistantFrame draftFrame = new DraftAssistantFrame(draftUiModel, new DraftAiExporter());

        WindowsDpapiApiKeyStore apiKeyStore = new WindowsDpapiApiKeyStore();
        MainFrame frame = new MainFrame(
                uiQueue,
                deckTracker,
                deckFrame,
                draftTracker,
                draftFrame,
                coachingFrame::open,
                this::rescanLog,
                () -> replayDraftFixture(draftTracker, draftFrame),
                owner -> new SettingsDialog(owner, apiKeyStore).open(),
                ignored -> close());
        frame.setVisible(true);
    }


    private void replayDraftFixture(DraftTracker draftTracker, DraftAssistantFrame draftFrame) {
        draftTracker.reset();
        restExecutor.submit(() -> {
            try (InputStream input = Application.class.getResourceAsStream("/logs/premier-draft.log")) {
                if (input == null) throw new IllegalStateException("Missing /logs/premier-draft.log");
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                    long sequence = 9_000_000L;
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isBlank()) continue;
                        filteredLogQueue.put(new RawLogEntry(sequence++, Instant.now(), line));
                    }
                }
                SwingUtilities.invokeLater(draftFrame::open);
            } catch (Throwable error) {
                reportError(error);
            }
        });
    }

    private void rescanLog() {
        filteredLogQueue.clear();
        enrichmentQueue.clear();
        uiQueue.clear();
        if (logTailReader != null) logTailReader.requestRescanFromBeginning();
    }

    private Path defaultLogPath() {
        return Path.of(
                System.getProperty("user.home"),
                "AppData", "LocalLow", "Wizards Of The Coast", "MTGA", "Player.log");
    }

    private void reportError(Throwable error) {
        error.printStackTrace(System.err);
    }

    @Override
    public void close() {
        if (logTailReader != null) logTailReader.close();
        if (logMessageReader != null) logMessageReader.close();
        if (informationCollector != null) informationCollector.close();
        pipelineExecutor.shutdownNow();
        restExecutor.shutdownNow();
        scryfallClient.close();
        if (deckCache != null) deckCache.close();
        if (coachingRepository != null) coachingRepository.close();
        cardCache.close();
    }
}
