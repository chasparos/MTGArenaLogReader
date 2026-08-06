package app.deckplanner.filter;

public enum CardColor { WHITE("W"), BLUE("U"), BLACK("B"), RED("R"), GREEN("G");
    private final String symbol;
    CardColor(String symbol) { this.symbol = symbol; }
    public String symbol() { return symbol; }
}
