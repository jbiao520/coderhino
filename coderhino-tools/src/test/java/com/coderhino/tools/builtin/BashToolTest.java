package com.coderhino.tools.builtin;

import com.coderhino.tools.ToolContext;
import com.coderhino.tools.runtime.ToolBootstrapState;
import com.coderhino.types.PermissionMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BashToolTest {
    @TempDir
    Path tempDir;

    @Test
    void executeDrainsLargeOutputWhileProcessRuns() {
        var output = assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
            new BashTool().execute(
                new BashTool.Input("yes x | head -c 200000", 2),
                context(tempDir)
            )
        );

        assertEquals(0, output.exitCode());
        assertTrue(output.stdout().length() >= 100 * 1024);
        assertTrue(output.stdout().contains("... [truncated]"));
        assertEquals("", output.stderr());
    }

    private ToolContext context(Path cwd) {
        return new ToolContext(
            new TestBootstrapState(cwd.toString()),
            PermissionMode.BYPASS,
            null,
            null,
            null,
            null
        );
    }

    private record TestBootstrapState(String cwd) implements ToolBootstrapState {
        @Override
        public UUID sessionId() {
            return UUID.randomUUID();
        }

        @Override
        public void updatePermissionMode(PermissionMode permissionMode) {
        }
    }
}
