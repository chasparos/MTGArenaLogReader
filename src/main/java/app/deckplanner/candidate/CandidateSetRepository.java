package app.deckplanner.candidate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/** Persists candidate categories and named Candidate Sets beside candidate membership. */
public final class CandidateSetRepository implements AutoCloseable {
    public record CandidateSet(String name, List<String> identities,
                               CandidateWorkspaceState.Snapshot workspace) {
        public CandidateSet {
            name = name == null ? "" : name.strip();
            identities = List.copyOf(identities == null ? List.of() : identities);
            workspace = workspace == null ? CandidateWorkspaceState.defaults() : workspace;
        }
        @Override public String toString() { return name; }
    }

    private final Connection connection;

    public CandidateSetRepository(Path databasePath) {
        try {
            Path absolute = databasePath.toAbsolutePath();
            if (absolute.getParent() != null) Files.createDirectories(absolute.getParent());
            connection = DriverManager.getConnection(
                    "jdbc:h2:file:" + absolute.toString().replace('\\', '/')
                            + ";DB_CLOSE_ON_EXIT=FALSE", "sa", "");
            initializeSchema();
        } catch (Exception error) {
            throw new IllegalStateException("Could not initialize Candidate Set repository", error);
        }
    }

    public synchronized CandidateWorkspaceState.Snapshot loadWorkspace() {
        try {
            List<CandidateWorkspaceState.Category> categories = new ArrayList<>();
            try (PreparedStatement st = connection.prepareStatement("""
                    SELECT category_id, category_name FROM deck_planner_candidate_categories
                    ORDER BY position_no""");
                 ResultSet rows = st.executeQuery()) {
                while (rows.next()) categories.add(
                        new CandidateWorkspaceState.Category(rows.getString(1), rows.getString(2)));
            }
            Map<String,String> assignments = new LinkedHashMap<>();
            try (PreparedStatement st = connection.prepareStatement("""
                    SELECT card_identity, category_id FROM deck_planner_candidate_assignments
                    ORDER BY card_identity""");
                 ResultSet rows = st.executeQuery()) {
                while (rows.next()) assignments.put(rows.getString(1), rows.getString(2));
            }
            return categories.isEmpty()
                    ? CandidateWorkspaceState.defaults()
                    : new CandidateWorkspaceState.Snapshot(categories, assignments);
        } catch (SQLException error) {
            throw new IllegalStateException("Could not read candidate category state", error);
        }
    }

    public synchronized void replaceWorkspace(CandidateWorkspaceState.Snapshot snapshot) {
        Objects.requireNonNull(snapshot);
        try {
            connection.setAutoCommit(false);
            try (Statement clear = connection.createStatement()) {
                clear.executeUpdate("DELETE FROM deck_planner_candidate_categories");
                clear.executeUpdate("DELETE FROM deck_planner_candidate_assignments");
            }
            insertWorkspace(snapshot);
            connection.commit();
        } catch (SQLException error) {
            rollback();
            throw new IllegalStateException("Could not persist candidate category state", error);
        } finally { autoCommit(); }
    }

    public synchronized List<CandidateSet> list() {
        try (PreparedStatement st = connection.prepareStatement("""
                SELECT set_name FROM deck_planner_candidate_sets ORDER BY set_name""");
             ResultSet rows = st.executeQuery()) {
            List<CandidateSet> sets = new ArrayList<>();
            while (rows.next()) sets.add(load(rows.getString(1)).orElseThrow());
            return List.copyOf(sets);
        } catch (SQLException error) {
            throw new IllegalStateException("Could not list Candidate Sets", error);
        }
    }

    public synchronized Optional<CandidateSet> load(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        try {
            boolean exists;
            try (PreparedStatement st = connection.prepareStatement(
                    "SELECT 1 FROM deck_planner_candidate_sets WHERE set_name=?")) {
                st.setString(1, name.strip());
                try (ResultSet row = st.executeQuery()) { exists = row.next(); }
            }
            if (!exists) return Optional.empty();
            List<String> identities = new ArrayList<>();
            try (PreparedStatement st = connection.prepareStatement("""
                    SELECT card_identity FROM deck_planner_candidate_set_cards
                    WHERE set_name=? ORDER BY position_no""")) {
                st.setString(1, name.strip());
                try (ResultSet rows = st.executeQuery()) {
                    while (rows.next()) identities.add(rows.getString(1));
                }
            }
            List<CandidateWorkspaceState.Category> categories = new ArrayList<>();
            try (PreparedStatement st = connection.prepareStatement("""
                    SELECT category_id, category_name FROM deck_planner_candidate_set_categories
                    WHERE set_name=? ORDER BY position_no""")) {
                st.setString(1, name.strip());
                try (ResultSet rows = st.executeQuery()) {
                    while (rows.next()) categories.add(
                            new CandidateWorkspaceState.Category(rows.getString(1), rows.getString(2)));
                }
            }
            Map<String,String> assignments = new LinkedHashMap<>();
            try (PreparedStatement st = connection.prepareStatement("""
                    SELECT card_identity, category_id FROM deck_planner_candidate_set_assignments
                    WHERE set_name=? ORDER BY card_identity""")) {
                st.setString(1, name.strip());
                try (ResultSet rows = st.executeQuery()) {
                    while (rows.next()) assignments.put(rows.getString(1), rows.getString(2));
                }
            }
            CandidateWorkspaceState.Snapshot workspace = categories.isEmpty()
                    ? CandidateWorkspaceState.defaults()
                    : new CandidateWorkspaceState.Snapshot(categories, assignments);
            return Optional.of(new CandidateSet(name.strip(), identities, workspace));
        } catch (SQLException error) {
            throw new IllegalStateException("Could not load Candidate Set", error);
        }
    }

