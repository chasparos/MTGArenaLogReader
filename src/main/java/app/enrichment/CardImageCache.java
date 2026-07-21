package app.enrichment;

import app.model.card.CardInfo;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Asynchronous memory + disk cache for Scryfall preview images.
 * <p><strong>Architectural role:</strong> This type belongs to the optional enrichment boundary; external metadata may supplement but never replace Arena-observed truth.</p>
 */
public final class CardImageCache {
    private static final String PREFIX = "[CardImageCache] ";

    private final Path directory;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ConcurrentMap<String, CompletableFuture<Optional<BufferedImage>>> memory = new ConcurrentHashMap<>();

    public CardImageCache(Path directory) {
        this.directory = directory;
        System.out.println(PREFIX + "directory=" + directory.toAbsolutePath());
    }

    public CompletableFuture<Optional<BufferedImage>> get(CardInfo card) {
        String url = card == null ? null : card.previewImageUrl();
        if (card == null) {
            System.out.println(PREFIX + "card is null");
            return CompletableFuture.completedFuture(Optional.empty());
        }
        if (url == null || url.isBlank()) {
            System.out.println(PREFIX + "no URL: name=" + card.getName()
                    + " scryfallId=" + card.getId() + " arenaId=" + card.getArenaId());
            return CompletableFuture.completedFuture(Optional.empty());
        }

        String id = card.getId() != null && !card.getId().isBlank()
                ? card.getId()
                : card.getArenaId() != null ? String.valueOf(card.getArenaId())
                : Integer.toHexString(url.hashCode());

        return memory.computeIfAbsent(id, ignored -> CompletableFuture.supplyAsync(() -> load(id, url)))
                .whenComplete((image, error) -> {
                    // A transient network/decode failure should be retryable on the next hover.
                    if (error != null || image == null || image.isEmpty()) memory.remove(id);
                });
    }

    private Optional<BufferedImage> load(String id, String url) {
        try {
            Files.createDirectories(directory);
            Path file = directory.resolve(id.replaceAll("[^A-Za-z0-9._-]", "_") + ".jpg");

            if (Files.exists(file)) {
                BufferedImage cached = ImageIO.read(file.toFile());
                if (cached != null) {
                    System.out.println(PREFIX + "disk hit: " + file.getFileName());
                    return Optional.of(cached);
                }
                System.out.println(PREFIX + "deleting undecodable cache file: " + file);
                Files.deleteIfExists(file);
            }

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "ArenaLogViewer/0.2 (personal desktop application)")
                    .header("Accept", "image/jpeg,image/png,*/*")
                    .GET().build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            String contentType = response.headers().firstValue("Content-Type").orElse("<missing>");
            int bytes = response.body() == null ? 0 : response.body().length;
            System.out.println(PREFIX + "HTTP " + response.statusCode() + " type=" + contentType
                    + " bytes=" + bytes + " url=" + response.uri());

            if (response.statusCode() / 100 != 2 || response.body() == null || response.body().length == 0) {
                return Optional.empty();
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
            if (image == null) {
                System.out.println(PREFIX + "ImageIO could not decode response for " + id);
                return Optional.empty();
            }
            Files.write(file, response.body());
            System.out.println(PREFIX + "cached " + image.getWidth() + "x" + image.getHeight()
                    + " at " + file.toAbsolutePath());
            return Optional.of(image);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            System.err.println(PREFIX + "interrupted: " + url);
            return Optional.empty();
        } catch (IOException | IllegalArgumentException error) {
            System.err.println(PREFIX + "failed id=" + id + " url=" + url + ": " + error);
            error.printStackTrace(System.err);
            return Optional.empty();
        }
    }
}
