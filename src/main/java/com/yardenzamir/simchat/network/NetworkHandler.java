package com.yardenzamir.simchat.network;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import com.yardenzamir.simchat.SimChatMod;
import com.yardenzamir.simchat.capability.ChatCapability;
import com.yardenzamir.simchat.config.ServerConfig;
import com.yardenzamir.simchat.data.ChatMessage;
import com.yardenzamir.simchat.team.SimChatTeamManager;
import com.yardenzamir.simchat.team.TeamData;

/**
 * Handles network packet registration and sending.
 */
public class NetworkHandler {

    private static final String PROTOCOL_VERSION = "5";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SimChatMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void init() {
        CHANNEL.registerMessage(packetId++, SyncChatDataPacket.class,
                SyncChatDataPacket::encode,
                SyncChatDataPacket::decode,
                SyncChatDataPacket::handle);

        CHANNEL.registerMessage(packetId++, NewMessagePacket.class,
                NewMessagePacket::encode,
                NewMessagePacket::decode,
                NewMessagePacket::handle);

        CHANNEL.registerMessage(packetId++, ActionClickPacket.class,
                ActionClickPacket::encode,
                ActionClickPacket::decode,
                ActionClickPacket::handle);

        CHANNEL.registerMessage(packetId++, OpenChatScreenPacket.class,
                OpenChatScreenPacket::encode,
                OpenChatScreenPacket::decode,
                OpenChatScreenPacket::handle);

        CHANNEL.registerMessage(packetId++, TypingPacket.class,
                TypingPacket::encode,
                TypingPacket::decode,
                TypingPacket::handle);

        CHANNEL.registerMessage(packetId++, MarkAsReadPacket.class,
                MarkAsReadPacket::encode,
                MarkAsReadPacket::decode,
                MarkAsReadPacket::handle);

        CHANNEL.registerMessage(packetId++, ResolveTemplateRequestPacket.class,
                ResolveTemplateRequestPacket::encode,
                ResolveTemplateRequestPacket::decode,
                ResolveTemplateRequestPacket::handle);

        CHANNEL.registerMessage(packetId++, ResolveTemplateResponsePacket.class,
                ResolveTemplateResponsePacket::encode,
                ResolveTemplateResponsePacket::decode,
                ResolveTemplateResponsePacket::handle);

        CHANNEL.registerMessage(packetId++, SyncTeamMetadataPacket.class,
                SyncTeamMetadataPacket::encode,
                SyncTeamMetadataPacket::decode,
                SyncTeamMetadataPacket::handle);

        CHANNEL.registerMessage(packetId++, SyncMessagesPacket.class,
                SyncMessagesPacket::encode,
                SyncMessagesPacket::decode,
                SyncMessagesPacket::handle);

        CHANNEL.registerMessage(packetId++, RequestOlderMessagesPacket.class,
                RequestOlderMessagesPacket::encode,
                RequestOlderMessagesPacket::decode,
                RequestOlderMessagesPacket::handle);
    }

