package com.yardenzamir.simchat.network;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import com.yardenzamir.simchat.config.ServerConfig;
import com.yardenzamir.simchat.data.ChatMessage;
import com.yardenzamir.simchat.team.SimChatTeamManager;
import com.yardenzamir.simchat.team.TeamData;

/**
 * Client requests older messages when scrolling up.
 */
public class RequestOlderMessagesPacket {

    private final String entityId;
    private final int beforeIndex; // Request messages before this index
    private final int count; // How many messages to request

    public RequestOlderMessagesPacket(String entityId, int beforeIndex, int count) {
        this.entityId = entityId;
        this.beforeIndex = beforeIndex;
        this.count = count;
    }

    public static void encode(RequestOlderMessagesPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.entityId);
        buf.writeInt(packet.beforeIndex);
        buf.writeInt(packet.count);
    }

    public static RequestOlderMessagesPacket decode(FriendlyByteBuf buf) {
        return new RequestOlderMessagesPacket(buf.readUtf(), buf.readInt(), buf.readInt());
    }

    public static void handle(RequestOlderMessagesPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            SimChatTeamManager manager = SimChatTeamManager.get(player.server);
            TeamData team = manager.getPlayerTeam(player);
            if (team == null) return;

            int totalCount = manager.getMessageCount(team, packet.entityId);
            if (totalCount <= 0) return;

            int cappedCount = Math.min(packet.count, ServerConfig.MAX_LAZY_LOAD_BATCH_SIZE.get());
            int endIndex = Math.min(packet.beforeIndex, totalCount);
            int startIndex = Math.max(0, endIndex - cappedCount);

            if (startIndex >= endIndex) return;

            List<ChatMessage> batch = manager.loadMessages(team, packet.entityId, startIndex, endIndex - startIndex);
            NetworkHandler.sendMessages(player, packet.entityId, batch, totalCount, startIndex);
        });
        ctx.get().setPacketHandled(true);
    }
}
