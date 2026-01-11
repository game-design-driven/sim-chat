package com.yardenzamir.simchat.network;

import com.yardenzamir.simchat.client.screen.ChatScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Tells client to open the chat screen, optionally selecting an entity.
 */
public class OpenChatScreenPacket {

    private final String entityId;

    public OpenChatScreenPacket(String entityId) {
        this.entityId = entityId;
    }

    public static void encode(OpenChatScreenPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.entityId);
    }

    public static OpenChatScreenPacket decode(FriendlyByteBuf buf) {
        return new OpenChatScreenPacket(buf.readUtf());
    }

    public static void handle(OpenChatScreenPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(packet))
        );
        ctx.get().setPacketHandled(true);
    }

    private static void handleClient(OpenChatScreenPacket packet) {
        Minecraft.getInstance().setScreen(new ChatScreen(packet.entityId));
    }
}
