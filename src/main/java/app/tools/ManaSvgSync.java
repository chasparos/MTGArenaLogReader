package app.tools;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Developer utility that copies Andrew Gioia's individual Mana SVG sources into
 * this project. It is intentionally not run during a normal build, keeping
 * application startup and Maven builds deterministic/offline.
 *
 * Run from the project root:
 *   mvn -q -Dexec.mainClass=app.tools.ManaSvgSync exec:java
 * <p><strong>Architectural role:</strong> This type is a development-time utility outside the runtime replay-processing pipeline.</p>
 */
public final class ManaSvgSync {
    private static final String RAW =
            "https://raw.githubusercontent.com/andrewgioia/mana/master/svg/";

    private static final Set<String> MANA = new LinkedHashSet<>();
    private static final Set<String> KEYWORDS = Set.of(
            "deathtouch", "defender", "doublestrike", "firststrike", "flying",
            "haste", "hexproof", "indestructible", "lifelink", "menace",
            "reach", "trample", "vigilance", "ward"
    );

    static {
        for (int value = 0; value <= 20; value++) MANA.add(Integer.toString(value));
        MANA.addAll(Set.of(
                "w", "u", "b", "r", "g", "c", "x", "y", "z", "s", "e", "t", "q",
                "w-u", "w-b", "u-b", "u-r", "b-r", "b-g", "r-g", "r-w", "g-w", "g-u",
                "2-w", "2-u", "2-b", "2-r", "2-g",
                "w-p", "u-p", "b-p", "r-p", "g-p", "c-p"
        ));
    }

    private ManaSvgSync() {}

    public static void main(String[] args) throws Exception {
        Path resources = Path.of("src", "main", "resources");
        Path manaDir = resources.resolve("mana-svg");
        Path keywordDir = resources.resolve("keyword-svg");
        Files.createDirectories(manaDir);
        Files.createDirectories(keywordDir);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        int copied = 0;
        for (String sourceName : MANA) {
            String resourceName = sourceName.toUpperCase(Locale.ROOT).replace('-', '_') + ".svg";
            if (download(client, sourceName + ".svg", manaDir.resolve(resourceName))) copied++;
        }
        for (String keyword : KEYWORDS) {
            String sourceName = "ability-" + keyword + ".svg";
            if (download(client, sourceName, keywordDir.resolve(sourceName))) copied++;
        }
        System.out.println("Synced " + copied + " Mana SVG assets into " + resources.toAbsolutePath());
    }

    private static boolean download(HttpClient client, String sourceName, Path target)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(RAW + sourceName))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "arena-log-viewer-svg-sync")
                .GET()
                .build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() == 404) {
            System.err.println("Not present upstream: " + sourceName);
            return false;
        }
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Failed " + sourceName + ": HTTP " + response.statusCode());
        }
        Files.write(target, response.body());
        System.out.println("  " + target);
        return true;
    }
}
