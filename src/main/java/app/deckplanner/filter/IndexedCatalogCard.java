package app.deckplanner.filter;

import app.deckplanner.catalog.FormatCatalogRepository;
import java.util.Set;

public record IndexedCatalogCard(FormatCatalogRepository.CardGroup group,
                                 Set<CardColor> colors, Set<CardColor> colorIdentity,
                                 Set<BaseCardType> baseTypes, double manaValue,
                                 Set<SemanticTag> tags) { }
