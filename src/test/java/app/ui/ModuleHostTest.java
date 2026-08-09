package app.ui;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.awt.BorderLayout;
import static org.junit.jupiter.api.Assertions.*;

class ModuleHostTest {
    @Test
    void placesModuleNavigationInAHorizontalTopStrip() {
        ModuleHost host = new ModuleHost(
                List.of(new TestModule("replay", new ArrayList<>())), ignored -> { });

        BorderLayout layout = (BorderLayout) host.getLayout();
        assertSame(host.navigationComponent(), layout.getLayoutComponent(BorderLayout.NORTH));
        assertInstanceOf(java.awt.FlowLayout.class, host.navigationComponent().getLayout());
        assertEquals("module-navigation", host.navigationComponent().getName());
    }

    @Test
    void selectsFirstModuleAndReplacesVisibleContentInLifecycleOrder() {
        List<String> events = new ArrayList<>();
        TestModule replay = new TestModule("replay", events);
        TestModule planner = new TestModule("planner", events);
        ModuleHost host = new ModuleHost(List.of(replay, planner),
                module -> events.add("selected:" + module.id()));
        assertEquals("replay", host.selectedModuleId());
        assertSame(replay.component(), host.selectedComponent());
        assertTrue(host.isNavigationSelected("replay"));
        assertEquals(List.of("activate:replay", "selected:replay"), events);

        host.selectModule("planner");
        assertEquals("planner", host.selectedModuleId());
        assertSame(planner.component(), host.selectedComponent());
        assertTrue(host.isNavigationSelected("planner"));
        assertEquals(List.of("activate:replay", "selected:replay",
                "deactivate:replay", "activate:planner", "selected:planner"), events);
    }

    @Test
    void ignoresRepeatedSelectionAndRejectsDuplicateOrUnknownIds() {
        List<String> events = new ArrayList<>();
        TestModule replay = new TestModule("replay", events);
        ModuleHost host = new ModuleHost(List.of(replay), module -> events.add("selected"));
        host.selectModule("replay");
        assertEquals(List.of("activate:replay", "selected"), events);
        assertThrows(IllegalArgumentException.class,
                () -> host.addModule(new TestModule("replay", events)));
        assertThrows(IllegalArgumentException.class, () -> host.selectModule("missing"));
    }

    private record TestModule(String id, List<String> events, JPanel component)
            implements ApplicationModule {
        private TestModule(String id, List<String> events) { this(id, events, new JPanel()); }
        @Override public String displayName() { return id; }
        @Override public void activated() { events.add("activate:" + id); }
        @Override public void deactivated() { events.add("deactivate:" + id); }
    }
}