    public synchronized void save(String name, List<String> identities,
                                  CandidateWorkspaceState.Snapshot workspace) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Candidate Set name is required");
        String setName = name.strip();
        try {
            connection.setAutoCommit(false);
            deleteSetChildren(setName);
            try (PreparedStatement upsert = connection.prepareStatement("""
                    MERGE INTO deck_planner_candidate_sets (set_name) KEY(set_name) VALUES (?)""")) {
                upsert.setString(1, setName); upsert.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO deck_planner_candidate_set_cards(set_name, position_no, card_identity)
                    VALUES (?, ?, ?)""")) {
                LinkedHashSet<String> unique = new LinkedHashSet<>(identities == null ? List.of() : identities);
                int index = 0;
                for (String identity : unique) {
                    insert.setString(1, setName); insert.setInt(2, index++);
                    insert.setString(3, identity); insert.addBatch();
                }
                insert.executeBatch();
            }
            insertSetWorkspace(setName, workspace == null ? CandidateWorkspaceState.defaults() : workspace);
            connection.commit();
        } catch (SQLException error) {
            rollback();
            throw new IllegalStateException("Could not save Candidate Set", error);
        } finally { autoCommit(); }
    }

    private void initializeSchema() throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS deck_planner_candidate_categories(position_no INT PRIMARY KEY, category_id VARCHAR(128) UNIQUE NOT NULL, category_name VARCHAR(256) NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS deck_planner_candidate_assignments(card_identity VARCHAR(256) PRIMARY KEY, category_id VARCHAR(128) NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS deck_planner_candidate_sets(set_name VARCHAR(256) PRIMARY KEY)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS deck_planner_candidate_set_cards(set_name VARCHAR(256) NOT NULL, position_no INT NOT NULL, card_identity VARCHAR(256) NOT NULL, PRIMARY KEY(set_name, position_no))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS deck_planner_candidate_set_categories(set_name VARCHAR(256) NOT NULL, position_no INT NOT NULL, category_id VARCHAR(128) NOT NULL, category_name VARCHAR(256) NOT NULL, PRIMARY KEY(set_name, position_no))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS deck_planner_candidate_set_assignments(set_name VARCHAR(256) NOT NULL, card_identity VARCHAR(256) NOT NULL, category_id VARCHAR(128) NOT NULL, PRIMARY KEY(set_name, card_identity))");
        }
    }

    private void insertWorkspace(CandidateWorkspaceState.Snapshot snapshot) throws SQLException {
        try (PreparedStatement category = connection.prepareStatement(
                "INSERT INTO deck_planner_candidate_categories(position_no, category_id, category_name) VALUES (?, ?, ?)");
             PreparedStatement assignment = connection.prepareStatement(
                     "INSERT INTO deck_planner_candidate_assignments(card_identity, category_id) VALUES (?, ?)")) {
            for (int i=0;i<snapshot.categories().size();i++) {
                var c=snapshot.categories().get(i);
                category.setInt(1,i); category.setString(2,c.id()); category.setString(3,c.name()); category.addBatch();
            }
            category.executeBatch();
            for (var e:snapshot.assignments().entrySet()) {
                assignment.setString(1,e.getKey()); assignment.setString(2,e.getValue()); assignment.addBatch();
            }
            assignment.executeBatch();
        }
    }

    private void insertSetWorkspace(String name, CandidateWorkspaceState.Snapshot snapshot) throws SQLException {
        try (PreparedStatement category = connection.prepareStatement(
                "INSERT INTO deck_planner_candidate_set_categories(set_name, position_no, category_id, category_name) VALUES (?, ?, ?, ?)");
             PreparedStatement assignment = connection.prepareStatement(
                     "INSERT INTO deck_planner_candidate_set_assignments(set_name, card_identity, category_id) VALUES (?, ?, ?)")) {
            for (int i=0;i<snapshot.categories().size();i++) {
                var c=snapshot.categories().get(i);
                category.setString(1,name); category.setInt(2,i); category.setString(3,c.id()); category.setString(4,c.name()); category.addBatch();
            }
            category.executeBatch();
            for (var e:snapshot.assignments().entrySet()) {
                assignment.setString(1,name); assignment.setString(2,e.getKey()); assignment.setString(3,e.getValue()); assignment.addBatch();
            }
            assignment.executeBatch();
        }
    }

    private void deleteSetChildren(String name) throws SQLException {
        for (String table : List.of("deck_planner_candidate_set_cards",
                "deck_planner_candidate_set_categories", "deck_planner_candidate_set_assignments")) {
            try (PreparedStatement st = connection.prepareStatement("DELETE FROM " + table + " WHERE set_name=?")) {
                st.setString(1,name); st.executeUpdate();
            }
        }
    }

    private void rollback(){ try { connection.rollback(); } catch(SQLException ignored){} }
    private void autoCommit(){ try { connection.setAutoCommit(true); } catch(SQLException ignored){} }

    @Override public synchronized void close() {
        try { connection.close(); }
        catch (SQLException error) { throw new IllegalStateException("Could not close Candidate Set repository", error); }
    }
}
