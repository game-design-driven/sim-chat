package com.yardenzamir.simchat.condition;

/**
 * Interface for evaluating conditions with a specific prefix.
 * Implementations handle different types of condition sources (kjs callbacks, flags, scores, etc.)
 */
@FunctionalInterface
public interface ConditionResolver {
    /**
     * Evaluates a condition by name within this resolver's domain.
     *
     * @param name The name after the prefix (e.g., "hasHighRep" from "kjs:hasHighRep")
     * @param ctx  The callback context
     * @return true if condition passes, false otherwise
     */
    boolean evaluate(String name, CallbackContext ctx);
}
