package com.yardenzamir.simchat.condition;

import com.yardenzamir.simchat.SimChatMod;

import java.util.HashMap;
import java.util.Map;

/**
 * Evaluates condition strings to determine action visibility.
 * Supports extensible prefixes for different condition types.
 *
 * <p>Syntax:
 * <ul>
 *   <li>{@code kjs:name} - Truthy check on KubeJS callback result</li>
 *   <li>{@code !kjs:name} - Falsy check (negated)</li>
 *   <li>{@code flag:name} - Check if team flag exists and is truthy</li>
 *   <li>{@code !flag:name} - Check if team flag is missing or falsy</li>
 * </ul>
 */
public final class ConditionEvaluator {
    private static final Map<String, ConditionResolver> resolvers = new HashMap<>();

    private ConditionEvaluator() {}

    /**
     * Registers a resolver for a given prefix.
     *
     * @param prefix The prefix (e.g., "kjs" for kjs:name)
     * @param resolver The resolver implementation
     */
    public static void registerResolver(String prefix, ConditionResolver resolver) {
        resolvers.put(prefix, resolver);
        SimChatMod.LOGGER.debug("Registered condition resolver for prefix: {}", prefix);
    }

    /**
     * Removes a resolver by prefix.
     */
    public static void unregisterResolver(String prefix) {
        resolvers.remove(prefix);
    }

    /**
     * Evaluates a condition string.
     *
     * @param condition The condition string (e.g., "kjs:hasHighRep", "!flag:seen_intro")
     * @param ctx The callback context
     * @return true if condition passes, false otherwise
     */
    public static boolean evaluate(String condition, CallbackContext ctx) {
        if (condition == null || condition.isEmpty()) {
            return true; // No condition = always show
        }

        // Handle negation
        boolean negate = false;
        String expr = condition.trim();
        if (expr.startsWith("!")) {
            negate = true;
            expr = expr.substring(1).trim();
        }

        // Parse prefix:name
        int colonIndex = expr.indexOf(':');
        if (colonIndex == -1) {
            SimChatMod.LOGGER.warn("Invalid condition syntax (missing prefix): {}", condition);
            return true; // Invalid syntax = show by default
        }

        String prefix = expr.substring(0, colonIndex);
        String name = expr.substring(colonIndex + 1);

        if (name.isEmpty()) {
            SimChatMod.LOGGER.warn("Invalid condition syntax (empty name): {}", condition);
            return true;
        }

        // Find resolver
        ConditionResolver resolver = resolvers.get(prefix);
        if (resolver == null) {
            SimChatMod.LOGGER.warn("Unknown condition prefix: {}", prefix);
            return true; // Unknown prefix = show by default
        }

        // Evaluate
        try {
            boolean result = resolver.evaluate(name, ctx);
            return negate != result; // XOR: negate flips the result
        } catch (Exception e) {
            SimChatMod.LOGGER.error("Error evaluating condition '{}': {}", condition, e.getMessage());
            return true; // Error = show by default
        }
    }

    /**
     * Checks if a condition string has valid syntax.
     */
    public static boolean isValidSyntax(String condition) {
        if (condition == null || condition.isEmpty()) {
            return true;
        }
        String expr = condition.trim();
        if (expr.startsWith("!")) {
            expr = expr.substring(1).trim();
        }
        int colonIndex = expr.indexOf(':');
        return colonIndex > 0 && colonIndex < expr.length() - 1;
    }

    // Static initializer to register built-in resolvers
    static {
        // KubeJS callback resolver - truthy evaluation
        registerResolver("kjs", (name, ctx) -> CallbackRegistry.evaluateBoolean(name, ctx));

        // Team data resolver - truthy evaluation
        registerResolver("data", (name, ctx) -> {
            if (ctx.team() == null) return false;
            return ctx.team().hasData(name);
        });

        // Scoreboard resolver (uses player's score)
        registerResolver("score", (name, ctx) -> {
            if (ctx.player() == null) return false;
            var scoreboard = ctx.player().getScoreboard();
            var objective = scoreboard.getObjective(name);
            if (objective == null) return false;
            String playerName = ctx.player().getScoreboardName();
            var score = scoreboard.getOrCreatePlayerScore(playerName, objective);
            return score.getScore() > 0;
        });

        // Permission level resolver
        registerResolver("permission", (name, ctx) -> {
            if (ctx.player() == null) return false;
            try {
                int required = Integer.parseInt(name);
                return ctx.player().hasPermissions(required);
            } catch (NumberFormatException e) {
                return false;
            }
        });
    }
}
