package app.draft.model;

public record DraftCardCount(long arenaId, int quantity) {
    public DraftCardCount {
        if (arenaId <= 0) throw new IllegalArgumentException("arenaId must be positive");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
    }
}
