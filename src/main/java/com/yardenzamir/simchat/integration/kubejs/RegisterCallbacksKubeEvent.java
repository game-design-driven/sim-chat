package com.yardenzamir.simchat.integration.kubejs;

import com.yardenzamir.simchat.SimChatMod;
import com.yardenzamir.simchat.condition.CallbackContext;
import com.yardenzamir.simchat.condition.CallbackRegistry;
import dev.latvian.mods.kubejs.event.EventJS;

import java.util.function.Function;

/**
 * KubeJS event for registering SimChat callbacks.
 * Callbacks can return any value - booleans for conditions, other types for templates.
 */
public class RegisterCallbacksKubeEvent extends EventJS {

    /**
     * Registers a callback that can be used in conditions and templates.
     *
     * @param name The callback name (used as "kjs:name" in conditions/templates)
     * @param callback The JS function that receives CallbackContext and returns any value
     */
    @SuppressWarnings("unchecked")
    public void register(String name, Function<CallbackContext, Object> callback) {
        SimChatMod.LOGGER.debug("Registering KubeJS callback: {}", name);
        CallbackRegistry.register(name, callback::apply);
    }

    /**
     * Unregisters a callback by name.
     */
    public void unregister(String name) {
        CallbackRegistry.unregister(name);
    }

    /**
     * Checks if a callback with the given name is registered.
     */
    public boolean has(String name) {
        return CallbackRegistry.has(name);
    }
}
