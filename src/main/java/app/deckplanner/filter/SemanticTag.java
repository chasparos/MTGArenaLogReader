package app.deckplanner.filter;

public record SemanticTag(TagCategory category, String key, String label) implements Comparable<SemanticTag> {
    public SemanticTag {
        if (category == null || key == null || key.isBlank() || label == null || label.isBlank()) {
            throw new IllegalArgumentException("Tag fields are required");
        }
    }
    @Override public int compareTo(SemanticTag other) {
        int categoryOrder = category.compareTo(other.category);
        return categoryOrder != 0 ? categoryOrder : key.compareTo(other.key);
    }
}
