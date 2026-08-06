package app.deckplanner.catalog;

import app.enrichment.ScryfallClient;
import app.model.card.CardInfo;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class FormatCatalogServiceTest {
    @TempDir Path temporary;

    @Test
    void queryRequiresArenaAndFormatLegality() {
        assertEquals("game:arena legal:standard",
                ScryfallClient.formatCatalogQuery("standard"));
        assertThrows(IllegalArgumentException.class,
                () -> ScryfallClient.formatCatalogQuery("standard OR game:paper"));
    }

    @Test
    void pagesSequentiallyEnrichesAndAtomicallyPublishes() {
        FakeSource source = new FakeSource(
                new CardCatalogPage(List.of(card("a", 1)), "page-2"),
                new CardCatalogPage(List.of(card("b", 2)), null));
        List<String> enriched = new ArrayList<>();
        try (FormatCatalogRepository repository = repository()) {
            FormatCatalogService.Result result = new FormatCatalogService(
                    source, repository, card -> enriched.add(card.getId()))
                    .refresh("Standard", () -> false);

            assertTrue(result.complete());
            assertEquals(List.of("a", "b"), enriched);
            assertEquals(List.of("first:standard", "next:page-2"), source.calls);
            FormatCatalogRepository.Snapshot snapshot =
                    repository.current("standard").orElseThrow();
            assertEquals(2, snapshot.outcomes().size());
            assertTrue(snapshot.outcomes().stream()
                    .allMatch(outcome -> outcome.outcome().equals("SUCCESS")));
        }
    }

    @Test
    void cancelledRunResumesWithoutReplacingCurrentSnapshot() {
        try (FormatCatalogRepository repository = repository()) {
            new FormatCatalogService(
                    new FakeSource(new CardCatalogPage(List.of(card("old", 1)), null)),
                    repository, ignored -> { })
                    .refresh("standard", () -> false);
            String published = repository.current("standard").orElseThrow().id();

            AtomicBoolean cancel = new AtomicBoolean();
            FormatCatalogService interrupted = new FormatCatalogService(
                    new FakeSource(new CardCatalogPage(
                            List.of(card("new-a", 2), card("new-b", 3)), null)),
                    repository, card -> cancel.set(true));
            FormatCatalogService.Result partial = interrupted.refresh(
                    "standard", cancel::get);

            assertFalse(partial.complete());
            assertEquals(published, repository.current("standard").orElseThrow().id());

            FakeSource resumedSource = new FakeSource(
                    new CardCatalogPage(List.of(card("new-a", 2), card("new-b", 3)), null));
            FormatCatalogService.Result resumed = new FormatCatalogService(
                    resumedSource, repository, ignored -> { })
                    .refresh("standard", () -> false);
            assertTrue(resumed.complete());
            assertEquals(partial.runId(), resumed.runId());
            assertEquals(2, repository.current("standard").orElseThrow()
                    .outcomes().size());
        }
    }

    @Test
    void enrichmentFailureIsRecordedPerCardAndDoesNotAbortCatalog() {
        try (FormatCatalogRepository repository = repository()) {
            FormatCatalogService.Result result = new FormatCatalogService(
                    new FakeSource(new CardCatalogPage(
                            List.of(card("good", 1), card("bad", 2)), null)),
                    repository,
                    card -> { if (card.getId().equals("bad")) throw new IllegalStateException("boom"); })
                    .refresh("historic", () -> false);
            assertTrue(result.complete());
            List<FormatCatalogRepository.CardOutcome> outcomes =
                    repository.current("historic").orElseThrow().outcomes();
            assertEquals(List.of("SUCCESS", "FAILED"),
                    outcomes.stream().map(FormatCatalogRepository.CardOutcome::outcome).toList());
            assertTrue(outcomes.get(1).error().contains("boom"));
        }
    }

    @Test
    void retriesTransientPageFailureWithExponentialDelay() {
        CardCatalogPage page = new CardCatalogPage(List.of(card("eventual", 1)), null);
        List<Long> delays = new ArrayList<>();
        List<FormatCatalogProgress.Phase> phases = new ArrayList<>();
        CardCatalogSource flaky = new CardCatalogSource() {
            int attempts;
            @Override public CardCatalogPage firstPage(String format) {
                if (++attempts < 3) throw new IllegalStateException("temporary");
                return page;
            }
            @Override public CardCatalogPage nextPage(String cursor) { throw new AssertionError(); }
        };
        try (FormatCatalogRepository repository = repository()) {
            FormatCatalogService.Result result = new FormatCatalogService(
                    flaky, repository, ignored -> { },
                    new FormatCatalogService.RetryPolicy(3, Duration.ofMillis(10)),
                    delays::add,
                    progress -> phases.add(progress.phase()))
                    .refresh("standard", () -> false);
            assertTrue(result.complete());
            assertEquals(List.of(10L, 20L), delays);
            assertEquals(2, phases.stream()
                    .filter(phase -> phase == FormatCatalogProgress.Phase.RETRYING).count());
        }
    }

    @Test
    void alternatePrintingsShareLogicalIdentityButRetainPrintingFacts() {
        CardInfo older = card("printing-a", 10);
        older.setOracleId("oracle-one");
        older.setReleasedAt("2024-01-01");
        CardInfo newer = card("printing-b", 20);
        newer.setOracleId("oracle-one");
        newer.setReleasedAt("2025-01-01");
        try (FormatCatalogRepository repository = repository()) {
            new FormatCatalogService(
                    new FakeSource(new CardCatalogPage(List.of(older, newer), null)),
                    repository, ignored -> { })
                    .refresh("standard", () -> false);
            List<FormatCatalogRepository.CardGroup> groups =
                    repository.current("standard").orElseThrow().cardGroups();
            assertEquals(1, groups.size());
            assertEquals("oracle:oracle-one", groups.getFirst().identity());
            assertEquals(2, groups.getFirst().printings().size());
            assertEquals("printing-b", groups.getFirst().preferredPrinting().getId());
            assertEquals(List.of(10L, 20L), groups.getFirst().printings().stream()
                    .map(CardInfo::getArenaId).toList());
        }
    }

    @Test
    void nonRetryableSourceFailureStopsImmediately() {
        List<Long> delays = new ArrayList<>();
        CardCatalogSource source = new CardCatalogSource() {
            int calls;
            @Override public CardCatalogPage firstPage(String format) {
                calls++;
                throw new CardCatalogSourceException("bad query", false);
            }
            @Override public CardCatalogPage nextPage(String cursor) { throw new AssertionError(); }
        };
        try (FormatCatalogRepository repository = repository()) {
            assertThrows(CardCatalogSourceException.class, () ->
                    new FormatCatalogService(source, repository, ignored -> { },
                            new FormatCatalogService.RetryPolicy(3, Duration.ofMillis(10)),
                            delays::add, FormatCatalogProgress.Listener.ignoring())
                            .refresh("standard", () -> false));
            assertTrue(delays.isEmpty());
        }
    }

    @Test
    void payloadMustConfirmArenaAvailabilityAndFormatLegality() {
        CardInfo invalid = card("paper-only", 9);
        invalid.setGames(List.of("paper"));
        try (FormatCatalogRepository repository = repository()) {
            new FormatCatalogService(
                    new FakeSource(new CardCatalogPage(List.of(invalid), null)),
                    repository, ignored -> { })
                    .refresh("standard", () -> false);
            FormatCatalogRepository.Snapshot snapshot =
                    repository.current("standard").orElseThrow();
            assertEquals("FAILED", snapshot.outcomes().getFirst().outcome());
            assertTrue(snapshot.cardGroups().isEmpty());
        }
    }

    private FormatCatalogRepository repository() {
        return new FormatCatalogRepository(new Gson(), temporary.resolve("catalog"));
    }

    private static CardInfo card(String id, long arenaId) {
        CardInfo card = new CardInfo();
        card.setId(id);
        card.setArenaId(arenaId);
        card.setName("Card " + id);
        card.setGames(List.of("arena"));
        card.setLegalities(java.util.Map.of(
                "standard", "legal", "historic", "legal"));
        return card;
    }

    private static final class FakeSource implements CardCatalogSource {
        private final List<CardCatalogPage> pages;
        private final List<String> calls = new ArrayList<>();
        private int next;

        private FakeSource(CardCatalogPage... pages) {
            this.pages = List.of(pages);
        }

        @Override public CardCatalogPage firstPage(String format) {
            calls.add("first:" + format);
            return pages.get(next++);
        }

        @Override public CardCatalogPage nextPage(String cursor) {
            calls.add("next:" + cursor);
            return pages.get(next++);
        }
    }
}
