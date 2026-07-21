package app.enrichment;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Resolves Arena-only cosmetic/variant grpIds that are not indexed by
 * Scryfall's /cards/arena/:id endpoint.
 *
 * User overrides can be placed in:
 *   ~/.arena-log-viewer/card-aliases.properties
 *
 * Each value is either another Arena ID:
 *   100673=12345
 *
 * or an exact Scryfall card name:
 *   100673=Doubling Season
 * <p><strong>Architectural role:</strong> This type belongs to the optional enrichment boundary; external metadata may supplement but never replace Arena-observed truth.</p>
 */
public final class CardAliasRegistry {
    private static final Map<Long, Alias> BUILT_INS = Map.of(
            100673L, Alias.exactName("Doubling Season")
    );

    private final Map<Long, Alias> aliases = new LinkedHashMap<>();

    public CardAliasRegistry() {
        aliases.putAll(BUILT_INS);
        loadUserAliases(Path.of(
                System.getProperty("user.home"),
                ".arena-log-viewer",
                "card-aliases.properties"
        ));
    }

    public Optional<Alias> find(long arenaId) {
        return Optional.ofNullable(aliases.get(arenaId));
    }

    private void loadUserAliases(Path path) {
        if (!Files.isRegularFile(path)) return;

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            properties.load(reader);
        } catch (IOException error) {
            System.err.println("[CardAlias] Could not read " + path + ": " + error.getMessage());
            return;
        }

        for (String key : properties.stringPropertyNames()) {
            try {
                long sourceId = Long.parseLong(key.trim());
                String value = properties.getProperty(key, "").trim();
                if (value.isEmpty()) continue;

                try {
                    aliases.put(sourceId, Alias.arenaId(Long.parseLong(value)));
                } catch (NumberFormatException notAnId) {
                    aliases.put(sourceId, Alias.exactName(value));
                }
            } catch (NumberFormatException invalidKey) {
                System.err.println("[CardAlias] Ignoring invalid Arena ID: " + key);
            }
        }
    }

    public record Alias(Long targetArenaId, String exactName) {
        public static Alias arenaId(long arenaId) {
            return new Alias(arenaId, null);
        }

        public static Alias exactName(String name) {
            return new Alias(null, name);
        }
    }
}
