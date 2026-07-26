package app.settings;

public enum ThemeMode {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    private final String label;

    ThemeMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
