package app.deckplanner.candidate;

import app.deckplanner.filter.CatalogFilterIndex;
import app.deckplanner.filter.IndexedCatalogCard;
import app.model.card.MagicCardOrdering;

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
public final class CandidateModel {
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

    public CandidateModel(Collection<String> initial, Persistence persistence) {
        this(initial, persistence, Runnable::run);
    }

    public CandidateModel(Collection<String> initial, Persistence persistence,
                                   java.util.concurrent.Executor persistenceExecutor) {
        this.persistence = Objects.requireNonNull(persistence);
        this.persistenceExecutor = Objects.requireNonNull(persistenceExecutor);
        if (initial != null) {
            for (String identity : initial) addNormalized(identity);
        }
    }

    public static CandidateModel transientModel() {
        return new CandidateModel(List.of(), ignored -> { });
    }

    public static CandidateModel persisted(CandidateRepository repository,
                                                      java.util.concurrent.Executor persistenceExecutor) {
        Objects.requireNonNull(repository);
        return new CandidateModel(repository.load(), repository::replace, persistenceExecutor);
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

    /** Adds candidates at a requested insertion point while preserving the incoming relative order. */
    public void addAt(Collection<String> additions, int insertionIndex) {
        LinkedHashSet<String> incoming = new LinkedHashSet<>();
        if (additions != null) {
            for (String identity : additions) {
                if (identity != null && !identity.isBlank()) incoming.add(identity.strip());
            }
        }
        if (incoming.isEmpty()) return;

        ArrayList<String> reordered = new ArrayList<>(identities);
        reordered.removeIf(incoming::contains);
        int target = Math.max(0, Math.min(reordered.size(), insertionIndex));
        reordered.addAll(target, incoming);
        if (reordered.equals(new ArrayList<>(identities))) return;
        identities.clear();
        identities.addAll(reordered);
        commit();
    }

    public void remove(String identity) {
        if (identity != null && identities.remove(identity)) commit();
    }

    public void remove(Collection<String> removals) {
        if (removals == null || removals.isEmpty()) return;
        boolean changed = false;
        for (String identity : removals) {
            if (identity != null) changed |= identities.remove(identity);
        }
        if (changed) commit();
    }

    public void clear() {
        if (identities.isEmpty()) return;
        identities.clear();
        commit();
    }

    /** Replaces the complete ordered membership, used when loading a named Candidate Set. */
    public void replace(Collection<String> replacement) {
        LinkedHashSet<String> next = new LinkedHashSet<>();
        if (replacement != null) {
            for (String identity : replacement) {
                if (identity != null && !identity.isBlank()) next.add(identity.strip());
            }
        }
        if (List.copyOf(next).equals(identities())) return;
        identities.clear();
        identities.addAll(next);
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

    /** Moves a selected group to an insertion point while preserving its displayed relative order. */
    public void moveManyToIndex(Collection<String> moved, int insertionIndex) {
        LinkedHashSet<String> requested = new LinkedHashSet<>();
        if (moved != null) {
            for (String identity : moved) {
                if (identity != null && identities.contains(identity)) requested.add(identity);
            }
        }
        if (requested.isEmpty()) return;
        ArrayList<String> current = new ArrayList<>(identities);
        ArrayList<String> moving = new ArrayList<>();
        for (String identity : current) if (requested.contains(identity)) moving.add(identity);
        int removedBeforeTarget = 0;
        int bounded = Math.max(0, Math.min(current.size(), insertionIndex));
        for (int index = 0; index < bounded; index++) {
            if (requested.contains(current.get(index))) removedBeforeTarget++;
        }
        current.removeIf(requested::contains);
        int target = Math.max(0, Math.min(current.size(), bounded - removedBeforeTarget));
        current.addAll(target, moving);
        if (current.equals(new ArrayList<>(identities))) return;
        identities.clear();
        identities.addAll(current);
        commit();
    }

    /** Moves an identity to an insertion point in the current ordered set. */
    public void moveToIndex(String identity, int insertionIndex) {
        if (identity == null || !identities.contains(identity)) return;
        ArrayList<String> reordered = new ArrayList<>(identities);
        int from = reordered.indexOf(identity);
        int target = Math.max(0, Math.min(reordered.size(), insertionIndex));
        reordered.remove(from);
        if (from < target) target--;
        target = Math.max(0, Math.min(reordered.size(), target));
        if (target == from) return;
        reordered.add(target, identity);
        identities.clear();
        identities.addAll(reordered);
        commit();
    }

    /**
     * Persists a caller-supplied presentation order while preserving exactly the current membership.
     *
     * <p>Unknown/duplicate identities in the request are ignored and any current identity omitted
     * by the caller is appended. This makes grouped drag/drop robust against a stale UI snapshot
     * without allowing presentation code to add or delete candidates.</p>
     */
    public void reorder(List<String> requestedOrder) {
        LinkedHashSet<String> reordered = new LinkedHashSet<>();
        if (requestedOrder != null) {
            for (String identity : requestedOrder) {
                if (identity != null && identities.contains(identity)) reordered.add(identity);
            }
        }
        for (String identity : identities) reordered.add(identity);
        if (List.copyOf(reordered).equals(identities())) return;
        identities.clear();
        identities.addAll(reordered);
        commit();
    }

    /** Applies the shared conventional MTG ordering while keeping stale identities recoverable. */
    public void sortByMagic(CatalogFilterIndex index) {
        Objects.requireNonNull(index);
        java.util.Map<String, IndexedCatalogCard> byIdentity = index.cards().stream()
                .collect(java.util.stream.Collectors.toMap(
                        card -> card.group().identity(), card -> card, (left, right) -> left));
        ArrayList<String> sorted = new ArrayList<>(identities);
        sorted.sort((left, right) -> {
            IndexedCatalogCard leftCard = byIdentity.get(left);
            IndexedCatalogCard rightCard = byIdentity.get(right);
            if (leftCard == null && rightCard == null) return left.compareToIgnoreCase(right);
            if (leftCard == null) return 1;
            if (rightCard == null) return -1;
            int compared = MagicCardOrdering.normalComparator().compare(
                    leftCard.group().preferredPrinting(), rightCard.group().preferredPrinting());
            return compared != 0 ? compared : left.compareToIgnoreCase(right);
        });
        if (sorted.equals(new ArrayList<>(identities))) return;
        identities.clear();
        identities.addAll(sorted);
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
