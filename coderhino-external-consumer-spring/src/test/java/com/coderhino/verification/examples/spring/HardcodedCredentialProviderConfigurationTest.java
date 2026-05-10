package com.coderhino.verification.examples.spring;

import com.coderhino.agent.spring.CoderhinoAgentCredentialProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class HardcodedCredentialProviderConfigurationTest {
    @TempDir
    Path tempDir;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(HardcodedCredentialProviderConfiguration.ProviderBeanConfiguration.class);

    @Test
    void configuredApiKeyFileWinsOverSampleFallback() throws Exception {
        Path apiKeyFile = tempDir.resolve("api-key.txt");
        Files.writeString(apiKeyFile, "file-backed-key\n");

        contextRunner
            .withPropertyValues("coderhino.agent.api-key=" + apiKeyFile)
            .run(context -> assertThat(context.getBean(CoderhinoAgentCredentialProvider.class).apiKey())
                .isEqualTo("file-backed-key"));
    }

    @Test
    void checkedInPlaceholderUsesSampleFallback() {
        contextRunner
            .withPropertyValues("coderhino.agent.api-key=replace-with-your-api-key")
            .run(context -> assertThat(context.getBean(CoderhinoAgentCredentialProvider.class).apiKey())
                .isEqualTo(HardcodedCredentialProviderConfiguration.EXAMPLE_API_KEY));
    }
}
