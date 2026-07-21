package app.enrichment;

import app.model.card.CardInfo;
import com.google.gson.Gson;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.Unirest;
import kong.unirest.core.UnirestInstance;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/** Small client for Scryfall card lookups, including Arena variant aliases.
 * <p><strong>Architectural role:</strong> This type belongs to the optional enrichment boundary; external metadata may supplement but never replace Arena-observed truth.</p>
 */
public final class ScryfallClient implements AutoCloseable {
    private final Gson gson;
    private final UnirestInstance unirest;
    private final CardAliasRegistry aliases = new CardAliasRegistry();

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

        HttpResponse<String> response = unirest.get("https://api.scryfall.com/cards/arena/{id}")
                .routeParam("id", Long.toString(arenaId))
                .asString();

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

        HttpResponse<String> response = unirest.get("https://api.scryfall.com/cards/named")
                .queryString("exact", exactName)
                .asString();

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

        HttpResponse<String> response = unirest.get("https://api.scryfall.com/cards/{id}")
                .routeParam("id", scryfallId)
                .asString();

        if (response.getStatus() == 404) return Optional.empty();
        if (response.getStatus() < 200 || response.getStatus() >= 300) {
            throw new IllegalStateException(
                    "Scryfall HTTP " + response.getStatus() + " for id=" + scryfallId);
        }

        return parseCard(response, "scryfallId=" + scryfallId, null);
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
