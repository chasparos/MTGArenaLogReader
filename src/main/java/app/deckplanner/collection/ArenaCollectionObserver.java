package app.deckplanner.collection;

import app.model.log.RawLogEntry;

import java.util.Objects;
import java.util.function.Consumer;

public final class ArenaCollectionObserver implements Consumer<RawLogEntry> {
    private final ArenaCollectionLogParser parser;
    private final ArenaCollectionRepository repository;

    public ArenaCollectionObserver(ArenaCollectionLogParser parser,
                                   ArenaCollectionRepository repository) {
        this.parser = Objects.requireNonNull(parser);
        this.repository = Objects.requireNonNull(repository);
    }

    @Override public void accept(RawLogEntry raw) {
        parser.parseComplete(raw).ifPresent(repository::replaceComplete);
    }
}
