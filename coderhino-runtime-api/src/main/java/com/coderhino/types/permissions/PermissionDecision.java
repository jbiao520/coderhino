package com.coderhino.types.permissions;

import com.coderhino.types.PermissionMode;

import java.time.Instant;
import java.util.Optional;

public record PermissionDecision(
        boolean allowed,
        PermissionReason reason,
        PermissionMode mode,
        String matchedRuleId,
        Instant evaluatedAt
) {
    public PermissionDecision(boolean allowed, PermissionReason reason, PermissionMode mode) {
        this(allowed, reason, mode, null, Instant.now());
    }

    public Optional<String> ruleId() {
        return Optional.ofNullable(matchedRuleId);
    }
}
