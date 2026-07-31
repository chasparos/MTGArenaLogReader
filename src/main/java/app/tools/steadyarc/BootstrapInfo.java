package app.tools.steadyarc;

/**
 * Steady Arc bootstrap payload helper.
 *
 * <p>Exposes compile-time project identity constants so that bootstrap tooling and
 * continuity scripts can verify they are running against the correct artifact without
 * requiring a full Maven metadata parse at runtime.
 *
 * <p>This class contains no runtime logic and does not participate in the application
 * startup path. It exists solely as a tooling surface for Steady Arc bootstrap workflows.
 */
public final class BootstrapInfo {

    public static final String GROUP_ID = "org.example";
    public static final String ARTIFACT_ID = "MTGArenaLogReader";
    public static final String VERSION = "1.0-SNAPSHOT";

    private BootstrapInfo() {
    }
}
