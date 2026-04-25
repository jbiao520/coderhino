package com.coderhino.types.permissions;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

public sealed interface PermissionRule permits
        PermissionRule.ToolNameRule,
        PermissionRule.PatternRule,
        PermissionRule.AlwaysAllowRule,
        PermissionRule.AlwaysDenyRule {

    String id();

    PermissionDecision evaluate(PermissionContext context);

    record ToolNameRule(String id, String toolName, boolean allow) implements PermissionRule {
        @Override
        public PermissionDecision evaluate(PermissionContext context) {
            boolean matches = toolName.equals(context.toolName());
            if (matches) {
                return new PermissionDecision(
                    allow,
                    allow ? new PermissionReason.Allowed("Tool '" + toolName + "' matched rule")
                           : new PermissionReason.Denied("Tool '" + toolName + "' denied by rule"),
                    context.mode(),
                    id,
                    Instant.now()
                );
            }
            return null;
        }
    }

    record PatternRule(String id, Pattern toolNamePattern, boolean allow) implements PermissionRule {
        @Override
        public PermissionDecision evaluate(PermissionContext context) {
            if (toolNamePattern.matcher(context.toolName()).matches()) {
                return new PermissionDecision(
                    allow,
                    allow ? new PermissionReason.Allowed("Tool '" + context.toolName() + "' matched pattern rule")
                           : new PermissionReason.Denied("Tool '" + context.toolName() + "' denied by pattern rule"),
                    context.mode(),
                    id,
                    Instant.now()
                );
            }
            return null;
        }
    }

    record AlwaysAllowRule(String id) implements PermissionRule {
        @Override
        public PermissionDecision evaluate(PermissionContext context) {
            return new PermissionDecision(
                true,
                new PermissionReason.Allowed("Always allowed by rule"),
                context.mode(),
                id,
                Instant.now()
            );
        }
    }

    record AlwaysDenyRule(String id) implements PermissionRule {
        @Override
        public PermissionDecision evaluate(PermissionContext context) {
            return new PermissionDecision(
                false,
                new PermissionReason.Denied("Always denied by rule"),
                context.mode(),
                id,
                Instant.now()
            );
        }
    }

    static PermissionRule allowTool(String id, String toolName) {
        return new ToolNameRule(id, toolName, true);
    }

    static PermissionRule denyTool(String id, String toolName) {
        return new ToolNameRule(id, toolName, false);
    }

    static PermissionDecision evaluateAll(List<PermissionRule> rules, PermissionContext context) {
        for (PermissionRule rule : rules) {
            PermissionDecision decision = rule.evaluate(context);
            if (decision != null) {
                return decision;
            }
        }
        return null;
    }
}
