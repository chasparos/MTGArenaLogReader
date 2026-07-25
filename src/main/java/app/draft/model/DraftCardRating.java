package app.draft.model;

public record DraftCardRating(
        long arenaId,
        String cardName,
        DraftTier tier,
        String note) {
    public DraftCardRating {
        cardName = cardName == null ? "" : cardName.strip();
        if (tier == null) throw new IllegalArgumentException("tier is required");
        note = note == null ? "" : note.strip();
    }
}
