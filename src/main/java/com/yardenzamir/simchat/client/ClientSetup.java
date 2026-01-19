package com.yardenzamir.simchat.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import org.lwjgl.glfw.GLFW;

import com.yardenzamir.simchat.client.screen.ChatScreen;

/**
 * Handles client-side initialization and events.
 */
public class ClientSetup {

    private static KeyMapping openChatKey;

    public static void init() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(ClientSetup::onClientSetup);
        modBus.addListener(ClientSetup::onRegisterKeyMappings);

        MinecraftForge.EVENT_BUS.register(ClientSetup.class);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(AvatarManager::init);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        openChatKey = new KeyMapping(
                "key.simchat.open_chat",
                GLFW.GLFW_KEY_SEMICOLON,
                "key.categories.simchat"
        );
        event.register(openChatKey);
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (openChatKey != null && openChatKey.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) {
                mc.setScreen(new ChatScreen(null));
            }
        }
    }

    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientTeamCache.clear();
        RuntimeTemplateResolver.clear();
        // Don't clear PlayerSkinCache - keep skins cached for offline teammates
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        RuntimeTemplateResolver.flushQueuedRequests();
    }

    /**
     * Gets the display name of the open chat keybind for use in toasts.
     */
    public static String getOpenChatKeyName() {
        if (openChatKey != null) {
            return openChatKey.getTranslatedKeyMessage().getString();
        }
        return ";";
    }
}
