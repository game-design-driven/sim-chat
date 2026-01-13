package com.yardenzamir.simchat.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Represents a single chat message in a conversation.
 */
public final class ChatMessage {

    private static final String TAG_TYPE = "type";
    private static final String TAG_ENTITY_ID = "entityId";
    private static final String TAG_SENDER_NAME = "senderName";
    private static final String TAG_SENDER_SUBTITLE = "senderSubtitle";
    private static final String TAG_SENDER_IMAGE = "senderImage";
    private static final String TAG_CONTENT = "content";
    private static final String TAG_WORLD_DAY = "worldDay";
    private static final String TAG_ACTIONS = "actions";
    private static final String TAG_TRANSACTION_INPUT = "transactionInput";
    private static final String TAG_TRANSACTION_OUTPUT = "transactionOutput";
    private static final String TAG_PLAYER_UUID = "playerUuid";

    private final MessageType type;
    private final String entityId;
    private final String senderName;
    private final @Nullable String senderSubtitle;
    private final @Nullable String senderImageId;
    private final String content;
    private final long worldDay;
    private final List<ChatAction> actions;
    private final List<ChatAction.ActionItem> transactionInput;
    private final List<ChatAction.ActionItem> transactionOutput;
    private final @Nullable UUID playerUuid;

    private ChatMessage(MessageType type, String entityId, String senderName,
                        @Nullable String senderSubtitle, @Nullable String senderImageId,
                        String content, long worldDay, List<ChatAction> actions,
                        List<ChatAction.ActionItem> transactionInput, List<ChatAction.ActionItem> transactionOutput,
                        @Nullable UUID playerUuid) {
        this.type = type;
        this.entityId = entityId;
        this.senderName = senderName;
        this.senderSubtitle = senderSubtitle;
        this.senderImageId = senderImageId;
        this.content = content;
        this.worldDay = worldDay;
        this.actions = List.copyOf(actions);
        this.transactionInput = List.copyOf(transactionInput);
        this.transactionOutput = List.copyOf(transactionOutput);
        this.playerUuid = playerUuid;
    }

    /**
     * Creates an entity message with optional action buttons.
     *
     * @param worldDay The current world day (level.getDayTime() / 24000)
     */
    public static ChatMessage fromEntity(String entityId, String displayName, @Nullable String subtitle,
                                         String imageId, String content, long worldDay, List<ChatAction> actions) {
        return new ChatMessage(MessageType.ENTITY, entityId, displayName, subtitle, imageId, content, worldDay, actions,
                Collections.emptyList(), Collections.emptyList(), null);
    }

