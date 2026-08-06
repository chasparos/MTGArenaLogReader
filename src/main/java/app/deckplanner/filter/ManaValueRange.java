package app.deckplanner.filter;

public record ManaValueRange(double minimum, double maximum) {
    public ManaValueRange {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum)
                || minimum < 0d || maximum < 0d || minimum > maximum) {
            throw new IllegalArgumentException("Invalid mana-value range");
        }
    }
    public boolean contains(double value) { return value >= minimum && value <= maximum; }
}
