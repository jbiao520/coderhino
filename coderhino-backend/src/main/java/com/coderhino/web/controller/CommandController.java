package com.coderhino.web.controller;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;
import com.coderhino.commands.CommandRegistry;
import com.coderhino.commands.MarkdownCommandDefinition;
import com.coderhino.commands.PromptBackedCommand;
import com.coderhino.cli.PrintStreamTerminalRenderer;
import com.coderhino.query.ModelClient;
import com.coderhino.query.ModelClientFactory;
import com.coderhino.query.QueryEngine;
import com.coderhino.services.ServiceRegistry;
import com.coderhino.state.BootstrapState;
import com.coderhino.state.SessionStore;
import com.coderhino.tools.ToolRegistry;
import com.coderhino.web.dto.CommandDto;
import com.coderhino.web.dto.CommandExecuteRequest;
import com.coderhino.web.dto.CommandExecuteResponse;
import com.coderhino.web.dto.ErrorResponse;
import com.coderhino.web.credentials.ProviderConfigResolver;
import com.coderhino.services.tasks.TaskOriginContext;
import com.coderhino.web.service.CommandAudioStore;
import com.coderhino.web.service.ReadCommandWebService;
import com.coderhino.web.session.WebSession;
import com.coderhino.web.session.WebSessionRegistry;
import com.coderhino.types.Message;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/commands")
public class CommandController {

    private final CommandRegistry commandRegistry;
    private final BootstrapState bootstrapState;
    private final SessionStore sessionStore;
    private final ServiceRegistry serviceRegistry;
    private final WebSessionRegistry sessionRegistry;
    private final ReadCommandWebService readCommandWebService;
    private final CommandAudioStore commandAudioStore;

