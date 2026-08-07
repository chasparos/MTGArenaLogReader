package app.deckplanner.consideration;

import app.deckplanner.filter.CatalogFilterIndex;
import app.deckplanner.filter.IndexedCatalogCard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Authoritative ordered candidate set for DP-06.
 *
 * <p>Membership is keyed by the catalog's logical identity, so alternate printings that share an
 * oracle identity are one candidate. Resolution is deliberately separate from persistence: a
 * missing catalog identity remains in the workspace as a recoverable stale entry.</p>
 */
public final class UnderConsiderationModel {
    @FunctionalInterface
    public interface Persistence {
        void save(List<String> identities);
    }

    @FunctionalInterface
    public interface Listener {
        void changed(List<String> identities);
    }

    public record Entry(String identity, Optional<IndexedCatalogCard> card) {
        public Entry {
            Objects.requireNonNull(identity);
            card = card == null ? Optional.empty() : card;
        }
        public boolean stale() { return card.isEmpty(); }
    }

    private final LinkedHashSet<String> identities = new LinkedHashSet<>();
    private final Persistence persistence;
    private final java.util.concurrent.Executor persistenceExecutor;
    private java.util.concurrent.CompletableFuture<Void> persistenceTail =
            java.util.concurrent.CompletableFuture.completedFuture(null);
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public UnderConsiderationModel(Collection<String> initial, Persistence persistence) {
        this(initial, persistence, Runnable::run);
    }

    public UnderConsiderationModel(Collection<String> initial, Persistence persistence,
                                   java.util.concurrent.Executor persistenceExecutor) {
        this.persistence = Objects.requireNonNull(persistence);
        this.persistenceExecutor = Objects.requireNonNull(persistenceExecutor);
        if (initial != null) {
            for (String identity : initial) addNormalized(identity);
        }
    }

    public static UnderConsiderationModel transientModel() {
        return new UnderConsiderationModel(List.of(), ignored -> { });
    }

    public static UnderConsiderationModel persisted(UnderConsiderationRepository repository,
                                                      java.util.concurrent.Executor persistenceExecutor) {
        Objects.requireNonNull(repository);
        return new UnderConsiderationModel(repository.load(), repository::replace, persistenceExecutor);
    }

    public List<String> identities() {
        return List.copyOf(identities);
    }

    public void add(Collection<String> additions) {
        boolean changed = false;
        if (additions != null) {
            for (String identity : additions) changed |= addNormalized(identity);
        }
        if (changed) commit();
    }

    public void remove(String identity) {
        if (identity != null && identities.remove(identity)) commit();
    }

    public void clear() {
        if (identities.isEmpty()) return;
        identities.clear();
        commit();
    }

    public void move(String identity, int delta) {
        if (identity == null || delta == 0 || !identities.contains(identity)) return;
        ArrayList<String> reordered = new ArrayList<>(identities);
        int from = reordered.indexOf(identity);
        int to = Math.max(0, Math.min(reordered.size() - 1, from + Integer.signum(delta)));
        if (from == to) return;
        reordered.remove(from);
        reordered.add(to, identity);
        identities.clear();
        identities.addAll(reordered);
        commit();
    }

    public List<Entry> resolve(CatalogFilterIndex index) {
        Objects.requireNonNull(index);
        java.util.Map<String, IndexedCatalogCard> byIdentity = index.cards().stream()
                .collect(java.util.stream.Collectors.toMap(
                        card -> card.group().identity(), card -> card, (left, right) -> left,
                        java.util.LinkedHashMap::new));
        return identities.stream()
                .map(identity -> new Entry(identity, Optional.ofNullable(byIdentity.get(identity))))
                .toList();
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private boolean addNormalized(String identity) {
        return identity != null && !identity.isBlank() && identities.add(identity.strip());
    }

    private void commit() {
        List<String> snapshot = identities();
        schedulePersistence(snapshot);
        for (Listener listener : listeners) listener.changed(snapshot);
    }

    private synchronized void schedulePersistence(List<String> snapshot) {
        persistenceTail = persistenceTail.handle((ignored, failure) -> null)
                .thenRunAsync(() -> persistence.save(snapshot), persistenceExecutor);
    }
}
