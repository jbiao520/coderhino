package com.coderhino.tools.builtin;

import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

public final class NotebookEditTool implements ToolDefinition<NotebookEditTool.Input, NotebookEditTool.Output> {

    @Override
    public String name() {
        return "notebook_edit";
    }

    @Override
    public String description() {
        return "Edit a Jupyter notebook (.ipynb) cell by replacing, inserting, or deleting content";
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "notebook_path", Map.of("type", "string"),
            "cell_id", Map.of("type", "string"),
            "new_source", Map.of("type", "string"),
            "cell_type", Map.of("type", "string"),
            "edit_mode", Map.of("type", "string")
        ));
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input.notebook_path() == null || input.notebook_path().isBlank()) {
            return PermissionResult.deny("notebook_path must not be blank.");
        }
        if (!input.notebook_path().endsWith(".ipynb")) {
            return PermissionResult.deny("File must be a Jupyter notebook (.ipynb file)");
        }

        String effectiveMode = (input.edit_mode() == null || input.edit_mode().isBlank()) ? "replace" : input.edit_mode().trim();

        if (!effectiveMode.equals("replace") && !effectiveMode.equals("insert") && !effectiveMode.equals("delete")) {
            return PermissionResult.deny("edit_mode must be one of: replace, insert, delete");
        }

        if (effectiveMode.equals("insert") && (input.cell_type() == null || input.cell_type().isBlank())) {
            return PermissionResult.deny("cell_type is required when edit_mode is insert");
        }

        if (!effectiveMode.equals("insert") && (input.cell_id() == null || input.cell_id().isBlank())) {
            return PermissionResult.deny("cell_id is required for replace and delete modes");
        }

        return PermissionResult.allow();
    }

    @Override
    public Output execute(Input input, ToolContext context) throws Exception {
        String effectiveMode = (input.edit_mode() == null || input.edit_mode().isBlank()) ? "replace" : input.edit_mode().trim();
        String notebookPath = input.notebook_path();
        Path target = Path.of(notebookPath);
        if (!target.isAbsolute()) {
            target = Path.of(context.bootstrapState().cwd()).resolve(target).normalize();
        }

        String originalContent = Files.readString(target);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode notebook = (ObjectNode) mapper.readTree(originalContent);
        ArrayNode cells = (ArrayNode) notebook.get("cells");

        int cellIndex = 0;
        String resolvedCellId = input.cell_id();

        if ("insert".equals(effectiveMode) && (resolvedCellId == null || resolvedCellId.isBlank())) {
            cellIndex = 0;
        } else if (resolvedCellId != null && !resolvedCellId.isBlank()) {
            boolean found = false;
            for (int i = 0; i < cells.size(); i++) {
                JsonNode cell = cells.get(i);
                JsonNode idNode = cell.get("id");
                if (idNode != null && resolvedCellId.equals(idNode.asText())) {
                    cellIndex = i;
                    found = true;
                    break;
                }
            }
            if (!found && resolvedCellId.startsWith("cell-")) {
                try {
                    cellIndex = Integer.parseInt(resolvedCellId.substring(5));
                    if (cellIndex >= 0 && cellIndex < cells.size()) {
                        found = true;
                    }
                } catch (NumberFormatException ignored) {}
            }
            if (!found) {
                if (!"insert".equals(effectiveMode)) {
                    return new Output(resolvedCellId, null, null, effectiveMode, input.new_source(),
                        "Cell not found: " + resolvedCellId, notebookPath, originalContent, originalContent);
                }
            }
            if ("insert".equals(effectiveMode) && found) {
                cellIndex = cellIndex + 1;
            }
        }

        String finalMode = effectiveMode;
        String finalCellType = input.cell_type();
        if ("replace".equals(effectiveMode) && cellIndex == cells.size()) {
            finalMode = "insert";
            if (finalCellType == null || finalCellType.isBlank()) finalCellType = "code";
        }

        String usedCellId = resolvedCellId;
        String usedCellType = finalCellType;

        if ("replace".equals(finalMode)) {
            ObjectNode cell = (ObjectNode) cells.get(cellIndex);
            cell.put("source", input.new_source());
            if (finalCellType != null && !finalCellType.isBlank()) {
                cell.put("cell_type", finalCellType);
                usedCellType = finalCellType;
            } else {
                JsonNode ct = cell.get("cell_type");
                usedCellType = ct != null ? ct.asText() : "code";
            }
            if ("code".equals(usedCellType)) {
                cell.putNull("execution_count");
                cell.putArray("outputs");
            }
            JsonNode idNode = cell.get("id");
            usedCellId = idNode != null ? idNode.asText() : resolvedCellId;
        } else if ("insert".equals(finalMode)) {
            ObjectNode newCell = mapper.createObjectNode();
            String ct = (finalCellType != null && !finalCellType.isBlank()) ? finalCellType : "code";
            newCell.put("cell_type", ct);
            newCell.put("source", input.new_source());
            JsonNode nbformatMinor = notebook.get("nbformat_minor");
            int minor = nbformatMinor != null ? nbformatMinor.asInt(0) : 0;
            if (minor >= 5) {
                String newId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                newCell.put("id", newId);
                usedCellId = newId;
            }
            newCell.set("metadata", mapper.createObjectNode());
            if ("code".equals(ct)) {
                newCell.putNull("execution_count");
                newCell.putArray("outputs");
            }
            usedCellType = ct;
            ArrayNode newCells = mapper.createArrayNode();
            for (int i = 0; i < cells.size(); i++) {
                if (i == cellIndex) newCells.add(newCell);
                newCells.add(cells.get(i));
            }
            if (cellIndex >= cells.size()) newCells.add(newCell);
            notebook.set("cells", newCells);
        } else {
            ArrayNode newCells = mapper.createArrayNode();
            for (int i = 0; i < cells.size(); i++) {
                if (i != cellIndex) newCells.add(cells.get(i));
            }
            notebook.set("cells", newCells);
        }

        String language = "python";
        JsonNode metadata = notebook.get("metadata");
        if (metadata != null) {
            JsonNode langInfo = metadata.get("language_info");
            if (langInfo != null) {
                JsonNode langName = langInfo.get("name");
                if (langName != null) language = langName.asText();
            }
        }

        String updatedContent = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(notebook);
        Files.writeString(target, updatedContent);

        return new Output(usedCellId, usedCellType, language, finalMode, input.new_source(),
            null, notebookPath, originalContent, updatedContent);
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    public record Input(
        String notebook_path,
        String cell_id,
        String new_source,
        String cell_type,
        String edit_mode
    ) {}

    public record Output(
        String cell_id,
        String cell_type,
        String language,
        String edit_mode,
        String new_source,
        String error,
        String notebook_path,
        String original_file,
        String updated_file
    ) {}
}
