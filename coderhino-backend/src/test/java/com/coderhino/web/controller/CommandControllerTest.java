package com.coderhino.web.controller;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;
import com.coderhino.commands.CommandRegistry;
import com.coderhino.commands.MarkdownCommandDefinition;
import com.coderhino.commands.MarkdownPromptDefinition;
import com.coderhino.commands.PromptBackedCommand;
import com.coderhino.query.ModelClient;
import com.coderhino.query.ModelResponse;
import com.coderhino.query.QueryRequest;
import com.coderhino.services.ServiceRegistry;
import com.coderhino.services.lsp.LspClientManager;
import com.coderhino.services.mcp.McpConnectionManager;
import com.coderhino.services.tasks.TaskService;
import com.coderhino.state.AppState;
import com.coderhino.state.BootstrapState;
import com.coderhino.state.SessionRuntime;
import com.coderhino.state.SessionStore;
import com.coderhino.tools.ToolRegistry;
import com.coderhino.types.PermissionMode;
import com.coderhino.web.session.SessionPersistenceService;
import com.coderhino.web.session.WebSession;
import com.coderhino.web.session.WebSessionRegistry;
import com.coderhino.web.events.SessionEventBus;
import com.coderhino.web.service.CommandAudioStore;
import com.coderhino.web.service.ReadCommandWebService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.parseMediaType;

class CommandControllerTest {

    @Test
    void listCommandsFiltersHiddenAndIncludesWebCompatibility(@TempDir Path tempDir) {
        var controller = controller(
            new CommandRegistry(List.of(
                new StubCommand("visible", "Visible command", List.of("v"), false, true, null),
                new PromptStubCommand("init", "Initialize", List.of(), false, true, "prompt body", List.of("bash")),
                new StubCommand("hidden", "Hidden command", List.of(), true, true, null),
                new StubCommand("terminal", "Terminal command", List.of("tty"), false, false, null)
            )),
            tempDir
        );

        var response = controller.listCommands();

        assertEquals(3, response.size());
        assertEquals("visible", response.get(0).name());
        assertEquals(List.of("v"), response.get(0).aliases());
        assertTrue(response.get(0).webCompatible());
        assertFalse(response.get(0).promptBacked());
        assertEquals("init", response.get(1).name());
        assertTrue(response.get(1).webCompatible());
        assertTrue(response.get(1).promptBacked());
        assertEquals("terminal", response.get(2).name());
        assertFalse(response.get(2).webCompatible());
        assertFalse(response.get(2).promptBacked());
    }

    @Test
    void executeCommandCapturesOutput(@TempDir Path tempDir) {
        var controller = controller(
            new CommandRegistry(List.of(
                new StubCommand("status", "Status", List.of(), false, true, (context, args) -> context.out().println("ready " + args))
            )),
            tempDir
        );

        var response = controller.executeCommand(new com.coderhino.web.dto.CommandExecuteRequest("status", List.of("now"), null));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var body = assertInstanceOf(com.coderhino.web.dto.CommandExecuteResponse.class, response.getBody());
        assertEquals("/status now", body.prompt());
        assertEquals("ready now", body.output());
        assertTrue(body.success());
        assertEquals("status", body.commandName());
        assertEquals(null, body.audio());
    }

    @Test
    void executeCommandReturns404ForUnknownCommand(@TempDir Path tempDir) {
        var controller = controller(new CommandRegistry(List.of()), tempDir);

        var response = controller.executeCommand(new com.coderhino.web.dto.CommandExecuteRequest("missing", List.of(), null));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        var body = assertInstanceOf(com.coderhino.web.dto.ErrorResponse.class, response.getBody());
        assertEquals("Unknown command: missing", body.getError());
    }

    @Test
    void executeCommandReturnsInformativeMessageForNonWebCompatibleCommand(@TempDir Path tempDir) {
        var executed = new AtomicBoolean(false);
        var controller = controller(
            new CommandRegistry(List.of(
                new StubCommand("vim", "Vim", List.of(), false, false, (context, args) -> executed.set(true))
            )),
            tempDir
        );

        var response = controller.executeCommand(new com.coderhino.web.dto.CommandExecuteRequest("vim", List.of(), null));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var body = assertInstanceOf(com.coderhino.web.dto.CommandExecuteResponse.class, response.getBody());
        assertEquals("/vim", body.prompt());
        assertEquals("/vim is not available in web mode.", body.output());
        assertFalse(body.success());
        assertEquals("vim", body.commandName());
        assertFalse(executed.get());
    }

