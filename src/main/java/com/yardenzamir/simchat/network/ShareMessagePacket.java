package com.yardenzamir.simchat.network;

import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import com.yardenzamir.simchat.condition.CallbackContext;
import com.yardenzamir.simchat.condition.TemplateEngine;
import com.yardenzamir.simchat.team.SimChatTeamManager;
import com.yardenzamir.simchat.team.TeamData;

/**
 * Shares a message to team chat with a clickable link.
 */
public class ShareMessagePacket {

    private final UUID messageId;

    public ShareMessagePacket(UUID messageId) {
        this.messageId = messageId;
    }

    public static void encode(ShareMessagePacket packet, net.minecraft.network.FriendlyByteBuf buf) {
        buf.writeUUID(packet.messageId);
    }

    public static ShareMessagePacket decode(net.minecraft.network.FriendlyByteBuf buf) {
        return new ShareMessagePacket(buf.readUUID());
    }

    public static void handle(ShareMessagePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            SimChatTeamManager manager = SimChatTeamManager.get(player.server);
            TeamData team = manager.getPlayerTeam(player);
            if (team == null) return;

            var stored = manager.getMessageById(team, packet.messageId);
            if (stored == null) return;

            CallbackContext callbackCtx = CallbackContext.of(player, team, stored.entityId());
            String contentTemplate = stored.message().contentTemplate() != null
                    ? stored.message().contentTemplate()
                    : stored.message().content();
            String resolvedContent = TemplateEngine.resolveWithPrefixes(contentTemplate, callbackCtx);

            String senderTemplate = stored.message().senderNameTemplate() != null
                    ? stored.message().senderNameTemplate()
                    : stored.message().senderName();
            String resolvedSender = TemplateEngine.resolveWithPrefixes(senderTemplate, callbackCtx);

            net.minecraft.network.chat.MutableComponent header = Component.literal("[SimChat] ")
                    .withStyle(Style.EMPTY.withColor(0x88AAFF));
            net.minecraft.network.chat.MutableComponent sender = Component.literal(resolvedSender + ": ")
                    .withStyle(Style.EMPTY.withColor(0xFFFFFF));
            net.minecraft.network.chat.MutableComponent body = Component.literal(resolvedContent)
                    .withStyle(Style.EMPTY.withColor(0xCCCCCC));

            net.minecraft.network.chat.MutableComponent linked = header.append(sender).append(body)
                    .withStyle(Style.EMPTY
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                    "/simchat openmessage " + packet.messageId))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal(resolvedContent))));

            for (ServerPlayer member : manager.getOnlineTeamMembers(team)) {
                member.sendSystemMessage(linked);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
