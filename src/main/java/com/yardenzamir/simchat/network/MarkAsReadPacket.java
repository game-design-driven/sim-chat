package com.yardenzamir.simchat.network;

import com.yardenzamir.simchat.capability.ChatCapability;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent from client to server when player views a conversation.
 */
public class MarkAsReadPacket {

    private final String entityId;

    public MarkAsReadPacket(String entityId) {
        this.entityId = entityId;
    }

    public static void encode(MarkAsReadPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.entityId);
    }

    public static MarkAsReadPacket decode(FriendlyByteBuf buf) {
        return new MarkAsReadPacket(buf.readUtf());
    }

    public static void handle(MarkAsReadPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChatCapability.get(player).ifPresent(data -> {
                data.markAsRead(packet.entityId);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
