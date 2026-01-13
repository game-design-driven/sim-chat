package com.yardenzamir.simchat.integration.kubejs;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;

/**
 * KubeJS plugin for SimChat integration.
 * Registers events and bindings for script access.
 */
public class SimChatKubeJSPlugin extends KubeJSPlugin {
    @Override
    public void registerEvents() {
        SimChatJSEvents.register();
    }

    @Override
    public void registerBindings(BindingsEvent event) {
        // Expose SimChat helper for direct callback registration
        event.add("SimChat", SimChatBindings.class);
    }
}
