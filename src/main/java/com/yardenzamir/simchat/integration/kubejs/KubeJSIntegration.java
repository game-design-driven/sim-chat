package com.yardenzamir.simchat.integration.kubejs;

import com.google.common.base.Suppliers;
import com.yardenzamir.simchat.SimChatMod;
import com.yardenzamir.simchat.condition.CallbackRegistry;
import net.minecraftforge.fml.ModList;

import java.util.function.Supplier;

/**
 * Integration wrapper for KubeJS.
 * All KubeJS-dependent calls should go through this class to avoid class loading issues
 * when KubeJS is not installed.
 */
public final class KubeJSIntegration {
    private static final Supplier<Boolean> IS_LOADED = Suppliers.memoize(
        () -> ModList.get().isLoaded("kubejs")
    );

    private KubeJSIntegration() {}

    /**
     * Checks if KubeJS is loaded.
     */
    public static boolean isLoaded() {
        return IS_LOADED.get();
    }

    /**
     * Fires the registerCallbacks event, clearing existing callbacks first.
     * Should be called during server start and reload.
     */
    public static void fireRegisterCallbacksEvent() {
        if (isLoaded()) {
            Events.fireRegisterCallbacks();
        }
    }

    /**
     * Inner class to isolate KubeJS class references.
     * This prevents class loading errors when KubeJS is not present.
     */
    private static class Events {
        static void fireRegisterCallbacks() {
            // Clear existing callbacks before re-registering
            CallbackRegistry.clear();
            SimChatMod.LOGGER.info("SimChat: Firing registerCallbacks event...");

            try {
                // Post the event for KubeJS scripts to register their callbacks
                SimChatJSEvents.REGISTER_CALLBACKS.post(new RegisterCallbacksKubeEvent());
            } catch (Exception e) {
                SimChatMod.LOGGER.error("SimChat: Error firing KubeJS event: {}", e.getMessage(), e);
            }

            int count = CallbackRegistry.size();
            if (count == 0) {
                SimChatMod.LOGGER.warn("SimChat: No KubeJS callbacks registered! Check that simchat.js exists in kubejs/server_scripts/ and has no syntax errors.");
            } else {
                SimChatMod.LOGGER.info("SimChat: Registered {} KubeJS callbacks", count);
            }
        }
    }
}
