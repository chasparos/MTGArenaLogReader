package app.deckplanner.candidate;

import app.deckplanner.filter.IndexedCatalogCard;
import app.model.card.CardInfo;

import java.util.*;
import java.util.function.Consumer;

/** Planner-owned category state independent from ordered candidate membership. */
public final class CandidateWorkspaceState {
    public static final String CREATURES = "creatures";
    public static final String NONCREATURES = "noncreatures";
    public static final String NONBASIC_LANDS = "nonbasic-lands";
    public static final String UNCATEGORIZED = "uncategorized";
    public static final String UNAVAILABLE = "unavailable";

    public record Category(String id, String name) {
        public Category {
            id = normalizeId(id);
            name = name == null || name.isBlank() ? "Category" : name.strip();
        }
    }

    public record Snapshot(List<Category> categories, Map<String, String> assignments) {
        public Snapshot {
            categories = List.copyOf(categories == null ? List.of() : categories);
            assignments = Map.copyOf(assignments == null ? Map.of() : assignments);
        }
    }

    private final List<Category> categories = new ArrayList<>();
    private final LinkedHashMap<String, String> assignments = new LinkedHashMap<>();
    private final Consumer<Snapshot> persistence;
    private final List<Runnable> listeners = new ArrayList<>();

    public CandidateWorkspaceState(Snapshot initial, Consumer<Snapshot> persistence) {
        this.persistence = Objects.requireNonNull(persistence);
        Snapshot source = initial == null ? defaults() : initial;
        categories.addAll(source.categories());
        assignments.putAll(source.assignments());
        if (categories.isEmpty()) categories.addAll(defaults().categories());
    }

    public static CandidateWorkspaceState transientState() {
        return new CandidateWorkspaceState(defaults(), ignored -> { });
    }

    public static Snapshot defaults() {
        return new Snapshot(List.of(
                new Category(CREATURES, "Creatures"),
                new Category(NONCREATURES, "Noncreatures"),
                new Category(NONBASIC_LANDS, "Nonbasic Lands")), Map.of());
    }

    public List<Category> categories() { return List.copyOf(categories); }

    public Map<String, String> assignments() { return Map.copyOf(assignments); }

    public String categoryFor(CandidateModel.Entry entry) {
        Objects.requireNonNull(entry);
        String explicit = assignments.get(entry.identity());
        if (explicit != null) return explicit;
        return defaultCategory(entry);
    }

    public void synchronize(Collection<CandidateModel.Entry> entries) {
        Set<String> live = new HashSet<>();
        if (entries != null) {
            for (CandidateModel.Entry entry : entries) live.add(entry.identity());
        }
        boolean changed = assignments.keySet().removeIf(identity -> !live.contains(identity));
        Set<String> usedCategories = new HashSet<>(assignments.values());
        changed |= categories.removeIf(category -> !defaultCategoryId(category.id())
                && !usedCategories.contains(category.id()));
        if (changed) commit();
    }

    public Category addCategory(String name) {
        String base = normalizeId(name);
        if (base.isBlank() || reserved(base)) base = "category";
        String id = base;
        int suffix = 2;
        Set<String> used = categories.stream().map(Category::id).collect(java.util.stream.Collectors.toSet());
        while (used.contains(id)) id = base + "-" + suffix++;
        Category created = new Category(id, name);
        categories.add(created);
        commit();
        return created;
    }

    public void removeCategory(String id, Collection<CandidateModel.Entry> entries) {
        if (id == null || UNAVAILABLE.equals(id) || UNCATEGORIZED.equals(id)) return;
        List<String> affected = new ArrayList<>();
        if (entries != null) {
            for (CandidateModel.Entry entry : entries) {
                if (id.equals(categoryFor(entry))) affected.add(entry.identity());
            }
        }
        boolean removed = categories.removeIf(category -> category.id().equals(id));
        if (!removed) return;
        for (String identity : affected) assignments.put(identity, UNCATEGORIZED);
        assignments.replaceAll((identity, assigned) -> id.equals(assigned) ? UNCATEGORIZED : assigned);
        commit();
    }

    public void moveCategory(String id, int delta) {
        if (id == null || delta == 0) return;
        int from = -1;
        for (int i = 0; i < categories.size(); i++) if (categories.get(i).id().equals(id)) from = i;
        if (from < 0) return;
        int to = Math.max(0, Math.min(categories.size() - 1, from + Integer.signum(delta)));
        if (from == to) return;
        Category moved = categories.remove(from);
        categories.add(to, moved);
        commit();
    }


    public void moveCategoryBefore(String sourceId, String targetId) {
        moveCategoryRelative(sourceId, targetId, false);
    }

    public void moveCategoryRelative(String sourceId, String targetId, boolean afterTarget) {
        if (sourceId == null || targetId == null || sourceId.equals(targetId)) return;
        int from = -1;
        int target = -1;
        for (int i = 0; i < categories.size(); i++) {
            String id = categories.get(i).id();
            if (id.equals(sourceId)) from = i;
            if (id.equals(targetId)) target = i;
        }
        if (from < 0 || target < 0) return;
        Category moved = categories.remove(from);
        if (from < target) target--;
        int insertion = target + (afterTarget ? 1 : 0);
        categories.add(Math.max(0, Math.min(insertion, categories.size())), moved);
        commit();
    }

    public void assign(Collection<String> identities, String categoryId) {
        if (identities == null || categoryId == null) return;
        String normalized = normalizeId(categoryId);
        if (!UNCATEGORIZED.equals(normalized)
                && categories.stream().noneMatch(category -> category.id().equals(normalized))) return;
        boolean changed = false;
        for (String identity : identities) {
            if (identity == null || identity.isBlank()) continue;
            String previous = assignments.put(identity, normalized);
            changed |= !Objects.equals(previous, normalized);
        }
        if (changed) commit();
    }

    public Snapshot snapshot() {
        return new Snapshot(categories, assignments);
    }

    public void replace(Snapshot snapshot) {
        Snapshot next = snapshot == null ? defaults() : snapshot;
        categories.clear();
        categories.addAll(next.categories().isEmpty() ? defaults().categories() : next.categories());
        assignments.clear();
        assignments.putAll(next.assignments());
        commit();
    }

    public void addListener(Runnable listener) { listeners.add(Objects.requireNonNull(listener)); }

    private String defaultCategory(CandidateModel.Entry entry) {
        if (entry.stale()) return UNAVAILABLE;
        CardInfo card = entry.resolvedCard().orElseThrow();
        String typeLine = Optional.ofNullable(card.effectiveTypeLine()).orElse("").toLowerCase(Locale.ROOT);
        String candidate = typeLine.contains("creature") ? CREATURES
                : typeLine.contains("land") && !typeLine.contains("basic land") ? NONBASIC_LANDS
                : NONCREATURES;
        return categories.stream().anyMatch(category -> category.id().equals(candidate))
                ? candidate : UNCATEGORIZED;
    }

    private void commit() {
        Snapshot snapshot = snapshot();
        persistence.accept(snapshot);
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }

    private static boolean defaultCategoryId(String id) {
        return CREATURES.equals(id) || NONCREATURES.equals(id) || NONBASIC_LANDS.equals(id);
    }

    private static boolean reserved(String id) {
        return UNCATEGORIZED.equals(id) || UNAVAILABLE.equals(id);
    }

    private static String normalizeId(String value) {
        if (value == null) return "";
        String normalized = value.strip().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return normalized;
    }
}
