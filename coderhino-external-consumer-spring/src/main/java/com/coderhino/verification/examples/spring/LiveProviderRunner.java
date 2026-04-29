package com.coderhino.verification.examples.spring;

import com.coderhino.agent.CoderhinoAgent;
import com.coderhino.query.ProviderApiType;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class LiveProviderRunner {
    public static final String LIVE_INPUT = "Summarize the live provider configuration for this Spring host.";
    public static final String PRIMARY_API_KEY_ENV = "CODERHINO_AGENT_API_KEY";

    private LiveProviderRunner() {
    }

    public static LiveRunGate evaluateGate(ProviderApiType providerApiType) {
        return evaluateGate(providerApiType, System.getenv());
    }

    public static LiveRunGate evaluateGate(ProviderApiType providerApiType, Map<String, String> environment) {
        var requiredEnvVars = requiredEnvVars(providerApiType);
        for (var envVar : requiredEnvVars) {
            var value = environment.get(envVar);
            if (value != null && !value.isBlank()) {
                return new LiveRunGate(LiveRunStatus.READY, providerApiType, envVar,
                    "Live provider run is explicitly enabled via " + envVar + ".", requiredEnvVars);
            }
        }
        return new LiveRunGate(
            LiveRunStatus.SKIPPED,
            providerApiType,
            null,
            "Skipping live provider run. Set one of " + requiredEnvVars + " before invoking a real provider.",
            requiredEnvVars
        );
    }

    public static LiveRunOutcome runWhenReady(ProviderApiType providerApiType, Supplier<CoderhinoAgent.AgentResult> liveCall) {
        return runWhenReady(providerApiType, System.getenv(), liveCall);
    }

    public static LiveRunOutcome runWhenReady(
        ProviderApiType providerApiType,
        Map<String, String> environment,
        Supplier<CoderhinoAgent.AgentResult> liveCall
    ) {
        var gate = evaluateGate(providerApiType, environment);
        if (gate.status() != LiveRunStatus.READY) {
            return new LiveRunOutcome(gate, false, null, gate.message());
        }
        var result = Objects.requireNonNull(liveCall, "liveCall").get();
        return new LiveRunOutcome(gate, true, result, "Live provider run completed.");
    }

    public static List<String> requiredEnvVars(ProviderApiType providerApiType) {
        Objects.requireNonNull(providerApiType, "providerApiType");
        return List.of(PRIMARY_API_KEY_ENV, "ANTHROPIC_API_KEY");
    }

    public enum LiveRunStatus {
        READY,
        SKIPPED
    }

    public record LiveRunGate(
        LiveRunStatus status,
        ProviderApiType providerApiType,
        String matchedEnvVar,
        String message,
        List<String> acceptedEnvVars
    ) {
    }

    public record LiveRunOutcome(
        LiveRunGate gate,
        boolean attempted,
        CoderhinoAgent.AgentResult result,
        String message
    ) {
    }
}
