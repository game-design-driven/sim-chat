package com.yardenzamir.simchat.condition;

import com.yardenzamir.simchat.SimChatMod;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for SimChat callbacks registered via KubeJS.
 * Thread-safe for concurrent access during condition evaluation.
 */
public final class CallbackRegistry {
    private static final Map<String, SimChatCallback> callbacks = new ConcurrentHashMap<>();

    private CallbackRegistry() {}

    /**
     * Registers a callback with the given name.
     * Overwrites any existing callback with the same name.
     */
    public static void register(String name, SimChatCallback callback) {
        callbacks.put(name, callback);
        SimChatMod.LOGGER.debug("Registered SimChat callback: {}", name);
    }

    /**
     * Removes a callback by name.
     */
    public static void unregister(String name) {
        callbacks.remove(name);
    }

    /**
     * Clears all registered callbacks. Called on reload.
     */
    public static void clear() {
        callbacks.clear();
        SimChatMod.LOGGER.debug("Cleared all SimChat callbacks");
    }

    /**
     * Checks if a callback is registered.
     */
    public static boolean has(String name) {
        return callbacks.containsKey(name);
    }

    /**
     * Evaluates a callback and returns the raw result.
     *
     * @return The callback result, or null if not found or error occurs
     */
    @Nullable
    public static Object evaluate(String name, CallbackContext ctx) {
        SimChatCallback callback = callbacks.get(name);
        if (callback == null) {
            SimChatMod.LOGGER.warn("KubeJS callback '{}' not found. Registered callbacks: {}", name, callbacks.keySet());
            return null;
        }
        try {
            Object result = callback.call(ctx);
            SimChatMod.LOGGER.debug("KubeJS callback '{}' returned: {}", name, result);
            return result;
        } catch (Exception e) {
            SimChatMod.LOGGER.error("Error evaluating KubeJS callback '{}': {}", name, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Evaluates a callback as a boolean using truthy/falsy semantics.
     * <ul>
     *   <li>null, false, 0, 0.0, "" -> false</li>
     *   <li>everything else -> true</li>
     * </ul>
     */
    public static boolean evaluateBoolean(String name, CallbackContext ctx) {
        return isTruthy(evaluate(name, ctx));
    }

    /**
     * Evaluates a callback and returns the result as a string.
     *
     * @return String representation, or empty string if null
     */
    public static String evaluateString(String name, CallbackContext ctx) {
        Object result = evaluate(name, ctx);
        return result != null ? result.toString() : "";
    }

    /**
     * Evaluates a callback and returns the result as a number.
     *
     * @return Numeric value, or 0.0 if not a number
     */
    public static double evaluateNumber(String name, CallbackContext ctx) {
        Object result = evaluate(name, ctx);
        if (result instanceof Number n) {
            return n.doubleValue();
        }
        return 0.0;
    }

    /**
     * Determines if a value is "truthy" using JavaScript-like semantics.
     */
    public static boolean isTruthy(@Nullable Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.doubleValue() != 0;
        }
        if (value instanceof String s) {
            return !s.isEmpty();
        }
        return true;
    }

    /**
     * Returns the number of registered callbacks.
     */
    public static int size() {
        return callbacks.size();
    }

    /**
     * Returns all registered callback names.
     */
    public static java.util.Set<String> getCallbackNames() {
        return callbacks.keySet();
    }
}