    @Test
    void executeCommandReturnsFailurePayloadWhenCommandThrows(@TempDir Path tempDir) {
        var controller = controller(
            new CommandRegistry(List.of(
                new StubCommand("explode", "Explode", List.of(), false, true, (context, args) -> {
                    context.err().println("before failure");
                    throw new IllegalStateException("boom");
                })
            )),
            tempDir
        );

        var response = controller.executeCommand(new com.coderhino.web.dto.CommandExecuteRequest("explode", List.of(), null));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var body = assertInstanceOf(com.coderhino.web.dto.CommandExecuteResponse.class, response.getBody());
        assertEquals("/explode", body.prompt());
        assertEquals("before failure", body.output());
        assertFalse(body.success());
        assertEquals("explode", body.commandName());
    }

    @Test
    void executeCommandUsesSessionBootstrapStateWhenSessionIdProvided(@TempDir Path tempDir) throws Exception {
        var sessionRegistry = sessionRegistry(tempDir);
        var session = WebSession.create("ses-123", tempDir.resolve("workspace"));
        putSession(sessionRegistry, session);
        var audioStore = new CommandAudioStore();
        var controller = new CommandController(
            new CommandRegistry(List.of(
                new StubCommand("cwd", "Current cwd", List.of(), false, true, (context, args) -> context.out().print(context.bootstrapState().get().cwd()))
            )),
            bootstrapState(tempDir.resolve("fallback")),
            sessionStore(tempDir),
            serviceRegistry(tempDir),
            sessionRegistry,
            new ReadCommandWebService(audioStore),
            audioStore
        );

        var response = controller.executeCommand(new com.coderhino.web.dto.CommandExecuteRequest("cwd", List.of(), "ses-123"));

        var body = assertInstanceOf(com.coderhino.web.dto.CommandExecuteResponse.class, response.getBody());
        assertEquals(tempDir.resolve("workspace").toAbsolutePath().normalize().toString(), body.output());
        assertEquals("cwd", body.commandName());
    }

    @Test
    void executeCommandPersistsVisibleCommandAndAssistantOutputForSession(@TempDir Path tempDir) throws Exception {
        var sessionRegistry = sessionRegistry(tempDir);
        var session = WebSession.create("ses-123", tempDir.resolve("workspace"));
        putSession(sessionRegistry, session);
        var controller = new CommandController(
            new CommandRegistry(List.of(
                new StubCommand("status", "Status", List.of(), false, true, (context, args) -> context.out().print("ready " + args))
            )),
            bootstrapState(tempDir.resolve("fallback")),
            sessionStore(tempDir),
            serviceRegistry(tempDir),
            sessionRegistry,
            new ReadCommandWebService(new CommandAudioStore()),
            new CommandAudioStore()
        );

        controller.executeCommand(new com.coderhino.web.dto.CommandExecuteRequest("status", List.of("now"), "ses-123"));

        var messages = session.getAppState().messages();
        assertEquals(2, messages.size());
        assertInstanceOf(com.coderhino.types.Message.UserMessage.class, messages.get(0));
        assertEquals("/status now", messages.get(0).content());
        assertInstanceOf(com.coderhino.types.Message.AssistantMessage.class, messages.get(1));
        assertEquals("ready now", messages.get(1).content());
    }

