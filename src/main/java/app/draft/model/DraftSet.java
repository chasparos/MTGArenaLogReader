package app.draft.model;

import java.time.LocalDate;

public record DraftSet(
        String code,
        String name,
        LocalDate releasedAt,
        String setType,
        boolean digital) {
    public DraftSet {
        code = code == null ? "" : code.toLowerCase();
        name = name == null ? code : name;
        setType = setType == null ? "" : setType;
    }

    public String displayName() {
        return name + " (" + code.toUpperCase() + ")";
    }

    @Override
    public String toString() {
        return displayName();
    }
}
