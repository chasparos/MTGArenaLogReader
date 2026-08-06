package app.deckplanner.filter;

public record ManaValueRange(double minimum, double maximum) {
    public ManaValueRange {
        if (Double.isNaN(minimum) || Double.isNaN(maximum) || minimum > maximum) {
            throw new IllegalArgumentException("Invalid mana-value range");
        }
    }
    public boolean contains(double value) { return value >= minimum && value <= maximum; }
}
