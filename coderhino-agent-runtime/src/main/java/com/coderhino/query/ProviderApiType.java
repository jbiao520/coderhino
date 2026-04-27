package com.coderhino.query;

import com.coderhino.config.credentials.ApiCredentials;

public enum ProviderApiType {
    CLAUDE_CODE,
    OPENAI;

    public static ProviderApiType fromStoredValue(String value) {
        if (ApiCredentials.ApiProvider.API_TYPE_OPENAI.equals(ApiCredentials.ApiProvider.normalizeApiType(value))) {
            return OPENAI;
        }
        return CLAUDE_CODE;
    }

    public String toStoredValue() {
        return name();
    }

    public String defaultBaseUrl() {
        if (this == OPENAI) {
            return "https://api.openai.com";
        }
        return "https://api.anthropic.com";
    }
}
