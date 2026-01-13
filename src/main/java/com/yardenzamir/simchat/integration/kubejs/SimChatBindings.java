package com.yardenzamir.simchat.integration.kubejs;

import com.yardenzamir.simchat.SimChatMod;
import com.yardenzamir.simchat.condition.CallbackContext;
import com.yardenzamir.simchat.condition.CallbackRegistry;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Static bindings exposed to KubeJS scripts via the "SimChat" global object.
 * Allows direct callback registration without needing events.
 *
 * Usage in JS:
 *   SimChat.registerCallback('myCallback', ctx => { return ctx.player.persistentData.getInt('foo') })
 */
public class SimChatBindings {

    // Track reload generation to auto-clear on first registration after reload
    private static final AtomicLong currentGeneration = new AtomicLong(0);
    private static long lastRegistrationGeneration = -1;

    /**
     * Call this at the start of script to signal a new reload cycle.
     * Clears all existing callbacks.
     */
    public static void clearCallbacks() {
        CallbackRegistry.clear();
        lastRegistrationGeneration = currentGeneration.incrementAndGet();
        SimChatMod.LOGGER.debug("SimChat: Cleared callbacks for new registration cycle");
    }

    /**
     * Registers a callback that can be used in conditions and templates.
     * Auto-clears old callbacks on first registration after script reload.
     *
     * @param name The callback name (used as "kjs:name" in conditions/templates)
     * @param callback The JS function that receives CallbackContext and returns any value
     */
    public static void registerCallback(String name, Function<CallbackContext, Object> callback) {
        // Auto-clear on first registration of a new cycle (detected by incrementing generation)
        long gen = currentGeneration.get();
        if (lastRegistrationGeneration != gen) {
            CallbackRegistry.clear();
            lastRegistrationGeneration = gen;
            SimChatMod.LOGGER.info("SimChat: Starting new callback registration cycle");
        }

        CallbackRegistry.register(name, callback::apply);
    }

    /**
     * Signals that script loading is starting (called internally).
     * Increments generation so next registerCallback knows to clear.
     */
    public static void notifyScriptsLoading() {
        currentGeneration.incrementAndGet();
    }

    /**
     * Gets the number of registered callbacks.
     */
    public static int getCallbackCount() {
        return CallbackRegistry.size();
    }

    /**
     * Logs the current callback count (for debugging).
     */
    public static void logStatus() {
        SimChatMod.LOGGER.info("SimChat: {} callbacks registered", CallbackRegistry.size());
    }
}
