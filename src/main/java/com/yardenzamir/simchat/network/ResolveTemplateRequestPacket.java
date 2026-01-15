package com.yardenzamir.simchat.network;

import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import com.yardenzamir.simchat.condition.CallbackContext;
import com.yardenzamir.simchat.condition.TemplateEngine;
import com.yardenzamir.simchat.team.SimChatTeamManager;
import com.yardenzamir.simchat.team.TeamData;

public class ResolveTemplateRequestPacket {

    private final UUID messageId;
    private final String fieldKey;
    private final String entityId;
    private final String template;

    public ResolveTemplateRequestPacket(UUID messageId, String fieldKey, String entityId, String template) {
        this.messageId = messageId;
        this.fieldKey = fieldKey;
        this.entityId = entityId;
        this.template = template;
    }

    public static void encode(ResolveTemplateRequestPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.messageId);
        buf.writeUtf(packet.fieldKey);
        buf.writeUtf(packet.entityId);
        buf.writeUtf(packet.template);
    }

    public static ResolveTemplateRequestPacket decode(FriendlyByteBuf buf) {
        UUID messageId = buf.readUUID();
        String fieldKey = buf.readUtf();
        String entityId = buf.readUtf();
        String template = buf.readUtf();
        return new ResolveTemplateRequestPacket(messageId, fieldKey, entityId, template);
    }

    public static void handle(ResolveTemplateRequestPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }

            SimChatTeamManager manager = SimChatTeamManager.get(player.server);
            TeamData team = manager.getPlayerTeam(player);
            CallbackContext callbackCtx = new CallbackContext(player, team, packet.entityId);

            String resolved;
            try {
                resolved = TemplateEngine.resolveWithPrefixes(packet.template, callbackCtx);
            } catch (Exception e) {
                resolved = packet.template;
            }
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new ResolveTemplateResponsePacket(packet.messageId, packet.fieldKey, resolved));
        });
        ctx.get().setPacketHandled(true);
    }
}
