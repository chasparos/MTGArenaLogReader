package app.deckplanner.catalog;

import app.model.card.CardInfo;

import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Sequential, cancellable coordinator for a resumable format catalog refresh. */
public final class FormatCatalogService {
    private final CardCatalogSource source;
    private final FormatCatalogRepository repository;
    private final Consumer<CardInfo> enrichmentStep;
    private final RetryPolicy retryPolicy;
    private final Sleeper sleeper;
    private final FormatCatalogProgress.Listener progress;

    public FormatCatalogService(CardCatalogSource source,
                                FormatCatalogRepository repository,
                                Consumer<CardInfo> enrichmentStep) {
        this(source, repository, enrichmentStep,
                new RetryPolicy(3, Duration.ofMillis(250)),
                Thread::sleep, FormatCatalogProgress.Listener.ignoring());
    }

    public FormatCatalogService(CardCatalogSource source,
                                FormatCatalogRepository repository,
                                Consumer<CardInfo> enrichmentStep,
                                RetryPolicy retryPolicy,
                                Sleeper sleeper,
                                FormatCatalogProgress.Listener progress) {
        this.source = Objects.requireNonNull(source);
        this.repository = Objects.requireNonNull(repository);
        this.enrichmentStep = Objects.requireNonNull(enrichmentStep);
        this.retryPolicy = Objects.requireNonNull(retryPolicy);
        this.sleeper = Objects.requireNonNull(sleeper);
        this.progress = Objects.requireNonNull(progress);
    }

    public Result refresh(String format, BooleanSupplier cancelled) {
        Objects.requireNonNull(cancelled);
        FormatCatalogRepository.Run run = repository.beginOrResume(format);
        emit(run, FormatCatalogProgress.Phase.STARTING, 0, 1, "resume=" + (run.stagedCards() > 0));
        String cursor = run.nextCursor();
        int processed = 0;
        do {
            if (cancelled.getAsBoolean()) return cancelled(run, processed);
            String requestedCursor = cursor;
            emit(run, FormatCatalogProgress.Phase.FETCHING_PAGE, processed, 1,
                    requestedCursor == null ? "first" : "next");
            CardCatalogPage page = fetchWithRetry(run, processed, cancelled,
                    () -> requestedCursor == null
                            ? source.firstPage(run.format())
                            : source.nextPage(requestedCursor));
            if (page == null) return cancelled(run, processed);
            for (CardInfo card : page.cards()) {
                if (cancelled.getAsBoolean()) return cancelled(run, processed);
                emit(run, FormatCatalogProgress.Phase.ENRICHING, processed, 1,
                        safeIdentity(card));
                try {
                    CatalogCardValidator.requireEligible(card, run.format());
                    enrichmentStep.accept(card);
                    repository.stageSuccess(run.id(), card);
                } catch (RuntimeException error) {
                    repository.stageFailure(run.id(), card, error);
                }
                processed++;
            }
            cursor = page.nextCursor();
            repository.checkpoint(run.id(), cursor);
        } while (cursor != null);
        emit(run, FormatCatalogProgress.Phase.PUBLISHING, processed, 1, "atomic publish");
        repository.publish(run.id());
        emit(run, FormatCatalogProgress.Phase.COMPLETE, processed, 1, "published");
        return new Result(true, processed, run.id());
    }

    private CardCatalogPage fetchWithRetry(FormatCatalogRepository.Run run,
                                           int processed,
                                           BooleanSupplier cancelled,
                                           Supplier<CardCatalogPage> request) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= retryPolicy.maximumAttempts(); attempt++) {
            if (cancelled.getAsBoolean()) return null;
            try {
                return request.get();
            } catch (RuntimeException error) {
                last = error;
                if (error instanceof CardCatalogSourceException sourceError
                        && !sourceError.retryable()) throw sourceError;
                if (attempt == retryPolicy.maximumAttempts()) break;
                long delay = retryPolicy.delayMillis(attempt);
                emit(run, FormatCatalogProgress.Phase.RETRYING, processed, attempt + 1,
                        error.getClass().getSimpleName() + "; delayMs=" + delay);
                try {
                    sleeper.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Catalog retry interrupted", interrupted);
                }
            }
        }
        throw last == null ? new IllegalStateException("Catalog page request failed") : last;
    }

    private Result cancelled(FormatCatalogRepository.Run run, int processed) {
        emit(run, FormatCatalogProgress.Phase.CANCELLED, processed, 1, "staging retained");
        return new Result(false, processed, run.id());
    }

    private void emit(FormatCatalogRepository.Run run,
                      FormatCatalogProgress.Phase phase,
                      int processed, int attempt, String detail) {
        progress.onProgress(new FormatCatalogProgress(
                run.format(), run.id(), phase, processed, attempt, detail));
    }

    private String safeIdentity(CardInfo card) {
        try { return CatalogCardIdentity.of(card); }
        catch (RuntimeException ignored) { return "unidentified"; }
    }

    public record Result(boolean complete, int processedCards, String runId) { }

    public record RetryPolicy(int maximumAttempts, Duration initialDelay) {
        public RetryPolicy {
            if (maximumAttempts < 1) throw new IllegalArgumentException("maximumAttempts < 1");
            Objects.requireNonNull(initialDelay);
            if (initialDelay.isNegative()) throw new IllegalArgumentException("initialDelay is negative");
        }

        long delayMillis(int failedAttempt) {
            long multiplier = 1L << Math.min(20, Math.max(0, failedAttempt - 1));
            return Math.multiplyExact(initialDelay.toMillis(), multiplier);
        }
    }

    @FunctionalInterface
    public interface Sleeper { void sleep(long milliseconds) throws InterruptedException; }
}
