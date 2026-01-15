package com.yardenzamir.simchat.network;

import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import com.yardenzamir.simchat.client.RuntimeTemplateResolver;

public class ResolveTemplateResponsePacket {

    private final UUID messageId;
    private final String fieldKey;
    private final String resolvedText;

    public ResolveTemplateResponsePacket(UUID messageId, String fieldKey, String resolvedText) {
        this.messageId = messageId;
        this.fieldKey = fieldKey;
        this.resolvedText = resolvedText;
    }

    public static void encode(ResolveTemplateResponsePacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.messageId);
        buf.writeUtf(packet.fieldKey);
        buf.writeUtf(packet.resolvedText);
    }

    public static ResolveTemplateResponsePacket decode(FriendlyByteBuf buf) {
        UUID messageId = buf.readUUID();
        String fieldKey = buf.readUtf();
        String resolvedText = buf.readUtf();
        return new ResolveTemplateResponsePacket(messageId, fieldKey, resolvedText);
    }

    public static void handle(ResolveTemplateResponsePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(packet))
        );
        ctx.get().setPacketHandled(true);
    }

    private static void handleClient(ResolveTemplateResponsePacket packet) {
        RuntimeTemplateResolver.updateFromServer(packet.messageId, packet.fieldKey, packet.resolvedText);
    }
}
