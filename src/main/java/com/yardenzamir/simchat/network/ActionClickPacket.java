package com.yardenzamir.simchat.network;

import com.yardenzamir.simchat.SimChatMod;
import com.yardenzamir.simchat.data.ChatAction;
import com.yardenzamir.simchat.data.ChatMessage;
import com.yardenzamir.simchat.team.SimChatTeamManager;
import com.yardenzamir.simchat.team.TeamData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
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

            SimChatTeamManager manager = SimChatTeamManager.get(player.server);
            TeamData team = manager.getPlayerTeam(player);
            if (team == null) {
                SimChatMod.LOGGER.warn("Player {} has no team", player.getName().getString());
                return;
            }

            List<ChatMessage> messages = team.getMessages(packet.entityId);
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
                // Action may have been consumed by teammate - fail silently
                return;
            }

            // Validate player has all required input items
            if (!action.itemsInput().isEmpty()) {
                Inventory inventory = player.getInventory();
                for (ChatAction.ActionItem inputItem : action.itemsInput()) {
                    ItemStack required = inputItem.toItemStack();
                    if (required == null) continue;

                    int found = 0;
                    for (int i = 0; i < inventory.getContainerSize(); i++) {
                        ItemStack slot = inventory.getItem(i);
                        if (ItemStack.isSameItemSameTags(slot, required)) {
                            found += slot.getCount();
                        }
                    }
                    if (found < required.getCount()) {
                        SimChatMod.LOGGER.warn("Player {} missing required item {} x{} for action '{}'",
                                player.getName().getString(), inputItem.item(), inputItem.count(), packet.actionLabel);
                        return;
                    }
                }

                // Consume input items from inventory
                for (ChatAction.ActionItem inputItem : action.itemsInput()) {
                    ItemStack required = inputItem.toItemStack();
                    if (required == null) continue;

                    int toRemove = required.getCount();
                    for (int i = 0; i < inventory.getContainerSize() && toRemove > 0; i++) {
                        ItemStack slot = inventory.getItem(i);
                        if (ItemStack.isSameItemSameTags(slot, required)) {
                            int remove = Math.min(toRemove, slot.getCount());
                            slot.shrink(remove);
                            toRemove -= remove;
                        }
                    }
                }
            }

            // Give output items to player
            for (ChatAction.ActionItem outputItem : action.itemsOutput()) {
                ItemStack toGive = outputItem.toItemStack();
                if (toGive != null) {
                    if (!player.getInventory().add(toGive)) {
                        player.drop(toGive, false);
                    }
                }
            }

            // Consume actions (remove buttons after clicking) - affects whole team
            team.consumeActions(packet.entityId, packet.messageIndex);

            // Add player reply if specified
            long worldDay = player.level().getDayTime() / 24000L;
            if (action.replyText() != null && !action.replyText().isEmpty()) {
                ChatMessage reply = ChatMessage.fromPlayer(
                        packet.entityId,
                        player.getUUID(),
                        player.getName().getString(),
                        team.getTitle(),
                        action.replyText(),
                        worldDay
                );
                team.addMessage(reply);
            }

            // Create transaction system message after reply
            if (!action.itemsInput().isEmpty() || !action.itemsOutput().isEmpty()) {
                ChatMessage transactionMsg = ChatMessage.transactionMessage(
                        packet.entityId, worldDay,
                        action.itemsInput(), action.itemsOutput()
                );
                team.addMessage(transactionMsg);
            }

            // Save team data
            manager.saveTeam(team);

            // Sync to ALL team members
            NetworkHandler.syncTeamToAllMembers(team, player.server);

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
        ctx.get().setPacketHandled(true);
    }
}
