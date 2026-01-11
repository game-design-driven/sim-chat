package com.yardenzamir.simchat.command;

import com.yardenzamir.simchat.SimChatMod;
import com.yardenzamir.simchat.capability.ChatCapability;
import com.yardenzamir.simchat.data.ChatMessage;
import com.yardenzamir.simchat.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Schedules delayed message delivery with typing indicator.
 * On player logout, pending messages are immediately delivered to ensure no loss.
 */
@Mod.EventBusSubscriber(modid = SimChatMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DelayedMessageScheduler {

    private static final List<PendingMessage> pendingMessages = new ArrayList<>();

    public static void schedule(ServerPlayer player, ChatMessage message, int delayTicks) {
        NetworkHandler.sendTyping(player, message.entityId(), message.senderName(),
                message.senderImageId(), true);
        pendingMessages.add(new PendingMessage(player.getUUID(), message, delayTicks));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        Iterator<PendingMessage> iterator = pendingMessages.iterator();
        while (iterator.hasNext()) {
            PendingMessage pending = iterator.next();
            pending.ticksRemaining--;

            if (pending.ticksRemaining <= 0) {
                iterator.remove();
                ServerPlayer player = server.getPlayerList().getPlayer(pending.playerUuid);
                if (player != null) {
                    deliverMessage(player, pending.message);
                }
                // If player offline, message is lost - but this won't happen
                // because onPlayerLogout delivers all pending first
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        UUID uuid = player.getUUID();
        Iterator<PendingMessage> iterator = pendingMessages.iterator();
        while (iterator.hasNext()) {
            PendingMessage pending = iterator.next();
            if (pending.playerUuid.equals(uuid)) {
                iterator.remove();
                // Deliver immediately to capability (will be saved)
                ChatCapability.get(player).ifPresent(data -> {
                    data.addMessage(pending.message);
                });
            }
        }
    }

    private static void deliverMessage(ServerPlayer player, ChatMessage message) {
        NetworkHandler.sendTyping(player, message.entityId(),
                message.senderName(), message.senderImageId(), false);

        ChatCapability.get(player).ifPresent(data -> {
            data.addMessage(message);
            NetworkHandler.sendNewMessage(player, message, true);
        });
    }

    private static class PendingMessage {
        final UUID playerUuid;
        final ChatMessage message;
        int ticksRemaining;

        PendingMessage(UUID playerUuid, ChatMessage message, int ticksRemaining) {
            this.playerUuid = playerUuid;
            this.message = message;
            this.ticksRemaining = ticksRemaining;
        }
    }
}
