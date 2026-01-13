package com.yardenzamir.simchat.integration.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

/**
 * KubeJS event definitions for SimChat.
 */
public interface SimChatJSEvents {
    EventGroup GROUP = EventGroup.of("SimChatEvents");

    /**
     * Event for registering callbacks that can be used in conditions and templates.
     * Fires during server startup and reload.
     *
     * <pre>{@code
     * SimChatEvents.registerCallbacks(event => {
     *     event.register('hasHighRep', ctx => {
     *         return ctx.player.persistentData.getInt('nexus_rep') >= 10;
     *     });
     * });
     * }</pre>
     */
    EventHandler REGISTER_CALLBACKS = GROUP.server("registerCallbacks", () -> RegisterCallbacksKubeEvent.class);

    static void register() {
        GROUP.register();
    }
}
