package com.yardenzamir.simchat.condition;

import org.jetbrains.annotations.Nullable;

/**
 * Interface for resolving template placeholders with a specific prefix.
 * Implementations handle different types of value sources (kjs callbacks, flags, etc.)
 */
@FunctionalInterface
public interface TemplateResolver {
    /**
     * Resolves a value by name within this resolver's domain.
     *
     * @param name The name after the prefix (e.g., "repLevel" from "{kjs:repLevel}")
     * @param ctx  The callback context
     * @return The resolved value as a string, or null if not found
     */
    @Nullable
    String resolve(String name, CallbackContext ctx);
}
