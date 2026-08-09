package app.ui;

import javax.swing.JComponent;

/** Minimal contract for application functionality hosted by the production shell. */
public interface ApplicationModule {
    String id();
    String displayName();
    JComponent component();

    default void activated() { }
    default void deactivated() { }
    default String shellTitle() { return displayName(); }
    default String shellStatus() { return "Ready"; }
}
