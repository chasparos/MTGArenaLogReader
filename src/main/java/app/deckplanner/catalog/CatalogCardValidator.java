package app.deckplanner.catalog;

import app.model.card.CardInfo;

public final class CatalogCardValidator {
    private CatalogCardValidator() { }

    public static void requireEligible(CardInfo card, String normalizedFormat) {
        CatalogCardIdentity.of(card);
        boolean arena = card.getGames() != null && card.getGames().stream()
                .anyMatch(game -> "arena".equalsIgnoreCase(game));
        if (!arena) throw new IllegalArgumentException("Card is not available in Arena");
        String legality = card.getLegalities() == null
                ? null : card.getLegalities().get(normalizedFormat);
        if (!"legal".equalsIgnoreCase(legality)) {
            throw new IllegalArgumentException(
                    "Card is not legal in format " + normalizedFormat);
        }
    }
}
