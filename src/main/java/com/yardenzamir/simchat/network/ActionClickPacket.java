package com.yardenzamir.simchat.network;

import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import org.jetbrains.annotations.Nullable;

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

/**
 * Sent from client to server when player clicks an action button.
 */
public class ActionClickPacket {

    private final String entityId;
    private final UUID messageId;
    private final int actionIndex;
    private final @Nullable String inputValue;

    public ActionClickPacket(String entityId, UUID messageId, int actionIndex) {
        this(entityId, messageId, actionIndex, null);
    }

    public ActionClickPacket(String entityId, UUID messageId, int actionIndex, @Nullable String inputValue) {
        this.entityId = entityId;
        this.messageId = messageId;
        this.actionIndex = actionIndex;
        this.inputValue = inputValue;
    }

    public static void encode(ActionClickPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.entityId);
        buf.writeUUID(packet.messageId);
        buf.writeVarInt(packet.actionIndex);
        buf.writeBoolean(packet.inputValue != null);
        if (packet.inputValue != null) {
            buf.writeUtf(packet.inputValue);
        }
    }

    public static ActionClickPacket decode(FriendlyByteBuf buf) {
        String entityId = buf.readUtf();
        UUID messageId = buf.readUUID();
        int actionIndex = buf.readVarInt();
        String inputValue = buf.readBoolean() ? buf.readUtf() : null;
        return new ActionClickPacket(entityId, messageId, actionIndex, inputValue);
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

            com.yardenzamir.simchat.storage.SimChatDatabase.StoredMessage stored = manager.getMessageById(team, packet.messageId);
            if (stored == null) {
                SimChatMod.LOGGER.warn("Message {} not found for entity {}", packet.messageId, packet.entityId);
                return;
            }
            if (!stored.entityId().equals(packet.entityId)) {
                SimChatMod.LOGGER.warn("Message {} does not belong to entity {}", packet.messageId, packet.entityId);
                return;
            }

            ChatMessage message = stored.message();
            if (packet.actionIndex < 0 || packet.actionIndex >= message.actions().size()) {
                return;
            }
            ChatAction action = message.actions().get(packet.actionIndex);

            // Validate and process player input if this is an input action
            CallbackContext callbackCtx = new CallbackContext(player, team, packet.entityId);
            if (action.playerInput() != null) {
                ChatAction.PlayerInputConfig inputConfig = action.playerInput();

                // Reject if no input provided or empty
                if (packet.inputValue == null || packet.inputValue.isEmpty()) {
                    SimChatMod.LOGGER.warn("Player {} submitted empty input for action '{}'",
                            player.getName().getString(), action.label());
                    return;
                }

                // Reject if exceeds max length
                if (packet.inputValue.length() > inputConfig.maxLength()) {
                    SimChatMod.LOGGER.warn("Player {} input exceeds max length ({} > {}) for action '{}'",
                            player.getName().getString(), packet.inputValue.length(), inputConfig.maxLength(), action.label());
                    return;
                }

                // Reject if pattern doesn't match
                if (inputConfig.pattern() != null && !packet.inputValue.matches(inputConfig.pattern())) {
                    SimChatMod.LOGGER.warn("Player {} input doesn't match pattern for action '{}'",
                            player.getName().getString(), action.label());
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
                                player.getName().getString(), inputItem.item(), inputItem.count(), action.label());
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

            boolean actionsConsumed = manager.consumeActions(team, packet.messageId);
            ChatMessage updatedMessage = actionsConsumed ? stored.message().withoutActions() : null;
            int updatedMessageIndex = stored.messageIndex();

            // Add player reply if specified (with template processing for input values)
            long worldDay = player.level().getDayTime() / 24000L;
            ChatMessage replyMessage = null;
            int replyIndex = -1;
            if (action.replyText() != null && !action.replyText().isEmpty()) {
                TemplateEngine.TemplateCompilation replyCompilation = TemplateEngine.compile(action.replyText(), callbackCtx);
                String processedReply = replyCompilation.compiledText();
                ChatMessage reply = ChatMessage.fromPlayer(
                        packet.entityId,
                        player.getUUID(),
                        player.getName().getString(),
                        null, // Use default template {team:title}, resolved at render time
                        processedReply,
                        replyCompilation.runtimeTemplate(),
                        worldDay
                );
                replyIndex = manager.appendMessage(team, reply);
                if (replyIndex >= 0) {
                    replyMessage = reply;
                }
            }

            // Create transaction system message after reply
            ChatMessage transactionMessage = null;
            int transactionIndex = -1;
            if (!action.itemsInput().isEmpty() || !action.itemsOutput().isEmpty()) {
                ChatMessage transactionMsg = ChatMessage.transactionMessage(
                        packet.entityId, worldDay,
                        action.itemsInput(), action.itemsOutput()
                );
                transactionIndex = manager.appendMessage(team, transactionMsg);
                if (transactionIndex >= 0) {
                    transactionMessage = transactionMsg;
                }
            }

            // Save team data
            manager.saveTeam(team);

            int totalCount = manager.getMessageCount(team, packet.entityId);
            if (actionsConsumed && updatedMessage != null) {
                NetworkHandler.sendMessageToTeam(team, updatedMessage, updatedMessageIndex, totalCount, player.server, false);
            }
            if (replyMessage != null) {
                int replyTotal = manager.getMessageCount(team, packet.entityId);
                NetworkHandler.sendMessageToTeam(team, replyMessage, replyIndex, replyTotal, player.server, false);
            }
            if (transactionMessage != null) {
                int transactionTotal = manager.getMessageCount(team, packet.entityId);
                NetworkHandler.sendMessageToTeam(team, transactionMessage, transactionIndex, transactionTotal, player.server, false);
            }

            // Trigger next dialogue state if specified
            if (action.nextState() != null && !action.nextState().isEmpty()) {
                ResourceLocation nextStateId = ResourceLocation.tryParse(action.nextState());
                if (nextStateId != null) {
                    DialogueData nextDialogue = DialogueManager.get(nextStateId);
                    if (nextDialogue != null) {
                        CallbackContext nextCtx = new CallbackContext(player, team, nextDialogue.entityId());
                        ChatMessage nextMessage = nextDialogue.toMessage(nextDialogue.entityId(), worldDay, nextCtx, nextStateId);
                        int delayTicks = calculateDelayTicks(nextMessage.content());
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
                    String cmd = TemplateEngine.resolveWithPrefixes(command, callbackCtx);
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
