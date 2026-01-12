package com.yardenzamir.simchat.network;

import com.yardenzamir.simchat.SimChatMod;
import com.yardenzamir.simchat.capability.ChatCapability;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Handles network packet registration and sending.
 */
public class NetworkHandler {

    private static final String PROTOCOL_VERSION = "2";

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
}
