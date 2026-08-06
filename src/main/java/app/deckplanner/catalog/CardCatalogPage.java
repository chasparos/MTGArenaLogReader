package app.deckplanner.catalog;

import app.model.card.CardInfo;

import java.util.List;

public record CardCatalogPage(List<CardInfo> cards, String nextCursor) {
    public CardCatalogPage {
        cards = cards == null ? List.of() : List.copyOf(cards);
        nextCursor = nextCursor == null || nextCursor.isBlank() ? null : nextCursor;
    }

    public boolean hasMore() {
        return nextCursor != null;
    }
}