    @Test
    void promptBackedCommandPersistsVisibleTurnButSendsExpandedPromptToModel(@TempDir Path tempDir) throws Exception {
        var sessionRegistry = sessionRegistry(tempDir);
        var session = WebSession.create("ses-prompt", tempDir.resolve("workspace"));
        putSession(sessionRegistry, session);

        QueryRequest[] captured = new QueryRequest[1];
        var controller = new TestableCommandController(
            new CommandRegistry(List.of(
                new PromptStubCommand("init", "Initialize", List.of(), false, true, "inspect repo first: $ARGUMENTS", List.of("bash"))
            )),
            bootstrapState(tempDir.resolve("fallback")),
            sessionStore(tempDir),
            serviceRegistry(tempDir),
            sessionRegistry,
            new ReadCommandWebService(new CommandAudioStore()),
            new CommandAudioStore(),
            (bootstrapState, request) -> {
                captured[0] = request;
                return new ModelResponse.AssistantReply("planned response");
            }
        );

        var response = controller.executeCommand(new com.coderhino.web.dto.CommandExecuteRequest("init", List.of("project"), "ses-prompt"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var body = assertInstanceOf(com.coderhino.web.dto.CommandExecuteResponse.class, response.getBody());
        assertEquals("/init project", body.prompt());
        assertEquals("planned response", body.output());

        var messages = session.getAppState().messages();
        assertEquals(2, messages.size());
        assertEquals("/init project", messages.get(0).content());
        assertEquals("planned response", messages.get(1).content());

        assertNotNull(captured[0]);
        assertEquals(1, captured[0].messages().stream().filter(com.coderhino.types.Message.UserMessage.class::isInstance).count());
        assertEquals("inspect repo first: project", captured[0].messages().get(0).content());
    }

    @Test
    void executeCommandReturnsAudioMetadataForReadCommand(@TempDir Path tempDir) {
        var audioStore = new CommandAudioStore();
        var controller = new CommandController(
            new CommandRegistry(List.of(
                new StubCommand("read", "Read", List.of(), false, true, null)
            )),
            bootstrapState(tempDir.resolve("workspace")),
            sessionStore(tempDir),
            serviceRegistry(tempDir),
            sessionRegistry(tempDir),
            new ReadCommandWebService(audioStore) {
                @Override
                public com.coderhino.web.dto.CommandExecuteResponse execute(String prompt, List<String> args, BootstrapState targetState) {
                    return new com.coderhino.web.dto.CommandExecuteResponse(
                        prompt,
                        "Read aloud text.",
                        true,
                        "read",
                        new com.coderhino.web.dto.CommandAudioDto("tok-1", "/api/commands/audio/tok-1")
                    );
                }
            },
            audioStore
        );

        var response = controller.executeCommand(new com.coderhino.web.dto.CommandExecuteRequest("read", List.of("hello"), null));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var body = assertInstanceOf(com.coderhino.web.dto.CommandExecuteResponse.class, response.getBody());
        assertEquals("read", body.commandName());
        assertNotNull(body.audio());
        assertEquals("tok-1", body.audio().token());
        assertEquals("/api/commands/audio/tok-1", body.audio().url());
    }

    @Test
    void executeCommandSupportsReadBackendSelectionWithoutAudio(@TempDir Path tempDir) {
        var controller = controller(
            new CommandRegistry(List.of(
                new StubCommand("read", "Read", List.of(), false, true, null)
            )),
            tempDir
        );

        var response = controller.executeCommand(new com.coderhino.web.dto.CommandExecuteRequest("read", List.of("backend", "chat-tts"), null));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var body = assertInstanceOf(com.coderhino.web.dto.CommandExecuteResponse.class, response.getBody());
        assertTrue(body.success());
        assertEquals("/read backend set to chat-tts.", body.output());
        assertEquals(null, body.audio());
    }

    @Test
    void getAndDeleteCommandAudioServesStoredFiles(@TempDir Path tempDir) throws Exception {
        var controller = controller(new CommandRegistry(List.of()), tempDir);
        var audioFile = tempDir.resolve("voice.mp3");
        java.nio.file.Files.writeString(audioFile, "audio-bytes", StandardCharsets.UTF_8);
        var audio = controllerAudioStore(controller).store(audioFile);

        var getResponse = controller.getCommandAudio(audio.token());

        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        var resource = assertInstanceOf(FileSystemResource.class, getResponse.getBody());
        assertEquals(audioFile, resource.getFile().toPath());

        var deleteResponse = controller.deleteCommandAudio(audio.token());
        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatusCode());
        assertFalse(java.nio.file.Files.exists(audioFile));

        var missingResponse = controller.getCommandAudio(audio.token());
        assertEquals(HttpStatus.NOT_FOUND, missingResponse.getStatusCode());
    }

    @Test
    void getCommandAudioReturnsWaveContentTypeForWavFiles(@TempDir Path tempDir) throws Exception {
        var controller = controller(new CommandRegistry(List.of()), tempDir);
        var audioFile = tempDir.resolve("voice.wav");
        java.nio.file.Files.writeString(audioFile, "audio-bytes", StandardCharsets.UTF_8);
        var audio = controllerAudioStore(controller).store(audioFile);

        var response = controller.getCommandAudio(audio.token());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(parseMediaType("audio/wav"), response.getHeaders().getContentType());
    }

    @Test
    void toolRegistryForPromptCommandUsesMarkdownAllowedTools() {
        var command = new MarkdownCommandDefinition(new MarkdownPromptDefinition(
            "opsx-propose",
            null,
            "desc",
            "body",
            List.of("bash", "read_file", "todo_write"),
            null,
            true,
            false,
            MarkdownPromptDefinition.DefinitionType.COMMAND,
            MarkdownPromptDefinition.Scope.PROJECT,
            MarkdownPromptDefinition.Namespace.OPENCODE,
            tempPath("/tmp/opsx-propose.md"),
            tempPath("/tmp")
        ));

        var registry = CommandController.toolRegistryForPromptCommand(ToolRegistry.createDefault(), command);

        assertEquals(List.of("bash", "read_file", "todo_write"), registry.all().stream().map(com.coderhino.tools.ToolDefinition::name).toList());
    }

    @Test
    void resolvePromptReturnsRenderedPromptForMarkdownCommand(@TempDir Path tempDir) {
        var controller = controller(
            new CommandRegistry(List.of(
                new MarkdownCommandDefinition(new MarkdownPromptDefinition(
                    "query-weather",
                    "Query Weather",
                    "Query weather",
                    "this is a propose command, user want you to query weather for : $ARGUMENTS",
                    List.of("webfetch"),
                    null,
                    true,
                    false,
                    MarkdownPromptDefinition.DefinitionType.COMMAND,
                    MarkdownPromptDefinition.Scope.PROJECT,
                    MarkdownPromptDefinition.Namespace.OPENCODE,
                    tempPath("/tmp/query-weather.md"),
                    tempPath("/tmp")
                ))
            )),
            tempDir
        );

        var response = controller.resolvePrompt(new com.coderhino.web.dto.CommandExecuteRequest("query-weather", List.of("shanghai"), null));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var body = assertInstanceOf(CommandController.ResolvedPromptDto.class, response.getBody());
        assertEquals("query-weather", body.commandName());
        assertEquals("this is a propose command, user want you to query weather for : shanghai", body.visiblePrompt());
        assertTrue(body.promptBacked());
    }

    @Test
    void toolRegistryForPromptCommandLeavesNonMarkdownCommandsUnrestricted() {
        var registry = CommandController.toolRegistryForPromptCommand(
            ToolRegistry.createDefault(),
            new StubCommand("status", "Status", List.of(), false, true, null)
        );

        assertTrue(registry.all().stream().map(com.coderhino.tools.ToolDefinition::name).toList().contains("write_file"));
        assertTrue(registry.all().stream().map(com.coderhino.tools.ToolDefinition::name).toList().contains("tool_search"));
    }

    @Test
    void toolRegistryForPromptCommandUsesBuiltInPromptAllowedTools() {
        var registry = CommandController.toolRegistryForPromptCommand(
            ToolRegistry.createDefault(),
            new PromptStubCommand("init", "Initialize", List.of(), false, true, "prompt body", List.of("bash", "ask_user_question"))
        );

        var names = registry.all().stream().map(com.coderhino.tools.ToolDefinition::name).toList();
        assertEquals(2, names.size());
        assertTrue(names.contains("bash"));
        assertTrue(names.contains("ask_user_question"));
    }

    @Test
    void resolvePromptReturnsRenderedPromptForBuiltInPromptCommand(@TempDir Path tempDir) {
        var controller = controller(
            new CommandRegistry(List.of(
                new PromptStubCommand("init", "Initialize", List.of(), false, true, "inspect repo first: $ARGUMENTS", List.of("bash"))
            )),
            tempDir
        );

        var response = controller.resolvePrompt(new com.coderhino.web.dto.CommandExecuteRequest("init", List.of("project"), null));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var body = assertInstanceOf(CommandController.ResolvedPromptDto.class, response.getBody());
        assertEquals("init", body.commandName());
        assertEquals("inspect repo first: project", body.visiblePrompt());
        assertTrue(body.promptBacked());
    }

    private static Path tempPath(String value) {
        return Path.of(value);
    }

    private static final class TestableCommandController extends CommandController {
        private final ModelClient modelClient;
        private final com.coderhino.web.credentials.ProviderConfigResolver providerConfigResolver;

        private TestableCommandController(
            CommandRegistry commandRegistry,
            BootstrapState bootstrapState,
            SessionStore sessionStore,
            ServiceRegistry serviceRegistry,
            WebSessionRegistry sessionRegistry,
            ReadCommandWebService readCommandWebService,
            CommandAudioStore commandAudioStore,
            ModelClient modelClient
        ) {
            super(commandRegistry, bootstrapState, sessionStore, serviceRegistry, sessionRegistry, readCommandWebService, commandAudioStore);
            this.modelClient = modelClient;
            this.providerConfigResolver = new com.coderhino.web.credentials.ProviderConfigResolver() {
                @Override
                public ResolvedConfig resolve(String providerId, String requestedModel) {
                    return new ResolvedConfig(
                        providerId == null || providerId.isBlank() ? "test-provider" : providerId,
                        "test-key",
                        "https://example.invalid",
                        requestedModel == null || requestedModel.isBlank() ? "MiniMax-M2.5" : requestedModel,
                        com.coderhino.query.ProviderApiType.CLAUDE_CODE,
                        128000L
                    );
                }
            };
        }

        @Override
        protected ModelClient createModelClient(com.coderhino.web.credentials.ProviderConfigResolver.ResolvedConfig config) {
            return modelClient;
        }

        @Override
        protected com.coderhino.web.credentials.ProviderConfigResolver createProviderConfigResolver() {
            return providerConfigResolver;
        }
    }

    private static CommandController controller(CommandRegistry registry, Path tempDir) {
        var audioStore = new CommandAudioStore();
        return new CommandController(
            registry,
            bootstrapState(tempDir.resolve("default-workspace")),
            sessionStore(tempDir),
            serviceRegistry(tempDir),
            sessionRegistry(tempDir),
            new ReadCommandWebService(audioStore),
            audioStore
        );
    }

    private static CommandAudioStore controllerAudioStore(CommandController controller) throws Exception {
        var field = controller.getClass().getDeclaredField("commandAudioStore");
        field.setAccessible(true);
        return (CommandAudioStore) field.get(controller);
    }

    private static BootstrapState bootstrapState(Path cwd) {
        var normalized = cwd.toAbsolutePath().normalize().toString();
        return new BootstrapState(new AppState(
            false,
            "MiniMax-M2.5",
            normalized,
            false,
            true,
            PermissionMode.BYPASS,
            0.0,
            new SessionRuntime(UUID.randomUUID(), null, null, List.of(), List.of(), List.of()),
            List.of()
        ));
    }

    private static SessionStore sessionStore(Path tempDir) {
        return new SessionStore(new ObjectMapper().registerModule(new JavaTimeModule()), tempDir.resolve("sessions"));
    }

    private static ServiceRegistry serviceRegistry(Path tempDir) {
        return new ServiceRegistry(
            new McpConnectionManager(),
            new LspClientManager(),
            new TaskService(tempDir.resolve("tasks.json"))
        );
    }

    private static WebSessionRegistry sessionRegistry(Path tempDir) {
        return new WebSessionRegistry(
            new SessionPersistenceService(tempDir.resolve("metadata")),
            sessionStore(tempDir),
            new SessionEventBus(new ObjectMapper()),
            new com.coderhino.web.project.ProjectPersistenceService() {
                @Override
                public Optional<com.coderhino.web.project.Project> find(String id) {
                    return Optional.empty();
                }
            }
        );
    }

    private static void putSession(WebSessionRegistry registry, WebSession session) throws Exception {
        var field = registry.getClass().getDeclaredField("sessions");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var sessions = (java.util.concurrent.ConcurrentHashMap<String, WebSession>) field.get(registry);
        sessions.put(session.getSessionId(), session);
        assertNotNull(registry.find(session.getSessionId()).orElse(null));
    }

    private interface CommandBehavior {
        void run(CommandContext context, String args);
    }

    private record StubCommand(
        String name,
        String description,
        List<String> aliases,
        boolean hidden,
        boolean webCompatible,
        CommandBehavior behavior
    ) implements CommandDefinition {

        @Override
        public void execute(CommandContext context, String args) {
            if (behavior != null) {
                behavior.run(context, args);
            }
        }
    }

    private record PromptStubCommand(
        String name,
        String description,
        List<String> aliases,
        boolean hidden,
        boolean webCompatible,
        String promptTemplate,
        List<String> allowedTools
    ) implements PromptBackedCommand {
        @Override
        public String prompt(String args) {
            return promptTemplate.replace("$ARGUMENTS", args == null ? "" : args);
        }
    }
}
