package app.enrichment;

import app.draft.model.DraftSet;
import app.deckplanner.catalog.CardCatalogPage;
import app.deckplanner.catalog.CardCatalogSource;
import app.deckplanner.catalog.CardCatalogSourceException;
import app.model.card.CardInfo;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.Unirest;
import kong.unirest.core.UnirestInstance;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** Small client for Scryfall card lookups, including Arena variant aliases.
 * <p><strong>Architectural role:</strong> This type belongs to the optional enrichment boundary; external metadata may supplement but never replace Arena-observed truth.</p>
 */
public final class ScryfallClient implements AutoCloseable, CardCatalogSource {
    private static final Duration MINIMUM_REQUEST_SPACING = Duration.ofMillis(110);
    private static final int MAX_RATE_LIMIT_ATTEMPTS = 5;

    private final Gson gson;
    private final UnirestInstance unirest;
    private final CardAliasRegistry aliases = new CardAliasRegistry();
    private final Object requestLock = new Object();
    private long nextRequestNanos;

    public ScryfallClient(Gson gson) {
        this.gson = gson;
        this.unirest = Unirest.spawnInstance();
        this.unirest.config()
                .connectTimeout(5_000)
                .requestTimeout(10_000)
                .setDefaultHeader("Accept", "application/json")
                .setDefaultHeader("User-Agent", "ArenaLogViewer/0.2 (personal desktop application)");
    }

    public Optional<CardInfo> findByArenaId(long arenaId) {
        return findByArenaId(arenaId, new HashSet<>());
    }

    private Optional<CardInfo> findByArenaId(long arenaId, Set<Long> visited) {
        if (!visited.add(arenaId)) {
            System.err.println("[CardAlias] Alias cycle detected for arenaId=" + arenaId);
            return Optional.empty();
        }

        HttpResponse<String> response = request(() ->
                unirest.get("https://api.scryfall.com/cards/arena/{id}")
                        .routeParam("id", Long.toString(arenaId))
                        .asString(), "arenaId=" + arenaId);

        if (response.getStatus() == 404) {
            Optional<CardAliasRegistry.Alias> alias = aliases.find(arenaId);
            if (alias.isEmpty()) {
                System.out.println("[Scryfall] No card for arenaId=" + arenaId);
                return Optional.empty();
            }

            CardAliasRegistry.Alias target = alias.get();
            Optional<CardInfo> resolved;
            if (target.targetArenaId() != null) {
                System.out.println("[CardAlias] arenaId=" + arenaId
                        + " -> arenaId=" + target.targetArenaId());
                resolved = findByArenaId(target.targetArenaId(), visited);
            } else {
                System.out.println("[CardAlias] arenaId=" + arenaId
                        + " -> exact name=\"" + target.exactName() + "\"");
                resolved = findByExactName(target.exactName());
            }

            resolved.ifPresent(card -> System.out.println(
                    "[CardAlias] Resolved arenaId=" + arenaId + " as " + card.getName()));
            return resolved;
        }

        if (response.getStatus() < 200 || response.getStatus() >= 300) {
            throw new IllegalStateException(
                    "Scryfall HTTP " + response.getStatus() + " for arenaId=" + arenaId);
        }

        return parseCard(response, "arenaId=" + arenaId, arenaId);
    }

    public Optional<CardInfo> findByExactName(String exactName) {
        if (exactName == null || exactName.isBlank()) return Optional.empty();

        HttpResponse<String> response = request(() ->
                unirest.get("https://api.scryfall.com/cards/named")
                        .queryString("exact", exactName)
                        .asString(), "exactName=\"" + exactName + "\"");

        if (response.getStatus() == 404) {
            System.out.println("[Scryfall] No exact-name card for \"" + exactName + "\"");
            return Optional.empty();
        }
        if (response.getStatus() < 200 || response.getStatus() >= 300) {
            throw new IllegalStateException(
                    "Scryfall HTTP " + response.getStatus() + " for exact name=" + exactName);
        }

        return parseCard(response, "exactName=\"" + exactName + "\"", null);
    }

