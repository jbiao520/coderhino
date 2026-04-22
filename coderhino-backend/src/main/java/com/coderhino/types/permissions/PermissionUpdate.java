package com.coderhino.types.permissions;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public record PermissionUpdate(
        String toolName,
        boolean allowed,
        String matchedRuleId,
        Instant timestamp,
        Map<String, Object> metadata
) {
    public PermissionUpdate(String toolName, boolean allowed) {
        this(toolName, allowed, null, Instant.now(), Map.of());
    }

    public PermissionUpdate(String toolName, boolean allowed, String matchedRuleId) {
        this(toolName, allowed, matchedRuleId, Instant.now(), Map.of());
    }

    public Optional<String> ruleId() {
        return Optional.ofNullable(matchedRuleId);
    }
}