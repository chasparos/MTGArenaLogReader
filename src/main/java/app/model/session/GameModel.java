package app.model.session;


import app.model.card.CardInfo;
import app.model.event.GameEvent;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
/**
 * Defines GameModel within the app.model.session package.
 *
 * <p>It participates in the application's established processing architecture and keeps its responsibility within this package boundary.</p>
 *
 * <p>Callers should use it through its public API rather than duplicating its behavior in adjacent layers.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the per-game session model that exposes projected events to presentation and export layers.</p>
 */
public class GameModel {
    private String matchId;
    private int gameNumber;
    private String openingHandPlayer;
    private int mulliganCount;
    private final List<CardInfo> openingHand = new ArrayList<>();
    private final List<GameEvent> events = new ArrayList<>();
    private final List<String> rawRecords = new ArrayList<>();

    public synchronized void addEvents(List<GameEvent> additions) { events.addAll(additions); }
    public synchronized void addRawRecord(String raw) {
        if (raw != null && !raw.isBlank()) rawRecords.add(raw);
    }
    public synchronized List<String> rawRecordSnapshot() { return List.copyOf(rawRecords); }
    public synchronized List<GameEvent> snapshot() { return List.copyOf(events); }
    public synchronized void setOpeningHand(String player, int mulligans, List<CardInfo> cards) {
        openingHandPlayer = player;
        mulliganCount = mulligans;
        openingHand.clear();
        openingHand.addAll(cards);
    }
    public synchronized List<CardInfo> openingHandSnapshot() { return List.copyOf(openingHand); }
    public synchronized boolean isComplete() {
        return events.stream().anyMatch(e -> e.getGameResult() != null || "Game completed".equals(e.getText()));
    }
    public synchronized void clear() { events.clear(); rawRecords.clear(); openingHand.clear(); openingHandPlayer = null; mulliganCount = 0; }
}