    public Optional<CardInfo> findByScryfallId(String scryfallId) {
        if (scryfallId == null || scryfallId.isBlank()) return Optional.empty();

        HttpResponse<String> response = request(() ->
                unirest.get("https://api.scryfall.com/cards/{id}")
                        .routeParam("id", scryfallId)
                        .asString(), "scryfallId=" + scryfallId);

        if (response.getStatus() == 404) return Optional.empty();
        if (response.getStatus() < 200 || response.getStatus() >= 300) {
            throw new IllegalStateException(
                    "Scryfall HTTP " + response.getStatus() + " for id=" + scryfallId);
        }

        return parseCard(response, "scryfallId=" + scryfallId, null);
    }

    public List<DraftSet> listSets() {
        HttpResponse<String> response = request(() -> unirest
                .get("https://api.scryfall.com/sets")
                .asString(), "set catalog");
        requireSuccess(response, "set catalog");
        JsonObject root = JsonParser.parseString(response.getBody()).getAsJsonObject();
        List<DraftSet> result = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("data")) {
            if (!element.isJsonObject()) continue;
            JsonObject set = element.getAsJsonObject();
            String code = string(set, "code");
            String name = string(set, "name");
            if (code.isBlank() || name.isBlank()) continue;
            result.add(new DraftSet(
                    code,
                    name,
                    date(set, "released_at"),
                    string(set, "set_type"),
                    bool(set, "digital")));
        }
        result.sort(Comparator
                .comparing(DraftSet::releasedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(DraftSet::name));
        return List.copyOf(result);
    }

    /**
     * Returns every Arena printing in a set, following Scryfall pagination.
     */
    public List<CardInfo> findArenaCardsInSet(String setCode) {
        if (setCode == null || setCode.isBlank()) return List.of();
        String nextPage = null;
        List<CardInfo> result = new ArrayList<>();
        do {
            String pageUrl = nextPage;
            HttpResponse<String> response = request(() -> pageUrl == null
                    ? unirest.get("https://api.scryfall.com/cards/search")
                    .queryString("q", "e:" + setCode.strip().toLowerCase() + " game:arena")
                    .queryString("unique", "prints")
                    .queryString("order", "set")
                    .asString()
                    : unirest.get(pageUrl).asString(), "cards for set=" + setCode);
            requireSuccess(response, "cards for set=" + setCode);
            JsonObject page = JsonParser.parseString(response.getBody()).getAsJsonObject();
            for (JsonElement element : page.getAsJsonArray("data")) {
                if (!element.isJsonObject()) continue;
                CardInfo card = gson.fromJson(element, CardInfo.class);
                if (card != null) result.add(card);
            }
            nextPage = bool(page, "has_more") ? string(page, "next_page") : null;
        } while (nextPage != null && !nextPage.isBlank());
        return List.copyOf(result);
    }

    @Override
    public CardCatalogPage firstPage(String normalizedFormat) {
        if (normalizedFormat == null
                || !normalizedFormat.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException(
                    "Invalid normalized Scryfall format: " + normalizedFormat);
        }
        HttpResponse<String> response = request(() -> unirest
                .get("https://api.scryfall.com/cards/search")
                .queryString("q", formatCatalogQuery(normalizedFormat))
                .queryString("unique", "prints")
                .queryString("order", "set")
                .asString(), "format=" + normalizedFormat);
        return parseCatalogPage(response, "format=" + normalizedFormat);
    }

    @Override
    public CardCatalogPage nextPage(String cursor) {
        if (cursor == null || cursor.isBlank()
                || !cursor.startsWith("https://api.scryfall.com/")) {
            throw new IllegalArgumentException("Invalid Scryfall page cursor");
        }
        return parseCatalogPage(
                request(() -> unirest.get(cursor).asString(), "catalog page"),
                "catalog page");
    }

    public static String formatCatalogQuery(String normalizedFormat) {
        if (normalizedFormat == null
                || !normalizedFormat.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException(
                    "Invalid normalized Scryfall format: " + normalizedFormat);
        }
        return "game:arena legal:" + normalizedFormat;
    }

