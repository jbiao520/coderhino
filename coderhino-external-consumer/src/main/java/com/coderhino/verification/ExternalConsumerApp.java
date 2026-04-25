package com.coderhino.verification;

import com.coderhino.agent.CoderhinoAgent;
import com.coderhino.config.credentials.CredentialsPersistenceService;
import com.coderhino.config.settings.SettingsPersistenceService;
import com.coderhino.query.AgentConfigResolver;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ExternalConsumerApp {
    private static final String PROMPT = "what is the best city in china, rank top 10";
    private static final String SETTINGS_DIR = ".coderhino";
    private static final String CREDENTIALS_FILE = "api-credentials.json";
    private static final String SETTINGS_FILE = "web-settings.json";

    private ExternalConsumerApp() {
    }

    public static void main(String[] args) {
        try {
            var workspaceRoot = locateWorkspaceRoot();
            var settingsDir = workspaceRoot.resolve(SETTINGS_DIR);
            var resolvedConfig = new AgentConfigResolver(
                new CredentialsPersistenceService(settingsDir.resolve(CREDENTIALS_FILE)),
                new SettingsPersistenceService(settingsDir.resolve(SETTINGS_FILE))
            ).resolve();
            var agent = CoderhinoAgent.builder()
                .cwd(workspaceRoot)
                .model(resolvedConfig.getModel())
                .apiKey(resolvedConfig.getApiKey())
                .apiBaseUrl(resolvedConfig.getBaseUrl())
                .providerApiType(resolvedConfig.getApiType())
                .contextWindow(resolvedConfig.getContextWindow())
                .build();

            var result = agent.run(PROMPT);
            if (!result.isSuccess()) {
                System.err.println("Coderhino external consumer run failed: " + result.stopReason());
                if (result.finalText() != null && !result.finalText().isBlank()) {
                    System.err.println(result.finalText());
                }
                System.exit(1);
                return;
            }

            if (result.finalText() == null || result.finalText().isBlank()) {
                System.err.println("Coderhino external consumer run succeeded but returned an empty assistant reply.");
                System.exit(1);
                return;
            }

            System.out.println(result.finalText());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            System.err.println("Coderhino external consumer is not configured for a live model run.");
            System.err.println(exception.getMessage());
            System.err.println("Configure a default provider and API key in the nearest .coderhino/api-credentials.json, and optionally a default model in .coderhino/web-settings.json.");
            System.exit(1);
        } catch (RuntimeException exception) {
            System.err.println("Coderhino external consumer run failed.");
            if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
                System.err.println(exception.getMessage());
            }
            System.exit(1);
        }
    }

    private static Path locateWorkspaceRoot() {
        var current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isDirectory(current.resolve(SETTINGS_DIR))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException(
            "Could not find a .coderhino directory from the current working directory or its parents."
        );
    }
}
