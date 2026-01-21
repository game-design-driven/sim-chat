package com.yardenzamir.simchat.network;

import com.yardenzamir.simchat.client.screen.ChatScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Tells client to open the chat screen, optionally selecting an entity and message.
 */
public class OpenChatScreenPacket {

    private final String entityId;
    private final boolean hasMessage;
    private final java.util.UUID messageId;
    private final int messageIndex;

    public OpenChatScreenPacket(String entityId, @org.jetbrains.annotations.Nullable java.util.UUID messageId, int messageIndex) {
        this.entityId = entityId;
        this.hasMessage = messageId != null;
        this.messageId = messageId != null ? messageId : new java.util.UUID(0L, 0L);
        this.messageIndex = messageIndex;
    }

    public static void encode(OpenChatScreenPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.entityId);
        buf.writeBoolean(packet.hasMessage);
        if (packet.hasMessage) {
            buf.writeUUID(packet.messageId);
            buf.writeInt(packet.messageIndex);
        }
    }

    public static OpenChatScreenPacket decode(FriendlyByteBuf buf) {
        String entityId = buf.readUtf();
        boolean hasMessage = buf.readBoolean();
        if (hasMessage) {
            return new OpenChatScreenPacket(entityId, buf.readUUID(), buf.readInt());
        }
        return new OpenChatScreenPacket(entityId, null, -1);
    }

    public static void handle(OpenChatScreenPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(packet))
        );
        ctx.get().setPacketHandled(true);
    }

    private static void handleClient(OpenChatScreenPacket packet) {
        if (packet.hasMessage) {
            Minecraft.getInstance().setScreen(new ChatScreen(packet.entityId, packet.messageId, packet.messageIndex));
        } else {
            Minecraft.getInstance().setScreen(new ChatScreen(packet.entityId, null, -1));
        }
    }
}
