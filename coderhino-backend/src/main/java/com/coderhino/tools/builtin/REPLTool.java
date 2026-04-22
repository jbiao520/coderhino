package com.coderhino.tools.builtin;

import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.util.List;
import java.util.Map;

public final class REPLTool implements ToolDefinition<REPLTool.Input, REPLTool.Output> {

    @Override
    public String name() {
        return "repl";
    }

    @Override
    public String description() {
        return "Returns the list of primitive tool names available in the REPL context.";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of());
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        return PermissionResult.allow();
    }

    @Override
    public Output execute(Input input, ToolContext context) throws Exception {
        List<String> primitiveTools = List.of(
            "read_file",
            "write_file",
            "edit_file",
            "glob",
            "grep",
            "bash",
            "notebook_edit",
            "agent"
        );
        return new Output(primitiveTools);
    }

    public record Input() {
    }

    public record Output(List<String> primitiveTools) {
    }
}
