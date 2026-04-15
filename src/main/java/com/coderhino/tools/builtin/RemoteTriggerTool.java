package com.coderhino.tools.builtin;

import com.coderhino.services.analytics.FeatureFlag;
import com.coderhino.services.trigger.RemoteTriggerService;
import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.util.Map;
import java.util.UUID;

public final class RemoteTriggerTool implements ToolDefinition<RemoteTriggerTool.Input, RemoteTriggerTool.Output> {

    @Override
    public String name() {
        return "remote_trigger";
    }

    @Override
    public String description() {
        return "Dispatch a webhook-style remote trigger event to registered handlers. Accepts event type and payload.";
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "event", Map.of("type", "string",
                "description", "The event type to dispatch (e.g. 'build.completed', 'deploy.started')"),
            "payload", Map.of("type", "object",
                "description", "The event payload as a key-value map")
        ));
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input.event() == null || input.event().isBlank()) {
            return PermissionResult.deny("event must not be blank.");
        }
        if (input.payload() == null) {
            return PermissionResult.deny("payload must not be null.");
        }
        return PermissionResult.allow();
    }

    @Override
    public Output execute(Input input, ToolContext context) throws Exception {
        RemoteTriggerService triggerService = context.services().remoteTriggerService();
        boolean hasHandler = triggerService.isRegistered(input.event());
        triggerService.dispatch(input.event(), input.payload());
        String dispatchId = UUID.randomUUID().toString();
        String status = hasHandler ? "dispatched" : "dispatched:no-handler";
        return new Output(dispatchId, input.event(), input.payload(), status);
    }

    public record Input(String event, Map<String, Object> payload) {
        public Input {
            if (event != null) event = event.strip();
        }
    }

    public record Output(String dispatchId, String event, Map<String, Object> payload, String status) {
    }
}
