package com.coderhino.query;

import com.coderhino.config.credentials.ApiCredentials;

public enum ProviderApiType {
    CLAUDE_CODE,
    OPENAI;

    public static ProviderApiType fromStoredValue(String value) {
        return ApiCredentials.ApiProvider.API_TYPE_OPENAI.equals(ApiCredentials.ApiProvider.normalizeApiType(value))
            ? OPENAI
            : CLAUDE_CODE;
    }

    public String toStoredValue() {
        return name();
    }

    public String defaultBaseUrl() {
        return this == OPENAI ? "https://api.openai.com" : "https://api.anthropic.com";
    }
}
