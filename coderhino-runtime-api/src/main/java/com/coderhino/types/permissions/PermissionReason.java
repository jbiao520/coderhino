package com.coderhino.types.permissions;

import java.util.Optional;

public sealed interface PermissionReason permits
        PermissionReason.Allowed,
        PermissionReason.Denied,
        PermissionReason.Ask,
        PermissionReason.Bypassed,
        PermissionReason.RuleMatch,
        PermissionReason.DefaultDeny {

    String message();

    Optional<String> ruleId();

    record Allowed(String message) implements PermissionReason {
        @Override
        public Optional<String> ruleId() {
            return Optional.empty();
        }
    }

    record Denied(String message) implements PermissionReason {
        @Override
        public Optional<String> ruleId() {
            return Optional.empty();
        }
    }

    record Ask(String message) implements PermissionReason {
        @Override
        public Optional<String> ruleId() {
            return Optional.empty();
        }
    }

    record Bypassed(String message) implements PermissionReason {
        @Override
        public Optional<String> ruleId() {
            return Optional.empty();
        }
    }

    record RuleMatch(String message, String matchedRuleId) implements PermissionReason {
        @Override
        public Optional<String> ruleId() {
            return Optional.of(matchedRuleId);
        }
    }

    record DefaultDeny(String message) implements PermissionReason {
        @Override
        public Optional<String> ruleId() {
            return Optional.empty();
        }
    }
}
