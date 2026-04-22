package com.coderhino.tools.builtin;

import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.util.Map;

public final class TeamDeleteTool implements ToolDefinition<TeamDeleteTool.Input, TeamDeleteTool.Output> {

    @Override
    public String name() {
        return "team_delete";
    }

    @Override
    public String description() {
        return "Delete a named team or session group by name";
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "teamName", Map.of("type", "string", "description", "The name of the team to delete")
        ));
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input.teamName() == null || input.teamName().isBlank()) {
            return PermissionResult.deny("teamName must not be blank.");
        }
        return PermissionResult.allow();
    }

    @Override
    public Output execute(Input input, ToolContext context) {
        var removed = TeamCreateTool.TEAMS.remove(input.teamName());
        if (removed != null) {
            return new Output(input.teamName(), "deleted");
        }
        return new Output(input.teamName(), "not_found");
    }

    public record Input(String teamName) {
        public Input {
            if (teamName != null) teamName = teamName.strip();
        }
    }

    public record Output(String teamName, String status) {
    }
}
