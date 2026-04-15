package com.coderhino.tools.builtin;

import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TeamCreateTool implements ToolDefinition<TeamCreateTool.Input, TeamCreateTool.Output> {

    static final ConcurrentHashMap<String, List<String>> TEAMS = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "team_create";
    }

    @Override
    public String description() {
        return "Create a named team or session group with an optional list of member agent IDs";
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "teamName", Map.of("type", "string", "description", "The unique name for the team"),
            "members", Map.of("type", "array", "description", "Optional list of member agent IDs")
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
        List<String> members = (input.members() != null) ? new ArrayList<>(input.members()) : new ArrayList<>();
        var existing = TEAMS.putIfAbsent(input.teamName(), members);
        if (existing != null) {
            return new Output(input.teamName(), Collections.unmodifiableList(existing), "already_exists");
        }
        return new Output(input.teamName(), Collections.unmodifiableList(members), "created");
    }

    public static void clearTeams() {
        TEAMS.clear();
    }

    public record Input(String teamName, List<String> members) {
        public Input {
            if (teamName != null) teamName = teamName.strip();
        }
    }

    public record Output(String teamName, List<String> members, String status) {
    }
}
