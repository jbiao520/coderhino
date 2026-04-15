package com.coderhino.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks API usage costs across models based on Anthropic's pricing tiers.
 * <p>
 * Pricing tiers (per million tokens):
 * <ul>
 *   <li>Haiku: $0.80 input / $4.00 output (cache read $0.08, cache write $1.00)</li>
 *   <li>Sonnet: $3.00 input / $15.00 output (cache read $0.30, cache write $3.75)</li>
 *   <li>Opus 4.1: $15.00 input / $75.00 output (cache read $1.50, cache write $18.75)</li>
 *   <li>Opus 4.5/4.6: $5.00 input / $25.00 output (cache read $0.50, cache write $6.25)</li>
 * </ul>
 * Unknown models fall back to the Opus 4.5/4.6 pricing tier.
 */
public final class CostTracker {


    private static final double HAIKU_INPUT = 0.80;
    private static final double HAIKU_OUTPUT = 4.00;
    private static final double HAIKU_CACHE_READ = 0.08;
    private static final double HAIKU_CACHE_WRITE = 1.00;

    private static final double SONNET_INPUT = 3.00;
    private static final double SONNET_OUTPUT = 15.00;
    private static final double SONNET_CACHE_READ = 0.30;
    private static final double SONNET_CACHE_WRITE = 3.75;

    private static final double OPUS_INPUT = 15.00;
    private static final double OPUS_OUTPUT = 75.00;
    private static final double OPUS_CACHE_READ = 1.50;
    private static final double OPUS_CACHE_WRITE = 18.75;

    private static final double OPUS_LOW_INPUT = 5.00;
    private static final double OPUS_LOW_OUTPUT = 25.00;
    private static final double OPUS_LOW_CACHE_READ = 0.50;
    private static final double OPUS_LOW_CACHE_WRITE = 6.25;

    private static final double DEFAULT_INPUT = OPUS_LOW_INPUT;
    private static final double DEFAULT_OUTPUT = OPUS_LOW_OUTPUT;
    private static final double DEFAULT_CACHE_READ = OPUS_LOW_CACHE_READ;
    private static final double DEFAULT_CACHE_WRITE = OPUS_LOW_CACHE_WRITE;

    public record ModelUsage(long inputTokens, long outputTokens, double costUsd) {
        public ModelUsage {
            if (inputTokens < 0) inputTokens = 0;
            if (outputTokens < 0) outputTokens = 0;
            if (Double.isNaN(costUsd) || costUsd < 0) costUsd = 0;
        }

        public ModelUsage add(long moreInput, long moreOutput, double moreCost) {
            return new ModelUsage(
                inputTokens + moreInput,
                outputTokens + moreOutput,
                costUsd + moreCost
            );
        }
    }

    /**
     * Snapshot of accumulated usage suitable for syncing with AppState.
     * Provides all fields needed by AppState.addUsage(long, long, long, long, double).
     */
    public record UsageSnapshot(
        double totalCostUsd,
        long totalInputTokens,
        long totalOutputTokens,
        long totalCacheReadTokens,
        long totalCacheWriteTokens,
        int totalToolUses
    ) {}

    /**
     * Records a single turn's usage for detailed per-turn tracking.
     */
    public record TurnRecord(
        int turnIndex,
        String model,
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens,
        double costUsd,
        Instant timestamp
    ) {}

    public record StoredCostState(
        double totalCostUsd,
        long totalInputTokens,
        long totalOutputTokens,
        long totalLinesAdded,
        long totalLinesRemoved,
        Map<String, ModelUsage> modelUsage
    ) {
        public StoredCostState {
            modelUsage = modelUsage != null ? new LinkedHashMap<>(modelUsage) : new LinkedHashMap<>();
        }
    }

    private final ObjectMapper objectMapper;
    private final Path storagePath;

    private final Map<String, ModelUsage> modelUsage = new LinkedHashMap<>();
    private double totalCostUsd = 0;
    private long totalInputTokens = 0;
    private long totalOutputTokens = 0;
    private long totalLinesAdded = 0;
    private long totalLinesRemoved = 0;
    private boolean hasUnknownModel = false;

    public CostTracker() {
        this(null);
    }

