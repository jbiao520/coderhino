package com.coderhino.verification.examples.spring;

import com.coderhino.agent.spring.CoderhinoAgentCredentialProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class HardcodedCredentialProviderConfiguration {
    public static final String EXAMPLE_API_KEY = "sk-omTdJ1LhU2qcqU3SqsZeN7_7oqdUOVawJuXbfD-akkuwZz6R217eJmrhZ9GtBt-k27oys6zcY7tVNo";
    private static final String PLACEHOLDER_API_KEY = "replace-with-your-api-key";

    private HardcodedCredentialProviderConfiguration() {
    }

    @Configuration(proxyBeanMethods = false)
    public static class ProviderBeanConfiguration {
        @Bean
        CoderhinoAgentCredentialProvider coderhinoAgentCredentialProvider(Environment environment) {
            return () -> firstNonBlank(
                resolveConfiguredApiKey(environment.getProperty("coderhino.agent.api-key"), "coderhino.agent.api-key"),
                resolveConfiguredApiKey(environment.getProperty("CODERHINO_AGENT_API_KEY"), "CODERHINO_AGENT_API_KEY"),
                resolveConfiguredApiKey(environment.getProperty("ANTHROPIC_API_KEY"), "ANTHROPIC_API_KEY"),
                EXAMPLE_API_KEY
            );
        }
    }

    private static String resolveConfiguredApiKey(String value, String sourceName) {
        if (value == null || value.isBlank() || PLACEHOLDER_API_KEY.equals(value.trim())) {
            return null;
        }

        Path candidatePath = Path.of(value.trim());
        if (!Files.isRegularFile(candidatePath)) {
            return value;
        }

        try {
            return Files.readString(candidatePath).trim();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read API key file configured by " + sourceName + ": " + candidatePath, exception);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
