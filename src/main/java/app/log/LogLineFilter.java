package app.log;

import java.util.List;

/**
 * Provides the LogLineFilter part of the Player.log ingestion pipeline.
 *
 * <p>It sits before enrichment and game projection, converting the mixed log stream into ordered records or coordinating the threads that do so.</p>
 *
 * <p>It must not mutate canonical game state or interpret gameplay semantics.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the Player.log ingestion boundary before enrichment, routing, and canonical game-state projection.</p>
 */
public final class LogLineFilter {
    private static final List<String> MARKERS = List.of(
            "DETAILED LOGS:",
            "Connecting to matchId",
            "matchGameRoomStateChangedEvent",
            "greToClientEvent",
            "ClientToGremessage",
            "ClientToGREMessage",
            "ClientMessageType_",
            "GameStateMessage",
            "MatchGameRoomStateType_",
            "Draft.Notify",
            "EventPlayerDraftMakePick",
            "EventSetDeckV3",
            "PlayerInventory.GetPlayerCardsV3",
            "ResultReason_"
    );

    private LogLineFilter() {
    }

    public static boolean isInteresting(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        if (line.stripLeading().startsWith("{")) {
            return true;
        }
        return MARKERS.stream().anyMatch(line::contains);
    }
}