    public CostTracker(Path storagePath) {
        this(defaultObjectMapper(), storagePath);
    }

    public CostTracker(ObjectMapper objectMapper, Path storagePath) {
        this.objectMapper = objectMapper;
        this.storagePath = storagePath;
        loadFromDisk();
    }

    public double addUsage(String model, long inputTokens, long outputTokens) {
        return addUsage(model, inputTokens, outputTokens, 0, 0);
    }

    public double addUsage(String model, long inputTokens, long outputTokens,
                          long cacheReadTokens, long cacheWriteTokens) {
        double cost = calculateCost(model, inputTokens, outputTokens, cacheReadTokens, cacheWriteTokens);
        addToAccumulator(model, inputTokens, outputTokens, cost);
        return cost;
    }

    public void addLinesChanged(long linesAdded, long linesRemoved) {
        if (linesAdded > 0) totalLinesAdded += linesAdded;
        if (linesRemoved > 0) totalLinesRemoved += linesRemoved;
    }

    public double totalCostUsd() {
        return totalCostUsd;
    }

    public long totalInputTokens() {
        return totalInputTokens;
    }

    public long totalOutputTokens() {
        return totalOutputTokens;
    }

    public long totalLinesAdded() {
        return totalLinesAdded;
    }

    public long totalLinesRemoved() {
        return totalLinesRemoved;
    }

    public boolean hasUnknownModel() {
        return hasUnknownModel;
    }

    public ModelUsage getModelUsage(String model) {
        String canonical = canonicalName(model);
        return modelUsage.getOrDefault(canonical, new ModelUsage(0, 0, 0));
    }

    public Map<String, ModelUsage> allModelUsage() {
        return new LinkedHashMap<>(modelUsage);
    }

    public double calculateCost(String model, long inputTokens, long outputTokens,
                                 long cacheReadTokens, long cacheWriteTokens) {
        double inputCost = (inputTokens / 1_000_000.0) * getInputPrice(model);
        double outputCost = (outputTokens / 1_000_000.0) * getOutputPrice(model);
        double cacheReadCost = (cacheReadTokens / 1_000_000.0) * getCacheReadPrice(model);
        double cacheWriteCost = (cacheWriteTokens / 1_000_000.0) * getCacheWritePrice(model);
        return inputCost + outputCost + cacheReadCost + cacheWriteCost;
    }

    public void save() {
        if (storagePath == null) {
            return;
        }
        var state = new StoredCostState(
            totalCostUsd,
            totalInputTokens,
            totalOutputTokens,
            totalLinesAdded,
            totalLinesRemoved,
            modelUsage
        );
        try {
            if (storagePath.getParent() != null) {
                Files.createDirectories(storagePath.getParent());
            }
            Files.writeString(storagePath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(state));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist cost state: %s".formatted(exception.getMessage()), exception);
        }
    }

    public boolean restore() {
        if (storagePath == null || !Files.exists(storagePath)) {
            return false;
        }
        try {
            var state = objectMapper.readValue(Files.readString(storagePath), StoredCostState.class);
            totalCostUsd = state.totalCostUsd();
            totalInputTokens = state.totalInputTokens();
            totalOutputTokens = state.totalOutputTokens();
            totalLinesAdded = state.totalLinesAdded();
            totalLinesRemoved = state.totalLinesRemoved();
            modelUsage.clear();
            modelUsage.putAll(state.modelUsage());
            hasUnknownModel = !modelUsage.isEmpty() && modelUsage.values().stream().noneMatch(u -> u.inputTokens() > 0 || u.outputTokens() > 0);
            return true;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load cost state: %s".formatted(exception.getMessage()), exception);
        }
    }

    public void reset() {
        totalCostUsd = 0;
        totalInputTokens = 0;
        totalOutputTokens = 0;
        totalLinesAdded = 0;
        totalLinesRemoved = 0;
        hasUnknownModel = false;
        modelUsage.clear();
    }

    public String formatCost(double cost) {
        return cost > 0.5
            ? "$" + Math.round(cost * 100.0) / 100.0
            : "$" + String.format("%.4f", cost);
    }

