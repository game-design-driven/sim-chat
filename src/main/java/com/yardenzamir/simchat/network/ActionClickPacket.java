package com.yardenzamir.simchat.network;

import com.yardenzamir.simchat.SimChatMod;
import com.yardenzamir.simchat.capability.ChatCapability;
import com.yardenzamir.simchat.data.ChatAction;
import com.yardenzamir.simchat.data.ChatMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Sent from client to server when player clicks an action button.
 */
public class ActionClickPacket {

    private final String entityId;
    private final int messageIndex;
    private final String actionLabel;

    public ActionClickPacket(String entityId, int messageIndex, String actionLabel) {
        this.entityId = entityId;
        this.messageIndex = messageIndex;
        this.actionLabel = actionLabel;
    }

    public static void encode(ActionClickPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.entityId);
        buf.writeVarInt(packet.messageIndex);
        buf.writeUtf(packet.actionLabel);
    }

    public static ActionClickPacket decode(FriendlyByteBuf buf) {
        return new ActionClickPacket(
                buf.readUtf(),
                buf.readVarInt(),
                buf.readUtf()
        );
    }

    public static void handle(ActionClickPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ChatCapability.get(player).ifPresent(data -> {
                List<ChatMessage> messages = data.getMessages(packet.entityId);
                if (packet.messageIndex < 0 || packet.messageIndex >= messages.size()) {
                    SimChatMod.LOGGER.warn("Invalid message index {} for entity {}",
                            packet.messageIndex, packet.entityId);
                    return;
                }

                ChatMessage message = messages.get(packet.messageIndex);
                ChatAction action = null;
                for (ChatAction a : message.actions()) {
                    if (a.label().equals(packet.actionLabel)) {
                        action = a;
                        break;
                    }
                }

                if (action == null) {
                    SimChatMod.LOGGER.warn("Action '{}' not found in message", packet.actionLabel);
                    return;
                }

                // Consume actions (remove buttons after clicking)
                data.consumeActions(packet.entityId, packet.messageIndex);

                // Add player reply if specified
                if (action.replyText() != null && !action.replyText().isEmpty()) {
                    long worldDay = player.level().getDayTime() / 24000L;
                    ChatMessage reply = ChatMessage.fromPlayer(
                            packet.entityId,
                            player.getName().getString(),
                            action.replyText(),
                            worldDay
                    );
                    data.addMessage(reply);
                }

                // Sync full data to client (includes consumed actions + reply)
                NetworkHandler.syncToPlayer(player);

                // Execute commands
                for (String command : action.commands()) {
                    if (!command.isEmpty()) {
                        String cmd = command;
                        if (cmd.startsWith("/")) {
                            cmd = cmd.substring(1);
                        }
                        player.getServer().getCommands().performPrefixedCommand(
                                player.createCommandSourceStack(),
                                cmd
                        );
                    }
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
