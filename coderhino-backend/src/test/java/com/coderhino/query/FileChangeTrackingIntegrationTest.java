package com.coderhino.query;

import com.coderhino.permissions.PermissionChecker;
import com.coderhino.services.ServiceRegistry;
import com.coderhino.services.summary.FileChange;
import com.coderhino.services.summary.FileChangeTracker;
import com.coderhino.services.summary.FileOperation;
import com.coderhino.state.AppState;
import com.coderhino.state.BootstrapState;
import com.coderhino.state.SessionRuntime;
import com.coderhino.tools.ToolRegistry;
import com.coderhino.types.PermissionMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileChangeTrackingIntegrationTest {

    private BootstrapState bootstrapState;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private FileChangeTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new FileChangeTracker();
        var appState = new AppState(
            false, "test-model", System.getProperty("user.dir"),
            true, true, PermissionMode.BYPASS,
            0.0, SessionRuntime.create(), List.of()
        );
        bootstrapState = new BootstrapState(appState);
    }

    @Test
    void fileWriteRecordsCreated() {
        var targetFile = Path.of(System.getProperty("user.dir")).resolve("integration-test-create.txt");
        var modelClient = (ModelClient) (state, request) -> new ModelResponse.ToolRequest(
            "write_file", Map.of("path", "integration-test-create.txt", "content", "hello"), "tool-use-1"
        );

        var orchestrator = new ToolLoopOrchestrator(
            ToolRegistry.createDefault(), modelClient, new PermissionChecker(),
            ServiceRegistry.createDefault(), objectMapper,
            10, new UsageAccumulator(), new StopReasonResolver(),
            new BudgetEnforcer(0), new ResponsePersistence(), null, tracker
        );

        var request = new QueryRequest(List.of(), "system", null, null, null);
        orchestrator.run(bootstrapState, request, NoOpQueryEventSink.INSTANCE);

        var sessionId = bootstrapState.get().sessionRuntime().sessionId();
        var changes = tracker.getChanges(sessionId);
        assertFalse(changes.isEmpty());
        assertEquals(FileOperation.CREATED, changes.get(0).operation());
        assertEquals(targetFile.normalize(), changes.get(0).file());

        try { Files.deleteIfExists(targetFile); } catch (Exception ignored) {}
    }

    @Test
    void fileEditRecordsModified() throws Exception {
        var targetFile = Path.of(System.getProperty("user.dir")).resolve("integration-test-edit.txt");
        Files.writeString(targetFile, "original content");

        var modelClient = (ModelClient) (state, request) -> new ModelResponse.ToolRequest(
            "edit_file",
            Map.of("path", "integration-test-edit.txt", "oldText", "original", "newText", "updated"),
            "tool-use-1"
        );

        var orchestrator = new ToolLoopOrchestrator(
            ToolRegistry.createDefault(), modelClient, new PermissionChecker(),
            ServiceRegistry.createDefault(), objectMapper,
            10, new UsageAccumulator(), new StopReasonResolver(),
            new BudgetEnforcer(0), new ResponsePersistence(), null, tracker
        );

        var request = new QueryRequest(List.of(), "system", null, null, null);
        orchestrator.run(bootstrapState, request, NoOpQueryEventSink.INSTANCE);

        var sessionId = bootstrapState.get().sessionRuntime().sessionId();
        var changes = tracker.getChanges(sessionId);
        assertFalse(changes.isEmpty());
        assertEquals(FileOperation.MODIFIED, changes.get(0).operation());
        assertEquals(targetFile.normalize(), changes.get(0).file());

        try { Files.deleteIfExists(targetFile); } catch (Exception ignored) {}
    }

    @Test
    void bashCommandRecordsChanges() {
        var modelClient = (ModelClient) (state, request) -> new ModelResponse.ToolRequest(
            "bash", Map.of("command", "touch integration-bash-test.txt"), "tool-use-1"
        );

        var orchestrator = new ToolLoopOrchestrator(
            ToolRegistry.createDefault(), modelClient, new PermissionChecker(),
            ServiceRegistry.createDefault(), objectMapper,
            10, new UsageAccumulator(), new StopReasonResolver(),
            new BudgetEnforcer(0), new ResponsePersistence(), null, tracker
        );

        var request = new QueryRequest(List.of(), "system", null, null, null);
        orchestrator.run(bootstrapState, request, NoOpQueryEventSink.INSTANCE);

        var sessionId = bootstrapState.get().sessionRuntime().sessionId();
        var changes = tracker.getChanges(sessionId);
        assertFalse(changes.isEmpty());
        assertEquals(FileOperation.CREATED, changes.get(0).operation());

        try { Files.deleteIfExists(Path.of(System.getProperty("user.dir")).resolve("integration-bash-test.txt")); } catch (Exception ignored) {}
    }
}
