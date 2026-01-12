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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Schedules delayed message delivery with typing indicator.
 * On player logout, pending messages are immediately delivered to ensure no loss.
 */
@Mod.EventBusSubscriber(modid = SimChatMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DelayedMessageScheduler {

    // CopyOnWriteArrayList to avoid ConcurrentModificationException when commands schedule
    // new messages while tick handler is iterating
    private static final List<PendingMessage> pendingMessages = new CopyOnWriteArrayList<>();

    public static void schedule(ServerPlayer player, ChatMessage message, int delayTicks) {
        NetworkHandler.sendTyping(player, message.entityId(), true);
        pendingMessages.add(new PendingMessage(player.getUUID(), message, delayTicks));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        // Decrement timers and collect ready messages
        for (PendingMessage pending : pendingMessages) {
            pending.ticksRemaining--;
        }

        // Remove and deliver ready messages (CopyOnWriteArrayList safe removal)
        pendingMessages.removeIf(pending -> {
            if (pending.ticksRemaining <= 0) {
                ServerPlayer player = server.getPlayerList().getPlayer(pending.playerUuid);
                if (player != null) {
                    deliverMessage(player, pending.message);
                }
                return true;
            }
            return false;
        });
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        UUID uuid = player.getUUID();
        pendingMessages.removeIf(pending -> {
            if (pending.playerUuid.equals(uuid)) {
                // Deliver immediately to capability (will be saved)
                ChatCapability.get(player).ifPresent(data -> {
                    data.addMessage(pending.message);
                });
                return true;
            }
            return false;
        });
    }

    private static void deliverMessage(ServerPlayer player, ChatMessage message) {
        NetworkHandler.sendTyping(player, message.entityId(), false);

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
