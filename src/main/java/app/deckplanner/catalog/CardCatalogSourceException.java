package app.deckplanner.catalog;

public final class CardCatalogSourceException extends RuntimeException {
    private final boolean retryable;

    public CardCatalogSourceException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public boolean retryable() { return retryable; }
}
