package app.deck.persistence;


import app.deck.model.CachedDeck;
import app.deck.model.DeckEntry;
import app.model.card.CardInfo;
import app.enrichment.CardCache;
import com.google.gson.Gson;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * Represents or implements DeckCache in the optional live deck-tracking subsystem.
 *
 * <p>The deck subsystem consumes routed Arena observations alongside cached deck metadata while remaining separate from replay reconstruction.</p>
 *
 * <p>It must not become a second source of canonical game state for the replay pipeline.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the deck-tracker persistence boundary and does not own replay state.</p>
 */
public final class DeckCache implements AutoCloseable {
    private final Gson gson;
    private final CardCache cardCache;
    private final Connection connection;

    public DeckCache(Gson gson, CardCache cardCache, Path databasePath) {
        this.gson = gson;
        this.cardCache = cardCache;
        try {
            Path absolute = databasePath.toAbsolutePath();
            if (absolute.getParent() != null) Files.createDirectories(absolute.getParent());
            connection = DriverManager.getConnection(
                    "jdbc:h2:file:" + absolute.toString().replace('\\','/') + ";DB_CLOSE_ON_EXIT=FALSE", "sa", "");
            initialize();
        } catch (Exception e) {
            throw new IllegalStateException("Could not initialize deck cache", e);
        }
    }

    public synchronized void put(CachedDeck deck) {
        String sql = """
            MERGE INTO arena_deck_cache
            (deck_id, deck_name, format_name, event_name, deck_json, updated_at)
            KEY(deck_id) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;
        try (PreparedStatement s = connection.prepareStatement(sql)) {
            s.setString(1, deck.deckId());
            s.setString(2, deck.name());
            s.setString(3, deck.format());
            s.setString(4, deck.eventName());
            s.setString(5, gson.toJson(deck));
            s.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not cache deck " + deck.deckId(), e);
        }
    }

    public synchronized Optional<CachedDeck> find(String deckId) {
        if (deckId == null || deckId.isBlank()) return Optional.empty();
        try (PreparedStatement s = connection.prepareStatement(
                "SELECT deck_json FROM arena_deck_cache WHERE deck_id=?")) {
            s.setString(1, deckId);
            try (ResultSet r=s.executeQuery()) {
                return r.next() ? Optional.of(enrich(gson.fromJson(r.getString(1), CachedDeck.class))) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read deck cache", e);
        }
    }

    public synchronized Optional<CachedDeck> mostRecentForEvent(String eventName) {
        String sql = eventName == null || eventName.isBlank()
                ? "SELECT deck_json FROM arena_deck_cache ORDER BY updated_at DESC LIMIT 1"
                : "SELECT deck_json FROM arena_deck_cache WHERE event_name=? ORDER BY updated_at DESC LIMIT 1";
        try (PreparedStatement s=connection.prepareStatement(sql)) {
            if (sql.contains("WHERE")) s.setString(1,eventName);
            try (ResultSet r=s.executeQuery()) {
                return r.next() ? Optional.of(enrich(gson.fromJson(r.getString(1), CachedDeck.class))) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not choose cached deck", e);
        }
    }

    private CachedDeck enrich(CachedDeck d) {
        return new CachedDeck(d.deckId(), d.name(), d.format(), d.eventName(), d.updatedAt(),
                enrich(d.mainDeck()), enrich(d.sideboard()), enrich(d.commandZone()), enrich(d.companions()));
    }

    private List<DeckEntry> enrich(List<DeckEntry> entries) {
        if (entries == null) return List.of();
        List<DeckEntry> out=new ArrayList<>();
        for (DeckEntry e:entries) {
            CardInfo card=e.card();
            if (card==null) card=cardCache.find(e.arenaId()).flatMap(CardCache.CachedCard::card).orElse(null);
            out.add(new DeckEntry(e.arenaId(),e.quantity(),card));
        }
        return List.copyOf(out);
    }

    private void initialize() throws SQLException {
        try (Statement s=connection.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS arena_deck_cache (
                    deck_id VARCHAR PRIMARY KEY,
                    deck_name VARCHAR,
                    format_name VARCHAR,
                    event_name VARCHAR,
                    deck_json CLOB NOT NULL,
                    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
        }
    }

    @Override public synchronized void close() {
        try { connection.close(); } catch (SQLException e) { throw new IllegalStateException(e); }
    }
}
