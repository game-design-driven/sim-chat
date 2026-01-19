package com.yardenzamir.simchat.command;

import com.yardenzamir.simchat.SimChatMod;
import com.yardenzamir.simchat.data.ChatMessage;
import com.yardenzamir.simchat.network.NetworkHandler;
import com.yardenzamir.simchat.team.SimChatTeamManager;
import com.yardenzamir.simchat.team.TeamData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Schedules delayed message delivery with typing indicator.
 * Messages are delivered to teams rather than individual players.
 */
@Mod.EventBusSubscriber(modid = SimChatMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DelayedMessageScheduler {

    private static final List<PendingMessage> pendingMessages = new CopyOnWriteArrayList<>();

    /**
     * Schedules a message to be delivered to a player's team after a delay.
     */
    public static void schedule(ServerPlayer player, ChatMessage message, int delayTicks) {
        SimChatTeamManager manager = SimChatTeamManager.get(player.server);
        TeamData team = manager.getPlayerTeam(player);
        if (team == null) {
            SimChatMod.LOGGER.warn("Cannot schedule message - player {} has no team", player.getName().getString());
            return;
        }

        // Send typing indicator to all team members
        NetworkHandler.sendTypingToTeam(team, message.entityId(), true, player.server);
        pendingMessages.add(new PendingMessage(team.getId(), message, delayTicks));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        // Decrement timers
        for (PendingMessage pending : pendingMessages) {
            pending.ticksRemaining--;
        }

        SimChatTeamManager manager = SimChatTeamManager.get(server);

        // Deliver ready messages
        pendingMessages.removeIf(pending -> {
            if (pending.ticksRemaining <= 0) {
                deliverMessage(manager, pending.teamId, pending.message, server);
                return true;
            }
            return false;
        });
    }

    private static void deliverMessage(SimChatTeamManager manager, String teamId, ChatMessage message, MinecraftServer server) {
        TeamData team = manager.getTeam(teamId);
        if (team == null) {
            SimChatMod.LOGGER.warn("Cannot deliver message - team {} not found", teamId);
            return;
        }

        // Stop typing indicator
        NetworkHandler.sendTypingToTeam(team, message.entityId(), false, server);

        // Add message to team data
        int messageIndex = manager.appendMessage(team, message);
        if (messageIndex < 0) {
            return;
        }
        manager.saveTeam(team);

        int totalCount = manager.getMessageCount(team, message.entityId());
        NetworkHandler.sendMessageToTeam(team, message, messageIndex, totalCount, server, true);
    }

    private static class PendingMessage {
        final String teamId;
        final ChatMessage message;
        int ticksRemaining;

        PendingMessage(String teamId, ChatMessage message, int ticksRemaining) {
            this.teamId = teamId;
            this.message = message;
            this.ticksRemaining = ticksRemaining;
        }
    }
}
