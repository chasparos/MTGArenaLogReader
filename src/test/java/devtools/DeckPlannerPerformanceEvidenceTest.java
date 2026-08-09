package devtools;

import app.deckplanner.application.DeckPlannerFilterCoordinator;
import app.deckplanner.catalog.CardCatalogPage;
import app.deckplanner.catalog.CardCatalogSource;
import app.deckplanner.ui.CardBrowserPanel;
import app.deckplanner.ui.CardGridLayout;
import app.deckplanner.ui.ViewportImageWindow;
import app.model.card.CardInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DeckPlannerPerformanceEvidenceTest {
    @TempDir Path tempDir;

    @Test
    void recordsCachedStartupEdtViewportAndBrowserCacheEvidence() throws Exception {
        Path root = tempDir.resolve("preview");
        List<CardInfo> catalog = cards(600);
        CardCatalogSource source = new CardCatalogSource() {
            @Override public CardCatalogPage firstPage(String normalizedFormat) {
                return new CardCatalogPage(catalog, null);
            }
            @Override public CardCatalogPage nextPage(String cursor) {
                throw new AssertionError("single-page fixture");
            }
        };
        DeckPlannerStandardPreviewCatalog.LoadResult seeded =
                DeckPlannerStandardPreviewCatalog.load(
                        root.resolve("format-catalog"), source, ignored -> { }, catalog.size());
        assertEquals(600, seeded.snapshot().orElseThrow().cardGroups().size());

        long startupStart = System.nanoTime();
        DeckPlannerStandardPreviewCatalog.LoadResult cached =
                DeckPlannerStandardPreviewCatalog.load(root);
        long startupMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startupStart);
        assertTrue(cached.snapshot().isPresent());
        assertEquals(DeckPlannerFilterCoordinator.Availability.READY, cached.availability());
        assertFalse(cached.refreshRecommended());

        ExecutorService blockedWorker = Executors.newSingleThreadExecutor();
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        AtomicBoolean heartbeat = new AtomicBoolean();
        try {
            blockedWorker.submit(() -> {
                workerStarted.countDown();
                try { releaseWorker.await(2, TimeUnit.SECONDS); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            });
            assertTrue(workerStarted.await(1, TimeUnit.SECONDS));
            long heartbeatStart = System.nanoTime();
            SwingUtilities.invokeAndWait(() -> heartbeat.set(true));
            long heartbeatMillis =
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - heartbeatStart);
            assertTrue(heartbeat.get(), "EDT must remain serviceable while background work is pending");

            AtomicInteger requests = new AtomicInteger();
            BufferedImage image = new BufferedImage(63, 88, BufferedImage.TYPE_INT_RGB);
            CardBrowserPanel[] browser = new CardBrowserPanel[1];
            SwingUtilities.invokeAndWait(() -> {
                browser[0] = new CardBrowserPanel(
                        new CardGridLayout(275, 400, 8, 8, 8),
                        new ViewportImageWindow(500),
                        card -> {
                            requests.incrementAndGet();
                            return CompletableFuture.completedFuture(Optional.of(image));
                        });
                browser[0].setSize(1200, 800);
                browser[0].setCards(cached.snapshot().orElseThrow().cardGroups().stream()
                        .map(group -> new CardBrowserPanel.BrowserCard(
                                group.identity(),
                                group.preferredPrinting().getName(),
                                group.printings().size(),
                                group.preferredPrinting()))
                        .toList());
                browser[0].updateViewport(new Rectangle(0, 0, 1200, 800));
            });
            SwingUtilities.invokeAndWait(() -> { });
            int firstRequests = requests.get();
            int firstCache = onEdt(browser[0]::cachedImageCount);
            int firstWindow = onEdt(browser[0]::requestedImageIdentityCount);
            assertTrue(firstRequests > 0);
            assertTrue(firstRequests < 600, "viewport materialization must not request the full catalog");
            assertEquals(firstRequests, firstCache);
            assertEquals(firstRequests, firstWindow);

            SwingUtilities.invokeAndWait(() ->
                    browser[0].updateViewport(new Rectangle(0, 0, 1200, 800)));
            SwingUtilities.invokeAndWait(() -> { });
            assertEquals(firstRequests, requests.get(),
                    "revisiting the same viewport should reuse browser image entries");

            SwingUtilities.invokeAndWait(() ->
                    browser[0].updateViewport(new Rectangle(0, 6000, 1200, 800)));
            SwingUtilities.invokeAndWait(() -> { });
            int afterScrollRequests = requests.get();
            int afterScrollCache = onEdt(browser[0]::cachedImageCount);
            assertTrue(afterScrollRequests > firstRequests);
            assertTrue(afterScrollRequests < 600);
            assertEquals(afterScrollRequests, afterScrollCache,
                    "browser image cache should grow only with distinct requested cards");

            System.out.printf(
                    "[DP08 PERF] cached-first-usable=%dms edt-heartbeat=%dms catalog=%d firstWindow=%d afterScrollRequests=%d browserCache=%d%n",
                    startupMillis, heartbeatMillis, catalog.size(), firstWindow,
                    afterScrollRequests, afterScrollCache);
        } finally {
            releaseWorker.countDown();
            blockedWorker.shutdownNow();
            assertTrue(blockedWorker.awaitTermination(1, TimeUnit.SECONDS));
        }
    }

    private static <T> T onEdt(java.util.concurrent.Callable<T> action) throws Exception {
        final Object[] value = new Object[1];
        final Throwable[] failure = new Throwable[1];
        SwingUtilities.invokeAndWait(() -> {
            try { value[0] = action.call(); }
            catch (Throwable error) { failure[0] = error; }
        });
        if (failure[0] != null) throw new RuntimeException(failure[0]);
        @SuppressWarnings("unchecked") T cast = (T) value[0];
        return cast;
    }

    private static List<CardInfo> cards(int count) {
        List<CardInfo> cards = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            CardInfo card = new CardInfo();
            card.setId("dp08-printing-" + index);
            card.setOracleId("dp08-oracle-" + index);
            card.setArenaId(900000L + index);
            card.setName("DP08 Card " + index);
            card.setTypeLine(index % 3 == 0 ? "Creature — Wizard" : "Instant");
            card.setCmc((double) (index % 7));
            card.setColors(index % 2 == 0 ? List.of("U") : List.of("R"));
            card.setColorIdentity(card.getColors());
            card.setGames(List.of("arena"));
            card.setLegalities(java.util.Map.of("standard", "legal"));
            cards.add(card);
        }
        return List.copyOf(cards);
    }
}
