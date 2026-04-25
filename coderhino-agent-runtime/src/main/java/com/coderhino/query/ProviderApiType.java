package com.coderhino.query;

import com.coderhino.config.credentials.ApiCredentials;

public enum ProviderApiType {
    CLAUDE_CODE,
    OPENAI;

    private static final String OPENAI_UNSUPPORTED_MESSAGE =
        "OpenAI-compatible requests are not supported in this release. Configure CLAUDE_CODE or inject a custom ModelClient.";

    public static ProviderApiType fromStoredValue(String value) {
        if (ApiCredentials.ApiProvider.API_TYPE_OPENAI.equals(ApiCredentials.ApiProvider.normalizeApiType(value))) {
            throw unsupportedOpenAi();
        }
        return CLAUDE_CODE;
    }

    public String toStoredValue() {
        return name();
    }

    public String defaultBaseUrl() {
        if (this == OPENAI) {
            throw unsupportedOpenAi();
        }
        return "https://api.anthropic.com";
    }

    public static IllegalArgumentException unsupportedOpenAi() {
        return new IllegalArgumentException(OPENAI_UNSUPPORTED_MESSAGE);
    }
}
