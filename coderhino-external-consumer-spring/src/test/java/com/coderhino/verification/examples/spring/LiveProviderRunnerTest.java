package com.coderhino.verification.examples.spring;

import com.coderhino.agent.CoderhinoAgent;
import com.coderhino.query.ProviderApiType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class LiveProviderRunnerTest {

    @Test
    void skipsClaudeRunWithoutCredentialsAndDoesNotInvokeSupplier() {
        var invoked = new AtomicBoolean(false);

        var outcome = LiveProviderRunner.runWhenReady(
            ProviderApiType.CLAUDE_CODE,
            Map.of(),
            () -> {
                invoked.set(true);
                return new CoderhinoAgent.AgentResult("unexpected", null, 0, null, null, null);
            }
        );

        assertThat(outcome.gate().status()).isEqualTo(LiveProviderRunner.LiveRunStatus.SKIPPED);
        assertThat(outcome.gate().acceptedEnvVars()).containsExactly(
            LiveProviderRunner.PRIMARY_API_KEY_ENV,
            "ANTHROPIC_API_KEY"
        );
        assertThat(outcome.gate().matchedEnvVar()).isNull();
        assertThat(outcome.attempted()).isFalse();
        assertThat(outcome.result()).isNull();
        assertThat(outcome.message()).contains("Skipping live provider run");
        assertThat(invoked.get()).isFalse();
    }

    @Test
    void acceptsPrimaryCoderhinoEnvVarAsExplicitOptIn() {
        var expected = new CoderhinoAgent.AgentResult("live reply", null, 1, null, null, null);

        var outcome = LiveProviderRunner.runWhenReady(
            ProviderApiType.CLAUDE_CODE,
            Map.of(LiveProviderRunner.PRIMARY_API_KEY_ENV, "test-key"),
            () -> expected
        );

        assertThat(outcome.gate().status()).isEqualTo(LiveProviderRunner.LiveRunStatus.READY);
        assertThat(outcome.gate().matchedEnvVar()).isEqualTo(LiveProviderRunner.PRIMARY_API_KEY_ENV);
        assertThat(outcome.attempted()).isTrue();
        assertThat(outcome.result()).isSameAs(expected);
        assertThat(outcome.message()).isEqualTo("Live provider run completed.");
    }

    @Test
    void acceptsProviderSpecificFallbackEnvVarForClaude() {
        var gate = LiveProviderRunner.evaluateGate(
            ProviderApiType.CLAUDE_CODE,
            Map.of("ANTHROPIC_API_KEY", "anthropic-test-key")
        );

        assertThat(gate.status()).isEqualTo(LiveProviderRunner.LiveRunStatus.READY);
        assertThat(gate.matchedEnvVar()).isEqualTo("ANTHROPIC_API_KEY");
        assertThat(gate.acceptedEnvVars()).containsExactly(
            LiveProviderRunner.PRIMARY_API_KEY_ENV,
            "ANTHROPIC_API_KEY"
        );
    }

    @Test
    void openAiProviderDoesNotTreatBareOpenAiEnvVarAsDefaultSpringReadiness() {
        var invoked = new AtomicBoolean(false);

        var outcome = LiveProviderRunner.runWhenReady(
            ProviderApiType.OPENAI,
            Map.of("OPENAI_API_KEY", "openai-test-key"),
            () -> {
                invoked.set(true);
                return new CoderhinoAgent.AgentResult("unexpected", null, 0, null, null, null);
            }
        );

        assertThat(outcome.gate().status()).isEqualTo(LiveProviderRunner.LiveRunStatus.SKIPPED);
        assertThat(outcome.gate().matchedEnvVar()).isNull();
        assertThat(outcome.gate().acceptedEnvVars()).containsExactly(
            LiveProviderRunner.PRIMARY_API_KEY_ENV,
            "ANTHROPIC_API_KEY"
        );
        assertThat(outcome.attempted()).isFalse();
        assertThat(invoked.get()).isFalse();
    }
}