    private CardCatalogPage parseCatalogPage(
            HttpResponse<String> response, String lookup) {
        int status = response.getStatus();
        if (status < 200 || status >= 300) {
            throw new CardCatalogSourceException(
                    "Scryfall HTTP " + status + " for " + lookup,
                    status == 429 || status >= 500);
        }
        JsonObject page = JsonParser.parseString(response.getBody()).getAsJsonObject();
        List<CardInfo> cards = new ArrayList<>();
        for (JsonElement element : page.getAsJsonArray("data")) {
            if (!element.isJsonObject()) continue;
            CardInfo card = gson.fromJson(element, CardInfo.class);
            if (card != null) cards.add(card);
        }
        String cursor = bool(page, "has_more") ? string(page, "next_page") : null;
        return new CardCatalogPage(cards, cursor);
    }

    private HttpResponse<String> request(
            Supplier<HttpResponse<String>> operation, String lookup) {
        synchronized (requestLock) {
            HttpResponse<String> response = null;
            for (int attempt = 0; attempt < MAX_RATE_LIMIT_ATTEMPTS; attempt++) {
                throttle();
                response = operation.get();
                if (response.getStatus() != 429) return response;

                String retryAfter = response.getHeaders().getFirst("Retry-After");
                Duration delay = retryDelay(retryAfter, attempt, Instant.now());
                System.err.println("[Scryfall] HTTP 429 for " + lookup
                        + "; retrying after " + delay.toMillis() + " ms");
                sleep(delay, "rate-limit backoff");
            }
            return response;
        }
    }

    static Duration retryDelay(String retryAfter, int attempt, Instant now) {
        Duration fallback = Duration.ofMillis(
                Math.min(8_000L, 500L << Math.min(Math.max(0, attempt), 4)));
        if (retryAfter == null || retryAfter.isBlank()) return fallback;
        String value = retryAfter.strip();
        try {
            long seconds = Long.parseLong(value);
            return Duration.ofSeconds(Math.max(0L, seconds));
        } catch (NumberFormatException ignored) {
            try {
                Instant requested = ZonedDateTime.parse(
                        value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                Duration delay = Duration.between(now, requested);
                return delay.isNegative() ? Duration.ZERO : delay;
            } catch (DateTimeParseException invalidDate) {
                return fallback;
            }
        }
    }

    private void throttle() {
        long waitNanos = nextRequestNanos - System.nanoTime();
        if (waitNanos > 0) {
            sleep(Duration.ofNanos(waitNanos), "request throttle");
        }
        nextRequestNanos = System.nanoTime() + MINIMUM_REQUEST_SPACING.toNanos();
    }

    private void sleep(Duration duration, String purpose) {
        if (duration == null || duration.isZero() || duration.isNegative()) return;
        long nanos = duration.toNanos();
        try {
            Thread.sleep(nanos / 1_000_000L, (int) (nanos % 1_000_000L));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted during Scryfall " + purpose, interrupted);
        }
    }

    private void requireSuccess(HttpResponse<String> response, String lookup) {
        if (response.getStatus() < 200 || response.getStatus() >= 300) {
            throw new IllegalStateException(
                    "Scryfall HTTP " + response.getStatus() + " for " + lookup);
        }
    }

    private String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private boolean bool(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && !value.isJsonNull() && value.getAsBoolean();
    }

    private LocalDate date(JsonObject object, String key) {
        String value = string(object, key);
        return value.isBlank() ? null : LocalDate.parse(value);
    }

    private Optional<CardInfo> parseCard(
            HttpResponse<String> response,
            String lookup,
            Long requestedArenaId
    ) {
        CardInfo card = gson.fromJson(response.getBody(), CardInfo.class);
        if (card == null) {
            throw new IllegalStateException("Scryfall returned an empty card for " + lookup);
        }
        if (card.getArenaId() == null && requestedArenaId != null) {
            card.setArenaId(requestedArenaId);
        }

        System.out.println("[Scryfall] " + lookup
                + " name=" + card.getName()
                + " scryfallId=" + card.getId()
                + " image=" + card.previewImageUrl()
                + " faces=" + (card.getCardFaces() == null ? 0 : card.getCardFaces().size()));
        return Optional.of(card);
    }

    @Override
    public void close() {
        unirest.close();
    }
}
