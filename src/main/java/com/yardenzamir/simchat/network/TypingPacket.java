package com.yardenzamir.simchat.network;

import com.yardenzamir.simchat.capability.ChatCapability;
import net.minecraft.client.Minecraft;
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
    private final String displayName;
    private final String imageId;
    private final boolean isTyping;

    public TypingPacket(String entityId, String displayName, String imageId, boolean isTyping) {
        this.entityId = entityId;
        this.displayName = displayName;
        this.imageId = imageId;
        this.isTyping = isTyping;
    }

    public static void encode(TypingPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.entityId);
        buf.writeUtf(packet.displayName);
        buf.writeUtf(packet.imageId);
        buf.writeBoolean(packet.isTyping);
    }

    public static TypingPacket decode(FriendlyByteBuf buf) {
        return new TypingPacket(
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readBoolean()
        );
    }

    public static void handle(TypingPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(packet))
        );
        ctx.get().setPacketHandled(true);
    }

    private static void handleClient(TypingPacket packet) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            ChatCapability.get(player).ifPresent(data -> {
                data.setTyping(packet.entityId, packet.isTyping);
            });
        }
    }
}
