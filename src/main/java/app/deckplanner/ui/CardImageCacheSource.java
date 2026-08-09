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
        return requestFace(card, 0);
    }
    @Override
    public CompletableFuture<Optional<BufferedImage>> requestFace(
            CardBrowserPanel.BrowserCard card, int faceIndex) {
        if (card == null || faceIndex < 0) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        boolean trace = isImageTraceCard(card.name());
        Optional<CardInfo> resolved;
        String resolutionSource;
        try {
            if (card.card() != null) {
                resolved = Optional.of(card.card());
                resolutionSource = "BrowserCard.card";
            } else {
                resolved = cardResolver.apply(card.identity());
                resolutionSource = "identity-resolver";
            }
        } catch (RuntimeException error) {
            if (trace) {
                System.err.println("[CardImageTrace] source name=" + card.name()
                        + " identity=" + card.identity()
                        + " face=" + faceIndex
                        + " resolution=ERROR " + error);
            }
            return CompletableFuture.failedFuture(error);
        }
        if (trace) {
            CardInfo info = resolved == null ? null : resolved.orElse(null);
            System.err.println("[CardImageTrace] source name=" + card.name()
                    + " identity=" + card.identity()
                    + " face=" + faceIndex
                    + " resolution=" + resolutionSource
                    + " resolved=" + (info != null)
                    + " id=" + (info == null ? "<null>" : info.getId())
                    + " arenaId=" + (info == null ? "<null>" : info.getArenaId())
                    + " set=" + (info == null ? "<null>" : info.getSet())
                    + " collector=" + (info == null ? "<null>" : info.getCollectorNumber())
                    + " urls=" + (info == null ? java.util.List.of() : info.previewImageUrls()));
        }
        if (resolved == null || resolved.isEmpty()) {
            if (trace) {
                System.err.println("[CardImageTrace] source FALLBACK name=" + card.name()
                        + " reason=no resolved CardInfo");
            }
            return CompletableFuture.completedFuture(Optional.empty());
        }
        CompletableFuture<Optional<BufferedImage>> future = loader.apply(resolved.get(), faceIndex);
        if (future == null) {
            if (trace) {
                System.err.println("[CardImageTrace] source FALLBACK name=" + card.name()
                        + " reason=image loader returned null future");
            }
            return CompletableFuture.completedFuture(Optional.empty());
        }
        if (!trace) return future;
        return future.whenComplete((image, error) -> {
            if (error != null) {
                System.err.println("[CardImageTrace] source FALLBACK name=" + card.name()
                        + " reason=image loader failed error=" + error);
            } else if (image == null || image.isEmpty()) {
                System.err.println("[CardImageTrace] source FALLBACK name=" + card.name()
                        + " reason=image loader returned empty");
            } else {
                BufferedImage loaded = image.get();
                System.err.println("[CardImageTrace] source SUCCESS name=" + card.name()
                        + " image=" + loaded.getWidth() + "x" + loaded.getHeight());
            }
        });
    }

    private static boolean isImageTraceCard(String name) {
        return "Marketback Walker".equalsIgnoreCase(name)
                || "Agent Maria Hill".equalsIgnoreCase(name);
    }
}
