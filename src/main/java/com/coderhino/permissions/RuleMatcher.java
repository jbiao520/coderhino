package com.coderhino.permissions;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Matches tool invocations against rule specifications for tool-name,
 * prefix, and content matching. Deterministic and stateless for thread-safety.
 */
public final class RuleMatcher {

    /**
     * A single permission rule specifying matching criteria for a tool invocation.
     *
     * @param toolPattern   exact tool name or pattern (null = any tool)
     * @param prefixPattern prefix to match at start of content (null = any prefix)
     * @param contentPattern regex pattern to match in content (null = any content)
     */
    public record Rule(
            String toolPattern,
            String prefixPattern,
            String contentPattern
    ) {
        public Rule {
            if (toolPattern != null && toolPattern.isBlank()) toolPattern = null;
            if (prefixPattern != null && prefixPattern.isBlank()) prefixPattern = null;
            if (contentPattern != null && contentPattern.isBlank()) contentPattern = null;
        }

        /**
         * Parses a rule from string format: "tool:prefix:content" where any field may be "*" for "any".
         */
        public static Rule parse(String spec) {
            if (spec == null || spec.isBlank()) {
                return new Rule(null, null, null);
            }
            String[] parts = spec.split(":", 3);
            String tool = parts[0].equals("*") ? null : parts[0];
            String prefix = parts.length < 2 || parts[1].equals("*") ? null : parts[1];
            String content = parts.length < 3 || parts[2].equals("*") ? null : parts[2];
            return new Rule(tool, prefix, content);
        }
    }

    /**
     * Result of matching a rule against an invocation.
     */
    public enum MatchResult {
        NO_MATCH,
        TOOL_MATCH,
        TOOL_PREFIX_MATCH,
        FULL_MATCH
    }

    /**
     * Matches an invocation against a single rule.
     * Returns detailed match result for downstream logic.
     */
    public MatchResult matches(Rule rule, String toolName, String content) {
        Objects.requireNonNull(toolName, "toolName must not be null");

        boolean toolMatches = rule.toolPattern() == null
                || rule.toolPattern().equals(toolName)
                || toolName.equals("bash") && rule.toolPattern().equals("shell");

        if (!toolMatches) {
            return MatchResult.NO_MATCH;
        }

        boolean prefixMatches = rule.prefixPattern() == null
                || (content != null && content.startsWith(rule.prefixPattern()));

        boolean contentMatches = rule.contentPattern() == null
                || (content != null && Pattern.compile(rule.contentPattern()).matcher(content).find());

        if (rule.prefixPattern() != null && rule.contentPattern() != null) {
            return (prefixMatches && contentMatches) ? MatchResult.FULL_MATCH : MatchResult.NO_MATCH;
        }
        if (rule.prefixPattern() != null) {
            return prefixMatches ? MatchResult.TOOL_PREFIX_MATCH : MatchResult.NO_MATCH;
        }
        if (rule.contentPattern() != null) {
            return contentMatches ? MatchResult.TOOL_PREFIX_MATCH : MatchResult.NO_MATCH;
        }
        return MatchResult.TOOL_MATCH;
    }

    /**
     * Checks if any rule in the list matches the invocation.
     */
    public boolean matchesAny(Iterable<Rule> rules, String toolName, String content) {
        for (Rule rule : rules) {
            if (matches(rule, toolName, content) != MatchResult.NO_MATCH) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the first matching rule, or null if none match.
     */
    public Rule findFirstMatch(Iterable<Rule> rules, String toolName, String content) {
        for (Rule rule : rules) {
            if (matches(rule, toolName, content) != MatchResult.NO_MATCH) {
                return rule;
            }
        }
        return null;
    }
}