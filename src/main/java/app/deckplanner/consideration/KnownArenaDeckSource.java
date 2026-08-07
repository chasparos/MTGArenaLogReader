package app.deckplanner.consideration;

import java.util.List;

@FunctionalInterface
public interface KnownArenaDeckSource {
    List<KnownArenaDeck> list();

    static KnownArenaDeckSource empty() {
        return List::of;
    }
}