    /**
     * Creates a player reply message.
     *
     * @param playerUuid Player's UUID for skin lookup
     * @param teamTitle Team name shown as subtitle
     * @param worldDay The current world day (level.getDayTime() / 24000)
     */
    public static ChatMessage fromPlayer(String entityId, UUID playerUuid, String playerName,
                                         @Nullable String teamTitle, String content, long worldDay) {
        return new ChatMessage(MessageType.PLAYER, entityId, playerName, teamTitle, null, content, worldDay,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), playerUuid);
    }

    /**
     * Creates a system message (no sender, describes an event).
     *
     * @param worldDay The current world day (level.getDayTime() / 24000)
     */
    public static ChatMessage systemMessage(String entityId, String content, long worldDay) {
        return new ChatMessage(MessageType.SYSTEM, entityId, "", null, null, content, worldDay, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), null);
    }

    /**
     * Creates a transaction system message showing items exchanged.
     *
     * @param worldDay The current world day (level.getDayTime() / 24000)
     * @param inputItems Items given by player (can be empty)
     * @param outputItems Items received by player (can be empty)
     */
    public static ChatMessage transactionMessage(String entityId, long worldDay,
                                                  List<ChatAction.ActionItem> inputItems,
                                                  List<ChatAction.ActionItem> outputItems) {
        return new ChatMessage(MessageType.SYSTEM, entityId, "", null, null, "", worldDay, Collections.emptyList(),
                inputItems, outputItems, null);
    }

    public MessageType type() {
        return type;
    }

    public boolean isPlayerMessage() {
        return type == MessageType.PLAYER;
    }

    public boolean isSystemMessage() {
        return type == MessageType.SYSTEM;
    }

    public boolean isEntityMessage() {
        return type == MessageType.ENTITY;
    }

    public String entityId() {
        return entityId;
    }

    public String senderName() {
        return senderName;
    }

    public @Nullable String senderSubtitle() {
        return senderSubtitle;
    }

    public @Nullable String senderImageId() {
        return senderImageId;
    }

    public String content() {
        return content;
    }

    /**
     * Gets the world day when this message was sent.
     * Use for display as "Day X".
     */
    public long worldDay() {
        return worldDay;
    }

    /**
     * Legacy: returns worldDay for sorting compatibility.
     */
    public long timestamp() {
        return worldDay;
    }

    public List<ChatAction> actions() {
        return actions;
    }

    public List<ChatAction.ActionItem> transactionInput() {
        return transactionInput;
    }

    public List<ChatAction.ActionItem> transactionOutput() {
        return transactionOutput;
    }

    /**
     * Gets the player UUID for skin lookup (only set for player messages).
     */
    public @Nullable UUID playerUuid() {
        return playerUuid;
    }

    /**
     * Returns a copy of this message with actions cleared.
     */
    public ChatMessage withoutActions() {
        return new ChatMessage(type, entityId, senderName, senderSubtitle, senderImageId,
                content, worldDay, Collections.emptyList(), transactionInput, transactionOutput, playerUuid);
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_TYPE, type.ordinal());
        tag.putString(TAG_ENTITY_ID, entityId);
        tag.putString(TAG_SENDER_NAME, senderName);
        if (senderSubtitle != null) {
            tag.putString(TAG_SENDER_SUBTITLE, senderSubtitle);
        }
        if (senderImageId != null) {
            tag.putString(TAG_SENDER_IMAGE, senderImageId);
        }
        tag.putString(TAG_CONTENT, content);
        tag.putLong(TAG_WORLD_DAY, worldDay);

        if (!actions.isEmpty()) {
            ListTag actionList = new ListTag();
            for (ChatAction action : actions) {
                actionList.add(action.toNbt());
            }
            tag.put(TAG_ACTIONS, actionList);
        }

        if (!transactionInput.isEmpty()) {
            tag.put(TAG_TRANSACTION_INPUT, itemListToNbt(transactionInput));
        }
        if (!transactionOutput.isEmpty()) {
            tag.put(TAG_TRANSACTION_OUTPUT, itemListToNbt(transactionOutput));
        }
        if (playerUuid != null) {
            tag.putUUID(TAG_PLAYER_UUID, playerUuid);
        }
        return tag;
    }

    private static ListTag itemListToNbt(List<ChatAction.ActionItem> items) {
        ListTag list = new ListTag();
        for (ChatAction.ActionItem item : items) {
            list.add(item.toNbt());
        }
        return list;
    }

    private static List<ChatAction.ActionItem> itemListFromNbt(CompoundTag tag, String key) {
        List<ChatAction.ActionItem> items = new ArrayList<>();
        if (tag.contains(key)) {
            ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                items.add(ChatAction.ActionItem.fromNbt(list.getCompound(i)));
            }
        }
        return items;
    }

    public static ChatMessage fromNbt(CompoundTag tag) {
        List<ChatAction> actions = new ArrayList<>();
        if (tag.contains(TAG_ACTIONS)) {
            ListTag actionList = tag.getList(TAG_ACTIONS, Tag.TAG_COMPOUND);
            for (int i = 0; i < actionList.size(); i++) {
                actions.add(ChatAction.fromNbt(actionList.getCompound(i)));
            }
        }

        long day = tag.contains(TAG_WORLD_DAY) ? tag.getLong(TAG_WORLD_DAY) : 0;

        List<ChatAction.ActionItem> transactionInput = itemListFromNbt(tag, TAG_TRANSACTION_INPUT);
        List<ChatAction.ActionItem> transactionOutput = itemListFromNbt(tag, TAG_TRANSACTION_OUTPUT);

        UUID playerUuid = tag.hasUUID(TAG_PLAYER_UUID) ? tag.getUUID(TAG_PLAYER_UUID) : null;

        MessageType type = MessageType.fromOrdinal(tag.getInt(TAG_TYPE));

        return new ChatMessage(
                type,
                tag.getString(TAG_ENTITY_ID),
                tag.getString(TAG_SENDER_NAME),
                tag.contains(TAG_SENDER_SUBTITLE) ? tag.getString(TAG_SENDER_SUBTITLE) : null,
                tag.contains(TAG_SENDER_IMAGE) ? tag.getString(TAG_SENDER_IMAGE) : null,
                tag.getString(TAG_CONTENT),
                day,
                actions,
                transactionInput,
                transactionOutput,
                playerUuid
        );
    }
}
