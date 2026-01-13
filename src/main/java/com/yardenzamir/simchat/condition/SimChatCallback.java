package com.yardenzamir.simchat.condition;

/**
 * Functional interface for KubeJS callbacks.
 * Returns Object to support boolean conditions, numeric values, and string display.
 */
@FunctionalInterface
public interface SimChatCallback {
    /**
     * Evaluates the callback with the given context.
     *
     * @param ctx Context containing player, team, and dialogue information
     * @return Result value - interpreted based on usage context:
     *         - For conditions: truthy/falsy evaluation
     *         - For templates: toString() for display
     *         - For data: raw object access
     */
    Object call(CallbackContext ctx);
}
