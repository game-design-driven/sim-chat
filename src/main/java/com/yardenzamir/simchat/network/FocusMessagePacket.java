package com.yardenzamir.simchat.network;

import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import com.yardenzamir.simchat.capability.ChatCapability;
import com.yardenzamir.simchat.team.SimChatTeamManager;
import com.yardenzamir.simchat.team.TeamData;

/**
 * Updates the focused message for a player.
 */
public class FocusMessagePacket {

    private final String entityId;
    private final boolean hasFocus;
    private final UUID messageId;
    private final int messageIndex;

    public FocusMessagePacket(String entityId, UUID messageId, int messageIndex) {
        this.entityId = entityId;
        this.hasFocus = true;
        this.messageId = messageId;
        this.messageIndex = messageIndex;
    }

    public static FocusMessagePacket clear(String entityId) {
        return new FocusMessagePacket(entityId);
    }

    private FocusMessagePacket(String entityId) {
        this.entityId = entityId;
        this.hasFocus = false;
        this.messageId = new UUID(0L, 0L);
        this.messageIndex = -1;
    }

    public static void encode(FocusMessagePacket packet, net.minecraft.network.FriendlyByteBuf buf) {
        buf.writeUtf(packet.entityId);
        buf.writeBoolean(packet.hasFocus);
        if (packet.hasFocus) {
            buf.writeUUID(packet.messageId);
            buf.writeInt(packet.messageIndex);
        }
    }

    public static FocusMessagePacket decode(net.minecraft.network.FriendlyByteBuf buf) {
        String entityId = buf.readUtf();
        boolean hasFocus = buf.readBoolean();
        if (hasFocus) {
            return new FocusMessagePacket(entityId, buf.readUUID(), buf.readInt());
        }
        return new FocusMessagePacket(entityId);
    }

    public static void handle(FocusMessagePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            SimChatTeamManager manager = SimChatTeamManager.get(player.server);
            TeamData team = manager.getPlayerTeam(player);
            if (team == null) return;

            ChatCapability.get(player).ifPresent(data -> {
                if (!packet.hasFocus) {
                    data.clearFocusedMessage(packet.entityId);
                    return;
                }

                var stored = manager.getMessageById(team, packet.messageId);
                if (stored == null || !stored.entityId().equals(packet.entityId)) {
                    return;
                }

                data.setFocusedMessage(packet.entityId, packet.messageId, stored.messageIndex());
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
