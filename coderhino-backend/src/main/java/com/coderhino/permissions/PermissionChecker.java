package com.coderhino.permissions;

import com.coderhino.types.PermissionMode;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.permissions.PermissionContext;
import com.coderhino.types.permissions.PermissionDecision;
import com.coderhino.types.permissions.PermissionReason;
import com.coderhino.types.permissions.PermissionRule;

import java.util.List;

public final class PermissionChecker {

    public PermissionResult resolve(PermissionMode mode, PermissionResult requested) {
        if (mode == PermissionMode.BYPASS) {
            return PermissionResult.allow();
        }

        if (requested instanceof PermissionResult.Deny deny) {
            return deny;
        }

        if (requested instanceof PermissionResult.Ask ask) {
            return ask;
        }

        return PermissionResult.allow();
    }

    public PermissionDecision evaluate(PermissionContext context, List<PermissionRule> rules) {
        switch (context.mode()) {
            case BYPASS:
                return new PermissionDecision(
                    true,
                    new PermissionReason.Bypassed("Bypass mode"),
                    context.mode()
                );

            case DONT_ASK:
                PermissionDecision dontAskResult = evaluateWithRules(context, rules);
                if (dontAskResult == null) {
                    return new PermissionDecision(
                        false,
                        new PermissionReason.DefaultDeny("No matching rule in DONT_ASK mode"),
                        context.mode()
                    );
                }
                return dontAskResult;

            case ACCEPT_EDITS:
                boolean isEditTool = "edit_file".equals(context.toolName()) || "write_file".equals(context.toolName());
                if (isEditTool) {
                    return new PermissionDecision(
                        true,
                        new PermissionReason.Allowed("Accept edits mode allows file modifications"),
                        context.mode()
                    );
                }
                PermissionDecision acceptEditsResult = evaluateWithRules(context, rules);
                if (acceptEditsResult == null) {
                    return new PermissionDecision(
                        false,
                        new PermissionReason.Ask("Destructive operation requires confirmation in ACCEPT_EDITS mode"),
                        context.mode()
                    );
                }
                return acceptEditsResult;

            case PLAN:
                return new PermissionDecision(
                    false,
                    new PermissionReason.Denied("Plan mode restricts operations"),
                    context.mode()
                );

            case AUTO:
            case DEFAULT:
            default:
                PermissionDecision defaultResult = evaluateWithRules(context, rules);
                if (defaultResult != null) {
                    return defaultResult;
                }
                return new PermissionDecision(
                    false,
                    new PermissionReason.Ask("Permission required"),
                    context.mode()
                );
        }
    }

    private PermissionDecision evaluateWithRules(PermissionContext context, List<PermissionRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        return PermissionRule.evaluateAll(rules, context);
    }

    public PermissionResult toPermissionResult(PermissionDecision decision) {
        if (decision.allowed()) {
            return PermissionResult.allow();
        }

        PermissionReason reason = decision.reason();
        if (reason instanceof PermissionReason.Ask) {
            return PermissionResult.ask(reason.message());
        }

        return PermissionResult.deny(reason.message());
    }
}
