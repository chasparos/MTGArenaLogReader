package app.model.match;

/**
 * Immutable score for Arena's two player seats.
 */
public record MatchScore(int seatOneWins, int seatTwoWins, int draws) {
    public int winsForSeat(int seatId) {
        return switch (seatId) {
            case 1 -> seatOneWins;
            case 2 -> seatTwoWins;
            default -> 0;
        };
    }
}
