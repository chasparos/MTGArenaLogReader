package app.draft.model;

public enum DraftTier {
    S, A, B, C, D, F;

    public static DraftTier parse(String value) {
        return value == null ? null : valueOf(value.strip().toUpperCase());
    }
}
