package app.deckplanner.catalog;

public interface CardCatalogSource {
    CardCatalogPage firstPage(String normalizedFormat);

    CardCatalogPage nextPage(String cursor);
}
