package com.coderhino.verification.examples.spring;

import com.coderhino.agent.spring.CoderhinoAgentProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AgentPropertiesExample {
    public static final String PREFIX = "coderhino.agent";
    public static final String MODEL = PREFIX + ".model";
    public static final String CWD = PREFIX + ".cwd";
    public static final String PERMISSION_MODE = PREFIX + ".permission-mode";
    public static final String ENABLED_TOOLS = PREFIX + ".enabled-tools";
    public static final String CUSTOM_SYSTEM_PROMPT = PREFIX + ".custom-system-prompt";
    public static final String APPEND_SYSTEM_PROMPT = PREFIX + ".append-system-prompt";
    public static final String MAX_TOOL_ITERATIONS = PREFIX + ".max-tool-iterations";
    public static final String MAX_BUDGET_USD = PREFIX + ".max-budget-usd";
    public static final String EMBEDDED_INTEGRATIONS_ENABLED = PREFIX + ".embedded-integrations-enabled";
    public static final String API_KEY = PREFIX + ".api-key";
    public static final String API_BASE_URL = PREFIX + ".api-base-url";
    public static final String PROVIDER_API_TYPE = PREFIX + ".provider-api-type";
    public static final String CONTEXT_WINDOW = PREFIX + ".context-window";
    public static final String MAX_OUTPUT_TOKENS = PREFIX + ".max-output-tokens";
    public static final String ENV_CODERHINO_AGENT_API_KEY = "CODERHINO_AGENT_API_KEY";
    public static final String ENV_ANTHROPIC_API_KEY = "ANTHROPIC_API_KEY";

    public static final List<PropertySpec> PROPERTY_MATRIX = createPropertyMatrix();
    public static final List<CredentialPrecedenceSpec> CREDENTIAL_PRECEDENCE = List.of(
        new CredentialPrecedenceSpec(1, "Custom ModelClient bean", "Spring backs off from the default ModelClient path entirely"),
        new CredentialPrecedenceSpec(2, "CoderhinoAgentCredentialProvider bean", "Uses provider.apiKey() before property or environment lookup"),
        new CredentialPrecedenceSpec(3, API_KEY + " or " + ENV_CODERHINO_AGENT_API_KEY, "Uses the configured property or direct environment fallback for the default ModelClient"),
        new CredentialPrecedenceSpec(4, ENV_ANTHROPIC_API_KEY, "Fallback for the default auto-configured ModelClient when the Coderhino-specific paths are unset"),
        new CredentialPrecedenceSpec(5, "Fail fast", "Throws missing-credentials guidance for the default auto-configured ModelClient"));

    public static final String APPLICATION_PROPERTIES_SNIPPET = String.join("\n", List.of(
        "coderhino.agent.provider-api-type=OPENAI",
        "coderhino.agent.model=gpt-4.1-mini",
        "coderhino.agent.context-window=65536",
        "coderhino.agent.max-output-tokens=4096",
        "coderhino.agent.api-base-url=https://api.openai.com/v1",
        "coderhino.agent.api-key=replace-with-your-api-key"));

    public static final String CREDENTIAL_PROVIDER_EXAMPLE = """
        interface ExternalSecretService {
            String lookupCoderhinoApiKey();
        }

        @Configuration(proxyBeanMethods = false)
        class CredentialProviderConfiguration {
            @Bean
            CoderhinoAgentCredentialProvider coderhinoAgentCredentialProvider(ExternalSecretService externalSecretService) {
                return externalSecretService::lookupCoderhinoApiKey;
            }
        }
        """;

    private AgentPropertiesExample() {
    }

    private static List<PropertySpec> createPropertyMatrix() {
        var properties = new ArrayList<PropertySpec>();
        properties.add(new PropertySpec(MODEL, "String", "MiniMax-M2.7", "Applied to the auto-configured agent and default model client", "wired"));
        properties.add(new PropertySpec(CWD, "Path", "current working directory", "Applied to the embedded ServiceRegistry and agent cwd", "wired"));
        properties.add(new PropertySpec(PERMISSION_MODE, "PermissionMode", "DEFAULT", "Applied to the auto-configured agent", "wired"));
        properties.add(new PropertySpec(ENABLED_TOOLS, "List<String>", "[]", "Empty uses safe embedded tools; non-empty filters the default registry", "wired"));
        properties.add(new PropertySpec(CUSTOM_SYSTEM_PROMPT, "String", "null", "Applied to the auto-configured agent", "wired"));
        properties.add(new PropertySpec(APPEND_SYSTEM_PROMPT, "String", "null", "Applied to the auto-configured agent", "wired"));
        properties.add(new PropertySpec(MAX_TOOL_ITERATIONS, "int", "200", "Applied to the auto-configured agent", "wired"));
        properties.add(new PropertySpec(MAX_BUDGET_USD, "double", "0.0", "Applied to the auto-configured agent", "wired"));
        properties.add(new PropertySpec(EMBEDDED_INTEGRATIONS_ENABLED, "boolean", "false", "Present and bindable on CoderhinoAgentProperties, but not consumed by current auto-configuration", "present_not_wired"));
        properties.add(new PropertySpec(API_KEY, "String", "null", "Used for the default ModelClient or can be replaced by a host-provided ModelClient bean", "wired"));
        properties.add(new PropertySpec(API_BASE_URL, "String", "provider default when omitted", "Applied to the default ModelClient; explicit values override provider-aware defaults", "wired"));
        properties.add(new PropertySpec(PROVIDER_API_TYPE, "ProviderApiType", CoderhinoAgentProperties.ProviderApiType.CLAUDE_CODE.name(), "Applied to the default ModelClient factory and default base URL selection", "wired"));
        properties.add(new PropertySpec(CONTEXT_WINDOW, "long", "128000", "Applied to the default ModelClient factory", "wired"));
        properties.add(new PropertySpec(MAX_OUTPUT_TOKENS, "long", "128000", "Applied to the default ModelClient factory and agent config", "wired"));
        return List.copyOf(properties);
    }

    public static Map<String, String> recommendedConfiguration() {
        var properties = new LinkedHashMap<String, String>();
        properties.put(API_KEY, "replace-with-your-api-key");
        properties.put(MODEL, "gpt-4.1-mini");
        properties.put(CWD, "/workspace/project");
        properties.put(PERMISSION_MODE, "BYPASS");
        properties.put(ENABLED_TOOLS, "read_file,grep");
        properties.put(CUSTOM_SYSTEM_PROMPT, "You are running inside a host Spring Boot application.");
        properties.put(APPEND_SYSTEM_PROMPT, "Prefer project-local conventions.");
        properties.put(MAX_TOOL_ITERATIONS, "12");
        properties.put(MAX_BUDGET_USD, "0.50");
        properties.put(API_BASE_URL, "https://api.openai.com/v1");
        properties.put(PROVIDER_API_TYPE, CoderhinoAgentProperties.ProviderApiType.OPENAI.name());
        properties.put(CONTEXT_WINDOW, "65536");
        properties.put(MAX_OUTPUT_TOKENS, "4096");
        return Map.copyOf(properties);
    }

    public record PropertySpec(
        String key,
        String type,
        String defaultValue,
        String autoConfigurationBehavior,
        String wiringStatus
    ) {
    }

    public record CredentialPrecedenceSpec(
        int order,
        String source,
        String behavior
    ) {
    }
}
