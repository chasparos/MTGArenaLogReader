package app.deckplanner.catalog;

import app.model.card.CardInfo;

/** Stable logical identity used to group alternate Arena/Scryfall printings. */
public final class CatalogCardIdentity {
    private CatalogCardIdentity() { }

    public static String of(CardInfo card) {
        if (card == null) throw new IllegalArgumentException("Card is null");
        if (hasText(card.getOracleId())) return "oracle:" + card.getOracleId().strip().toLowerCase();
        if (hasText(card.getId())) return "scryfall:" + card.getId().strip().toLowerCase();
        if (card.getArenaId() != null && card.getArenaId() > 0) {
            return "arena:" + card.getArenaId();
        }
        throw new IllegalArgumentException("Card has no stable catalog identity");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
