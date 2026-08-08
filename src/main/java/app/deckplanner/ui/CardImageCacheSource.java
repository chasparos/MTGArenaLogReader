package app.deckplanner.ui;

import app.enrichment.CardImageCache;
import app.model.card.CardInfo;

import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Bridges stable browser identities to the shared asynchronous card-image cache. */
public final class CardImageCacheSource implements CardBrowserPanel.ImageSource {
    private final Function<String, Optional<CardInfo>> cardResolver;
    private final BiFunction<CardInfo, Integer, CompletableFuture<Optional<BufferedImage>>> loader;

    public CardImageCacheSource(CardImageCache imageCache,
                                Function<String, Optional<CardInfo>> cardResolver) {
        this(cardResolver, Objects.requireNonNull(imageCache)::get);
    }

    CardImageCacheSource(Function<String, Optional<CardInfo>> cardResolver,
                         BiFunction<CardInfo, Integer, CompletableFuture<Optional<BufferedImage>>> loader) {
        this.cardResolver = Objects.requireNonNull(cardResolver);
        this.loader = Objects.requireNonNull(loader);
    }

    @Override
    public CompletableFuture<Optional<BufferedImage>> request(CardBrowserPanel.BrowserCard card) {
        if (card == null) return CompletableFuture.completedFuture(Optional.empty());
        Optional<CardInfo> resolved;
        try {
            resolved = card.card() != null
                    ? Optional.of(card.card())
                    : cardResolver.apply(card.identity());
        } catch (RuntimeException error) {
            return CompletableFuture.failedFuture(error);
        }
        if (resolved == null || resolved.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        CompletableFuture<Optional<BufferedImage>> future = loader.apply(resolved.get(), 0);
        return future == null ? CompletableFuture.completedFuture(Optional.empty()) : future;
    }
    @Override
    public CompletableFuture<Optional<BufferedImage>> requestFace(
            CardBrowserPanel.BrowserCard card, int faceIndex) {
        if (card == null || faceIndex < 0) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Optional<CardInfo> resolved;
        try {
            resolved = card.card() != null
                    ? Optional.of(card.card())
                    : cardResolver.apply(card.identity());
        } catch (RuntimeException error) {
            return CompletableFuture.failedFuture(error);
        }
        if (resolved == null || resolved.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        CompletableFuture<Optional<BufferedImage>> future = loader.apply(resolved.get(), faceIndex);
        return future == null ? CompletableFuture.completedFuture(Optional.empty()) : future;
    }
}