    public String formatSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Total cost: ").append(formatCost(totalCostUsd));
        if (hasUnknownModel) {
            sb.append(" (costs may be inaccurate due to usage of unknown models)");
        }
        sb.append("\n");
        sb.append("Input tokens: ").append(totalInputTokens).append("\n");
        sb.append("Output tokens: ").append(totalOutputTokens).append("\n");
        sb.append("Lines added: ").append(totalLinesAdded);
        sb.append(", removed: ").append(totalLinesRemoved).append("\n");

        if (!modelUsage.isEmpty()) {
            sb.append("Usage by model:\n");
            for (Map.Entry<String, ModelUsage> entry : modelUsage.entrySet()) {
                ModelUsage usage = entry.getValue();
                sb.append("  ")
                    .append(entry.getKey())
                    .append(": ")
                    .append(usage.inputTokens())
                    .append(" input, ")
                    .append(usage.outputTokens())
                    .append(" output (")
                    .append(formatCost(usage.costUsd()))
                    .append(")\n");
            }
        }
        return sb.toString();
    }

    // Package-private for testing
    String canonicalName(String model) {
        if (model == null) return "unknown";
        String lower = model.toLowerCase();
        if (lower.contains("haiku")) return "haiku";
        if (lower.contains("sonnet")) return "sonnet";
        if (lower.contains("opus-4-1") || lower.contains("opus_4_1")) return "opus_4_1";
        if (lower.contains("opus")) return "opus_low"; // Opus 4.5/4.6 tier

        hasUnknownModel = true;
        return "unknown";
    }

    private double getInputPrice(String model) {
        String canonical = canonicalName(model);
        return switch (canonical) {
            case "haiku" -> HAIKU_INPUT;
            case "sonnet" -> SONNET_INPUT;
            case "opus_4_1" -> OPUS_INPUT;
            case "opus_low" -> OPUS_LOW_INPUT;
            default -> DEFAULT_INPUT;
        };
    }

    private double getOutputPrice(String model) {
        String canonical = canonicalName(model);
        return switch (canonical) {
            case "haiku" -> HAIKU_OUTPUT;
            case "sonnet" -> SONNET_OUTPUT;
            case "opus_4_1" -> OPUS_OUTPUT;
            case "opus_low" -> OPUS_LOW_OUTPUT;
            default -> DEFAULT_OUTPUT;
        };
    }

    private double getCacheReadPrice(String model) {
        String canonical = canonicalName(model);
        return switch (canonical) {
            case "haiku" -> HAIKU_CACHE_READ;
            case "sonnet" -> SONNET_CACHE_READ;
            case "opus_4_1" -> OPUS_CACHE_READ;
            case "opus_low" -> OPUS_LOW_CACHE_READ;
            default -> DEFAULT_CACHE_READ;
        };
    }

    private double getCacheWritePrice(String model) {
        String canonical = canonicalName(model);
        return switch (canonical) {
            case "haiku" -> HAIKU_CACHE_WRITE;
            case "sonnet" -> SONNET_CACHE_WRITE;
            case "opus_4_1" -> OPUS_CACHE_WRITE;
            case "opus_low" -> OPUS_LOW_CACHE_WRITE;
            default -> DEFAULT_CACHE_WRITE;
        };
    }

    private void addToAccumulator(String model, long inputTokens, long outputTokens, double cost) {
        String canonical = canonicalName(model);
        ModelUsage existing = modelUsage.getOrDefault(canonical, new ModelUsage(0, 0, 0));
        modelUsage.put(canonical, existing.add(inputTokens, outputTokens, cost));

        totalCostUsd += cost;
        this.totalInputTokens += inputTokens;
        this.totalOutputTokens += outputTokens;
    }

    private void loadFromDisk() {
        if (storagePath == null || !Files.exists(storagePath)) {
            return;
        }
        try {
            var state = objectMapper.readValue(Files.readString(storagePath), StoredCostState.class);
            totalCostUsd = state.totalCostUsd();
            totalInputTokens = state.totalInputTokens();
            totalOutputTokens = state.totalOutputTokens();
            totalLinesAdded = state.totalLinesAdded();
            totalLinesRemoved = state.totalLinesRemoved();
            modelUsage.clear();
            modelUsage.putAll(state.modelUsage());
        } catch (IOException exception) {
            // Ignore corrupt files on startup
        }
    }

    private static ObjectMapper defaultObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}