    public CommandController(
        CommandRegistry commandRegistry,
        BootstrapState bootstrapState,
        SessionStore sessionStore,
        ServiceRegistry serviceRegistry,
        WebSessionRegistry sessionRegistry,
        ReadCommandWebService readCommandWebService,
        CommandAudioStore commandAudioStore
    ) {
        this.commandRegistry = commandRegistry;
        this.bootstrapState = bootstrapState;
        this.sessionStore = sessionStore;
        this.serviceRegistry = serviceRegistry;
        this.sessionRegistry = sessionRegistry;
        this.readCommandWebService = readCommandWebService;
        this.commandAudioStore = commandAudioStore;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<CommandDto> listCommands() {
        return commandRegistry.all().stream()
            .filter(command -> !command.hidden())
            .map(command -> new CommandDto(
                command.name(),
                command.description(),
                command.aliases(),
                command.webCompatible(),
                command instanceof PromptBackedCommand
            ))
            .toList();
    }

    @PostMapping(value = "/resolve-prompt", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> resolvePrompt(@RequestBody CommandExecuteRequest request) {
        var commandName = request.command() == null ? "" : request.command().trim();
        var command = commandRegistry.find(commandName);
        if (command.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("Unknown command: " + commandName));
        }

        var definition = command.get();
        var args = request.arguments() == null ? List.<String>of() : request.arguments();
        var normalizedPrompt = formatPrompt(definition.name(), args);
        var visiblePrompt = resolveVisiblePrompt(definition, String.join(" ", args), normalizedPrompt);
        return ResponseEntity.ok(new ResolvedPromptDto(definition.name(), visiblePrompt, definition instanceof PromptBackedCommand));
    }

    @PostMapping(value = "/execute", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> executeCommand(@RequestBody CommandExecuteRequest request) {
        var commandName = request.command() == null ? "" : request.command().trim();
        var command = commandRegistry.find(commandName);
        if (command.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("Unknown command: " + commandName));
        }

        var args = request.arguments() == null ? List.<String>of() : request.arguments();
        var prompt = formatPrompt(commandName, args);
        var definition = command.get();
        if (!definition.webCompatible()) {
            return ResponseEntity.ok(new CommandExecuteResponse(
                prompt,
                "/" + definition.name() + " is not available in web mode.",
                false,
                definition.name()
            ));
        }

        var targetState = resolveBootstrapState(request.sessionId());
        if ("read".equals(definition.name())) {
            var response = readCommandWebService.execute(prompt, args, targetState);
            persistCommandExchange(request.sessionId(), response.prompt(), response.output());
            return ResponseEntity.ok(response);
        }
        var stdout = new ByteArrayOutputStream();
        var stderr = new ByteArrayOutputStream();

        try (var out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
             var err = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            var context = new CommandContext(
                targetState,
                commandRegistry,
                sessionStore,
                serviceRegistry,
                (commandContext, commandDefinition, promptText) -> executePromptCommand(commandDefinition, promptText, prompt, request.sessionId()),
                new PrintStreamTerminalRenderer(out, err),
                out,
                err
            );
            definition.execute(context, String.join(" ", args));
            var response = new CommandExecuteResponse(prompt, combineOutput(stdout, stderr, null), true, definition.name());
            if (!(definition instanceof PromptBackedCommand)) {
                persistCommandExchange(request.sessionId(), response.prompt(), response.output());
            } else {
                persistSessionOnly(request.sessionId());
            }
            return ResponseEntity.ok(response);
        } catch (Exception exception) {
            var response = new CommandExecuteResponse(prompt, combineOutput(stdout, stderr, exception.getMessage()), false, definition.name());
            if (!(definition instanceof PromptBackedCommand)) {
                persistCommandExchange(request.sessionId(), response.prompt(), response.output());
            } else {
                persistSessionOnly(request.sessionId());
            }
            return ResponseEntity.ok(response);
        }
    }

    @GetMapping(value = "/audio/{token}", produces = "audio/mpeg")
    public ResponseEntity<?> getCommandAudio(@PathVariable("token") String token) {
        return commandAudioStore.resolve(token)
            .<ResponseEntity<?>>map(path -> ResponseEntity.ok()
                .contentType(mediaTypeForAudio(path))
                .body(new FileSystemResource(path)))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("Unknown or expired audio token.")));
    }

    @DeleteMapping("/audio/{token}")
    public ResponseEntity<Void> deleteCommandAudio(@PathVariable("token") String token) {
        commandAudioStore.delete(token);
        return ResponseEntity.noContent().build();
    }

    private BootstrapState resolveBootstrapState(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return bootstrapState;
        }
        return sessionRegistry.find(sessionId)
            .map(session -> session.getBootstrapState())
            .orElse(bootstrapState);
    }

    private static String formatPrompt(String command, List<String> arguments) {
        var joinedArgs = String.join(" ", arguments);
        return ("/" + command + " " + joinedArgs).trim();
    }

    private static String combineOutput(ByteArrayOutputStream stdout, ByteArrayOutputStream stderr, String fallbackMessage) {
        var standard = stdout.toString(StandardCharsets.UTF_8).stripTrailing();
        var error = stderr.toString(StandardCharsets.UTF_8).stripTrailing();
        var combined = standard;
        if (!error.isBlank()) {
            combined = combined.isBlank() ? error : combined + System.lineSeparator() + error;
        }
        if (!combined.isBlank()) {
            return combined;
        }
        return fallbackMessage == null ? "" : fallbackMessage;
    }

    static ToolRegistry toolRegistryForPromptCommand(ToolRegistry registry, CommandDefinition definition) {
        if (definition instanceof PromptBackedCommand promptBackedCommand) {
            return registry.filtered(promptBackedCommand.allowedTools());
        }
        return registry;
    }

    static String resolveVisiblePrompt(CommandDefinition definition, String args, String fallbackPrompt) {
        if (definition instanceof PromptBackedCommand promptBackedCommand) {
            var rendered = promptBackedCommand.prompt(args == null ? "" : args).trim();
            if (!rendered.isBlank()) {
                return rendered;
            }
        }
        return fallbackPrompt;
    }

    private String executePromptCommand(CommandDefinition definition, String prompt, String visiblePrompt, String sessionId) throws Exception {
        var session = sessionId == null || sessionId.isBlank() ? null : sessionRegistry.find(sessionId).orElse(null);
        if (session == null) {
            throw new IllegalStateException("Prompt-backed commands require a valid web session.");
        }

        ensureLatestVisibleCommand(session, visiblePrompt);
        var config = createProviderConfigResolver().resolve(session.getProviderId(), session.getAppState().model());
        var modelClient = createModelClient(config);
        var queryEngine = new QueryEngine(
            toolRegistryForPromptCommand(ToolRegistry.createDefault(), definition),
            modelClient,
            new com.coderhino.permissions.PermissionChecker(),
            new com.coderhino.context.ContextCollector(),
            serviceRegistry
        );
        var projectId = sessionRegistry.getProjectIdForSession(sessionId).orElse(null);
        try (var ignored = TaskOriginContext.open(projectId, session.getSessionId())) {
            return queryEngine.respond(session.getBootstrapState(), prompt, visiblePrompt).content();
        }
    }

    protected ModelClient createModelClient(ProviderConfigResolver.ResolvedConfig config) {
        return ModelClientFactory.create(
            config.getModel(),
            config.getApiKey(),
            config.getBaseUrl(),
            config.getApiType(),
            config.getContextWindow()
        );
    }

    protected ProviderConfigResolver createProviderConfigResolver() {
        return new ProviderConfigResolver();
    }

    private void persistCommandExchange(String sessionId, String prompt, String output) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        sessionRegistry.find(sessionId).ifPresent(session -> {
            ensureLatestVisibleCommand(session, prompt);
            if (output != null && !output.isBlank()) {
                session.getBootstrapState().addMessage(new Message.AssistantMessage(output));
            }
            sessionRegistry.persistSessionState(session);
        });
    }

    private void persistSessionOnly(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        sessionRegistry.find(sessionId).ifPresent(sessionRegistry::persistSessionState);
    }

    private static void ensureLatestVisibleCommand(WebSession session, String prompt) {
        if (session == null || prompt == null || prompt.isBlank()) {
            return;
        }
        var messages = session.getAppState().messages();
        boolean alreadyPresent = !messages.isEmpty()
            && messages.get(messages.size() - 1) instanceof Message.UserMessage userMessage
            && prompt.equals(userMessage.content());
        if (!alreadyPresent) {
            session.getBootstrapState().addMessage(new Message.UserMessage(prompt));
        }
    }

    private static MediaType mediaTypeForAudio(java.nio.file.Path path) {
        var fileName = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".wav")) {
            return MediaType.parseMediaType("audio/wav");
        }
        return MediaType.parseMediaType("audio/mpeg");
    }

    public record ResolvedPromptDto(String commandName, String visiblePrompt, boolean promptBacked) {
    }
}
