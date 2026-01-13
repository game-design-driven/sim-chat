package com.yardenzamir.simchat.network;

import com.yardenzamir.simchat.SimChatMod;
import com.yardenzamir.simchat.command.DelayedMessageScheduler;
import com.yardenzamir.simchat.condition.CallbackContext;
import com.yardenzamir.simchat.condition.TemplateEngine;
import com.yardenzamir.simchat.config.ServerConfig;
import com.yardenzamir.simchat.data.ChatAction;
import com.yardenzamir.simchat.data.ChatMessage;
import com.yardenzamir.simchat.data.DialogueData;
import com.yardenzamir.simchat.data.DialogueManager;
import com.yardenzamir.simchat.team.SimChatTeamManager;
import com.yardenzamir.simchat.team.TeamData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Sent from client to server when player clicks an action button.
 */
public class ActionClickPacket {

    private final String entityId;
    private final int messageIndex;
    private final String actionLabel;
    private final @Nullable String inputValue;

    public ActionClickPacket(String entityId, int messageIndex, String actionLabel) {
        this(entityId, messageIndex, actionLabel, null);
    }

    public ActionClickPacket(String entityId, int messageIndex, String actionLabel, @Nullable String inputValue) {
        this.entityId = entityId;
        this.messageIndex = messageIndex;
        this.actionLabel = actionLabel;
        this.inputValue = inputValue;
    }

    public static void encode(ActionClickPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.entityId);
        buf.writeVarInt(packet.messageIndex);
        buf.writeUtf(packet.actionLabel);
        buf.writeBoolean(packet.inputValue != null);
        if (packet.inputValue != null) {
            buf.writeUtf(packet.inputValue);
        }
    }

    public static ActionClickPacket decode(FriendlyByteBuf buf) {
        String entityId = buf.readUtf();
        int messageIndex = buf.readVarInt();
        String actionLabel = buf.readUtf();
        String inputValue = buf.readBoolean() ? buf.readUtf() : null;
        return new ActionClickPacket(entityId, messageIndex, actionLabel, inputValue);
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

            // Validate and process player input if this is an input action
            CallbackContext callbackCtx = new CallbackContext(player, team, packet.entityId);
            if (action.playerInput() != null) {
                ChatAction.PlayerInputConfig inputConfig = action.playerInput();

                // Reject if no input provided or empty
                if (packet.inputValue == null || packet.inputValue.isEmpty()) {
                    SimChatMod.LOGGER.warn("Player {} submitted empty input for action '{}'",
                            player.getName().getString(), packet.actionLabel);
                    return;
                }

                // Reject if exceeds max length
                if (packet.inputValue.length() > inputConfig.maxLength()) {
                    SimChatMod.LOGGER.warn("Player {} input exceeds max length ({} > {}) for action '{}'",
                            player.getName().getString(), packet.inputValue.length(), inputConfig.maxLength(), packet.actionLabel);
                    return;
                }

                // Reject if pattern doesn't match
                if (inputConfig.pattern() != null && !packet.inputValue.matches(inputConfig.pattern())) {
                    SimChatMod.LOGGER.warn("Player {} input doesn't match pattern for action '{}'",
                            player.getName().getString(), packet.actionLabel);
                    return;
                }

                // Save to team data if configured
                if (inputConfig.saveAsData() && team != null) {
                    team.setData(inputConfig.id(), packet.inputValue);
                }

                // Add input value to context for template processing
                callbackCtx = callbackCtx.withInputValue(inputConfig.id(), packet.inputValue);
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

            // Add player reply if specified (with template processing for input values)
            long worldDay = player.level().getDayTime() / 24000L;
            if (action.replyText() != null && !action.replyText().isEmpty()) {
                String processedReply = TemplateEngine.process(action.replyText(), callbackCtx);
                ChatMessage reply = ChatMessage.fromPlayer(
                        packet.entityId,
                        player.getUUID(),
                        player.getName().getString(),
                        team.getTitle(),
                        processedReply,
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

            // Trigger next dialogue state if specified
            if (action.nextState() != null && !action.nextState().isEmpty()) {
                ResourceLocation nextStateId = ResourceLocation.tryParse(action.nextState());
                if (nextStateId != null) {
                    DialogueData nextDialogue = DialogueManager.get(nextStateId);
                    if (nextDialogue != null) {
                        CallbackContext nextCtx = new CallbackContext(player, team, nextDialogue.entityId());
                        ChatMessage nextMessage = nextDialogue.toMessage(nextDialogue.entityId(), worldDay, nextCtx);
                        int delayTicks = calculateDelayTicks(nextDialogue.text());
                        DelayedMessageScheduler.schedule(player, nextMessage, delayTicks);
                    } else {
                        SimChatMod.LOGGER.warn("nextState dialogue not found: {}", action.nextState());
                    }
                } else {
                    SimChatMod.LOGGER.warn("Invalid nextState resource location: {}", action.nextState());
                }
            }

            // Execute commands (with template processing for input values)
            for (String command : action.commands()) {
                if (!command.isEmpty()) {
                    String cmd = TemplateEngine.process(command, callbackCtx);
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

    /**
     * Calculates typing delay in ticks based on text length and config.
     */
    private static int calculateDelayTicks(String text) {
        float charsPerSecond = ServerConfig.TYPING_CHARS_PER_SECOND.get().floatValue();
        float minDelay = ServerConfig.TYPING_DELAY_MIN.get().floatValue();
        float maxDelay = ServerConfig.TYPING_DELAY_MAX.get().floatValue();

        float seconds = text.length() / charsPerSecond;
        float delay = Math.max(minDelay, Math.min(maxDelay, seconds));
        return (int) (delay * 20);
    }
}
