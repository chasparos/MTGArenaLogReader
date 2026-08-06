package app.deckplanner.ui;

import app.model.card.CardInfo;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CardImageCacheSourceTest {
    @Test
    void resolvesStableIdentityAndDelegatesToSharedCacheBoundary() {
        CardInfo info = new CardInfo();
        info.setId("scryfall-a");
        AtomicInteger imageIndex = new AtomicInteger(-1);
        BufferedImage image = new BufferedImage(63, 88, BufferedImage.TYPE_INT_RGB);
        CardImageCacheSource source = new CardImageCacheSource(
                identity -> identity.equals("a") ? Optional.of(info) : Optional.empty(),
                (card, index) -> {
                    assertSame(info, card);
                    imageIndex.set(index);
                    return CompletableFuture.completedFuture(Optional.of(image));
                });

        assertSame(image, source.request(new CardBrowserPanel.BrowserCard("a", "Alpha")).join().orElseThrow());
        assertEquals(0, imageIndex.get());
        assertTrue(source.request(new CardBrowserPanel.BrowserCard("missing", "Missing")).join().isEmpty());
    }

    @Test
    void resolverFailuresRemainAsynchronousAndObservable() {
        CardImageCacheSource source = new CardImageCacheSource(
                ignored -> { throw new IllegalStateException("resolver failed"); },
                (card, index) -> CompletableFuture.completedFuture(Optional.empty()));

        CompletableFuture<Optional<BufferedImage>> future =
                source.request(new CardBrowserPanel.BrowserCard("a", "Alpha"));
        assertTrue(future.isCompletedExceptionally());
        assertThrows(java.util.concurrent.CompletionException.class, future::join);
    }
}
