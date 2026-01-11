package com.yardenzamir.simchat.network;

import com.yardenzamir.simchat.SimChatMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles syncing chat data when players login or respawn.
 */
@Mod.EventBusSubscriber(modid = SimChatMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlayerSyncHandler {

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            NetworkHandler.syncToPlayer(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            NetworkHandler.syncToPlayer(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            NetworkHandler.syncToPlayer(serverPlayer);
        }
    }
}
