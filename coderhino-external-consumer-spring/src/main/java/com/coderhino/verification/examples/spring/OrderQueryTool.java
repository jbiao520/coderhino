package com.coderhino.verification.examples.spring;

import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.util.List;
import java.util.Map;

public final class OrderQueryTool implements ToolDefinition<OrderQueryTool.Input, OrderQueryTool.Output> {
    public static final String TOOL_NAME = "order_query";

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "Return a deterministic mock order response for a host order id";
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "orderId", Map.of("type", "string")
        ));
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input == null || input.orderId() == null || input.orderId().isBlank()) {
            return PermissionResult.deny("orderId must not be blank.");
        }
        return PermissionResult.allow();
    }

    @Override
    public Output execute(Input input, ToolContext context) {
        return new Output(
            input.orderId().trim(),
            "MOCK_CONFIRMED",
            "Ada Lovelace",
            "USD",
            "129.99",
            List.of(new LineItem("SKU-COFFEE-001", "Coderhino Coffee Beans", 1))
        );
    }

    public record Input(String orderId) {
    }

    public record Output(
        String orderId,
        String status,
        String customerName,
        String currency,
        String total,
        List<LineItem> lineItems
    ) {
    }

    public record LineItem(String sku, String name, int quantity) {
    }
}
