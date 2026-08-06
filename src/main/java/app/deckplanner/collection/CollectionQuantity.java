package app.deckplanner.collection;

public record CollectionQuantity(int copies) {
    public static final int UNKNOWN = -1;

    public CollectionQuantity {
        if (copies < UNKNOWN) throw new IllegalArgumentException("copies < -1");
    }

    public boolean known() { return copies >= 0; }
    public boolean owned() { return copies > 0; }
}
