package app.collection.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MemoryCollectionServiceTest {
    @TempDir Path temporary;

    @Test
    void fakeHarnessPublishesProgressOutputAndCompleteOwnership() throws Exception {
        List<String> progress = new CopyOnWriteArrayList<>();
        List<String> output = new CopyOnWriteArrayList<>();
        try (MemoryCollectionService service = MemoryCollectionService.fakeHarness(
                temporary.resolve("service"), progress::add, output::add)) {
            assertEquals(Map.of(1001L, -1), service.getCopiesOwned(List.of(1001L)));
            java.util.concurrent.CompletableFuture<app.collection.CollectionUpdate.Completed> completed =
                    new java.util.concurrent.CompletableFuture<>();
            var session = service.begin(event -> {
                if (event instanceof app.collection.CollectionUpdate.Completed value) completed.complete(value);
            });
            session.respond(new app.collection.CollectionUpdate.Continue());
            var completion = completed.get(5, TimeUnit.SECONDS);
            assertTrue(completion.updated());
            assertEquals(3, completion.summary().distinctCardsOwned());
            assertEquals(7, completion.summary().totalCopies());

            assertEquals(Map.of(1001L, 4, 9999L, 0),
                    service.getCopiesOwned(List.of(1001L, 9999L)));
            assertEquals("Scan started", progress.get(0));
            assertTrue(progress.contains("Arena client process acquired (simulated)"));
            assertTrue(progress.get(progress.size() - 1).startsWith("Complete collection published:"));
            assertEquals(1, output.size());
            assertTrue(output.get(0).contains("1001 -> 4"));
        }
    }

    @Test
    void cancellationInterruptsActiveWorkAndPreventsPublication() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        CollectionScanEngine blocking = progress -> {
            entered.countDown();
            try {
                while (true) Thread.sleep(1_000);
            } catch (InterruptedException error) {
                interrupted.set(true);
                throw error;
            }
        };
        try (MemoryCollectionService service = new MemoryCollectionService(
                temporary.resolve("cancel"), blocking, ignored -> { }, ignored -> { })) {
            java.util.concurrent.CompletableFuture<app.collection.CollectionUpdate.Completed> completed =
                    new java.util.concurrent.CompletableFuture<>();
            var session = service.begin(event -> {
                if (event instanceof app.collection.CollectionUpdate.Completed value) completed.complete(value);
            });
            session.respond(new app.collection.CollectionUpdate.Continue());
            assertTrue(entered.await(2, TimeUnit.SECONDS));

            session.cancel();

            assertFalse(completed.get(2, TimeUnit.SECONDS).updated());
            for (int attempt = 0; attempt < 20 && !interrupted.get(); attempt++) Thread.sleep(10);
            assertTrue(interrupted.get());
            assertEquals(Map.of(1001L, -1), service.getCopiesOwned(List.of(1001L)));
        }
    }

    @Test
    void firstRunRequestsCardsThenLaterSessionsReuseVerifiedCards() throws Exception {
        AtomicInteger scans = new AtomicInteger();
        CollectionScanEngine scanner = progress -> {
            scans.incrementAndGet();
            return new CollectionScanEngine.ScanResult(true, Map.of(1001L, 2, 1002L, 4), "ok");
        };
        List<app.collection.CollectionUpdate.Event> events = new CopyOnWriteArrayList<>();
        try (MemoryCollectionService service = new MemoryCollectionService(
                temporary.resolve("guided"), scanner, ignored -> { }, ignored -> { }, ignored -> { },
                () -> List.of(
                        new app.collection.CollectionUpdate.CardOption(1001, "One", "SET", "Set", "1"),
                        new app.collection.CollectionUpdate.CardOption(1002, "Two", "SET", "Set", "2")))) {
            var first = service.begin(events::add);
            first.respond(new app.collection.CollectionUpdate.Continue());
            assertTrue(events.stream().anyMatch(app.collection.CollectionUpdate.CardsRequired.class::isInstance));
            assertEquals(0, scans.get());
            first.respond(new app.collection.CollectionUpdate.VerifiedCards(Map.of(1001L, 2, 1002L, 4)));
            first.respond(new app.collection.CollectionUpdate.Continue());
            for (int attempt = 0; attempt < 100 && events.stream().noneMatch(
                    app.collection.CollectionUpdate.Completed.class::isInstance); attempt++) Thread.sleep(10);
            assertEquals(1, scans.get());
            Thread.sleep(30);

            var second = service.begin(ignored -> { });
            second.respond(new app.collection.CollectionUpdate.Continue());
            for (int attempt = 0; attempt < 100 && scans.get() == 1; attempt++) Thread.sleep(10);
            assertEquals(2, scans.get());
        }
    }
}
