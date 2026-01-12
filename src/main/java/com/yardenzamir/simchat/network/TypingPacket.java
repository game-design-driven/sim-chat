package com.yardenzamir.simchat.network;

import com.yardenzamir.simchat.client.ClientTeamCache;
import com.yardenzamir.simchat.team.TeamData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Notifies client that an entity is typing.
 */
public class TypingPacket {

    private final String entityId;
    private final boolean isTyping;

    public TypingPacket(String entityId, boolean isTyping) {
        this.entityId = entityId;
        this.isTyping = isTyping;
    }

    public static void encode(TypingPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.entityId);
        buf.writeBoolean(packet.isTyping);
    }

    public static TypingPacket decode(FriendlyByteBuf buf) {
        return new TypingPacket(buf.readUtf(), buf.readBoolean());
    }

    public static void handle(TypingPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(packet))
        );
        ctx.get().setPacketHandled(true);
    }

    private static void handleClient(TypingPacket packet) {
        TeamData team = ClientTeamCache.getTeam();
        if (team != null) {
            team.setTyping(packet.entityId, packet.isTyping);
        }
    }
}