    /**
     * Syncs all chat data to a player (used on login/respawn).
     */
    public static void syncToPlayer(ServerPlayer player) {
        ChatCapability.get(player).ifPresent(data ->
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncChatDataPacket(data))
        );
    }

    /**
     * Sends a new message to a player.
     * Shows toast notification instead of auto-opening screen.
     */
    public static void sendNewMessage(ServerPlayer player, com.yardenzamir.simchat.data.ChatMessage message, boolean showToast) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new NewMessagePacket(message, showToast));
    }

    /**
     * Tells client to open chat screen for an entity.
     */
    public static void openChatScreen(ServerPlayer player, String entityId) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenChatScreenPacket(entityId));
    }

    /**
     * Sends typing indicator to client.
     */
    public static void sendTyping(ServerPlayer player, String entityId, boolean isTyping) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new TypingPacket(entityId, isTyping));
    }

    // === Team sync methods ===


    /**
     * Syncs team metadata and recent messages (hybrid approach).
     * Sends metadata + last 500 messages per conversation.
     */
    public static void syncTeamWithLazyLoad(ServerPlayer player, TeamData team) {
        SimChatTeamManager manager = SimChatTeamManager.get(player.server);

        Map<String, Integer> messageCountPerEntity = new HashMap<>();
        List<String> entityIds = team.getEntityIds();
        for (String entityId : entityIds) {
            int count = manager.getMessageCount(team, entityId);
            messageCountPerEntity.put(entityId, count);
        }
        List<String> entityOrder = new java.util.ArrayList<>(entityIds);
        Collections.reverse(entityOrder);

        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncTeamMetadataPacket(team.getId(), team.getTitle(), team.getColor(),
                        new java.util.ArrayList<>(team.getMembers()), entityOrder, messageCountPerEntity, team.getAllData()));

        int initialCount = ServerConfig.INITIAL_SYNC_MESSAGE_COUNT.get();
        for (String entityId : entityIds) {
            int totalCount = messageCountPerEntity.getOrDefault(entityId, 0);
            if (totalCount <= 0 || initialCount <= 0) {
                continue;
            }
            int startIndex = Math.max(0, totalCount - initialCount);
            List<com.yardenzamir.simchat.data.ChatMessage> recent =
                    manager.loadMessages(team, entityId, startIndex, totalCount - startIndex);

            sendMessages(player, entityId, recent, totalCount, startIndex);
        }
    }

    /**
     * Sends a batch of messages to a player.
     */
    public static void sendMessages(ServerPlayer player, String entityId,
                                    java.util.List<com.yardenzamir.simchat.data.ChatMessage> messages,
                                    int totalCount, int startIndex) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncMessagesPacket(entityId, messages, totalCount, startIndex));
    }

    /**
     * Client requests older messages.
     */
    public static void requestOlderMessages(String entityId, int beforeIndex, int count) {
        CHANNEL.sendToServer(new RequestOlderMessagesPacket(entityId, beforeIndex, count));
    }

    /**
     * Syncs team data to all online team members.
     */
    public static void syncTeamToAllMembers(TeamData team, MinecraftServer server) {
        SimChatTeamManager manager = SimChatTeamManager.get(server);
        List<ServerPlayer> members = manager.getOnlineTeamMembers(team);
        for (ServerPlayer member : members) {
            syncTeamWithLazyLoad(member, team);
        }
    }

    /**
     * Sends a new or updated message to all online team members.
     */
    public static void sendMessageToTeam(TeamData team, ChatMessage message, MinecraftServer server, boolean showToast) {
        SimChatTeamManager manager = SimChatTeamManager.get(server);
        com.yardenzamir.simchat.storage.SimChatDatabase.StoredMessage stored = manager.getMessageById(team, message.messageId());
        if (stored == null) {
            return;
        }
        int totalCount = manager.getMessageCount(team, stored.entityId());
        sendMessageToTeam(team, message, stored.messageIndex(), totalCount, server, showToast);
    }

    public static void sendMessageToTeam(TeamData team, ChatMessage message, int messageIndex, int totalCount,
                                         MinecraftServer server, boolean showToast) {
        SimChatTeamManager manager = SimChatTeamManager.get(server);
        List<ServerPlayer> members = manager.getOnlineTeamMembers(team);
        for (ServerPlayer member : members) {
            sendMessages(member, message.entityId(), java.util.List.of(message), totalCount, messageIndex);
            if (showToast) {
                sendNewMessage(member, message, true);
            }
        }
    }

    /**
     * Sends typing indicator to all online team members.
     */
    public static void sendTypingToTeam(TeamData team, String entityId, boolean isTyping, MinecraftServer server) {
        SimChatTeamManager manager = SimChatTeamManager.get(server);
        List<ServerPlayer> members = manager.getOnlineTeamMembers(team);
        for (ServerPlayer member : members) {
            sendTyping(member, entityId, isTyping);
        }
    }
}
