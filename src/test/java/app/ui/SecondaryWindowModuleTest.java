package app.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecondaryWindowModuleTest {
    @Test
    void opensSingletonWindowOnActivationButNotRepeatedSelection() {
        AtomicInteger opens = new AtomicInteger();
        SecondaryWindowModule secondary = new SecondaryWindowModule(
                "draft", "Draft Assistant", "Companion window", opens::incrementAndGet);
        ApplicationModule replay = new ApplicationModule() {
            private final javax.swing.JPanel component = new javax.swing.JPanel();
            @Override public String id() { return "replay"; }
            @Override public String displayName() { return "Replay"; }
            @Override public javax.swing.JComponent component() { return component; }
        };
        ModuleHost host = new ModuleHost(List.of(replay, secondary), ignored -> { });

        host.selectModule("draft");
        host.selectModule("draft");

        assertEquals(1, opens.get());
    }
}
