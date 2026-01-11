package com.yardenzamir.simchat;

import com.yardenzamir.simchat.client.ClientSetup;
import com.yardenzamir.simchat.config.ClientConfig;
import com.yardenzamir.simchat.config.ServerConfig;
import com.yardenzamir.simchat.data.DialogueManager;
import com.yardenzamir.simchat.network.NetworkHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(SimChatMod.MOD_ID)
public class SimChatMod {
    public static final String MOD_ID = "simchat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public SimChatMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register configs
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ServerConfig.SPEC);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onConfigLoad);
        modEventBus.addListener(this::onConfigReload);

        MinecraftForge.EVENT_BUS.addListener(this::onAddReloadListeners);

        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ClientSetup::init);

        LOGGER.info("Sim Chat initialized");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(NetworkHandler::init);
    }

    private void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getModId().equals(MOD_ID)) {
            LOGGER.info("Loaded {} config", event.getConfig().getType().name().toLowerCase());
        }
    }

    private void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getModId().equals(MOD_ID)) {
            LOGGER.info("Reloaded {} config", event.getConfig().getType().name().toLowerCase());

            // Clear avatar cache on client config reload to pick up any changes
            if (event.getConfig().getType() == ModConfig.Type.CLIENT) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    com.yardenzamir.simchat.client.AvatarManager.clearCache();
                });
            }
        }
    }

    private void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new DialogueManager());
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
