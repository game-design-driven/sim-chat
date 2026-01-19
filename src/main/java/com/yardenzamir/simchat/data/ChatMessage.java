package com.yardenzamir.simchat.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.GsonHelper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a single chat message in a conversation.
 */
public final class ChatMessage {

    private static final String TAG_TYPE = "type";
    private static final String TAG_MESSAGE_ID = "messageId";
    private static final String TAG_ENTITY_ID = "entityId";
    private static final String TAG_SENDER_NAME = "senderName";
    private static final String TAG_SENDER_NAME_TEMPLATE = "senderNameTemplate";
    private static final String TAG_SENDER_SUBTITLE = "senderSubtitle";
    private static final String TAG_SENDER_SUBTITLE_TEMPLATE = "senderSubtitleTemplate";
    private static final String TAG_SENDER_IMAGE = "senderImage";
    private static final String TAG_CONTENT = "content";
    private static final String TAG_CONTENT_TEMPLATE = "contentTemplate";
    private static final String TAG_WORLD_DAY = "worldDay";
    private static final String TAG_ACTIONS = "actions";
    private static final String TAG_TRANSACTION_INPUT = "transactionInput";
    private static final String TAG_TRANSACTION_OUTPUT = "transactionOutput";
    private static final String TAG_PLAYER_UUID = "playerUuid";

    /**
     * Default subtitle template for player messages.
     * Resolved at render time via ClientTemplateEngine.
     */
    public static final String DEFAULT_PLAYER_SUBTITLE = "{team:title}";

    private final MessageType type;
    private final UUID messageId;
    private final String entityId;
    private final String senderName;
    private final @Nullable String senderNameTemplate;
    private final @Nullable String senderSubtitle;
    private final @Nullable String senderSubtitleTemplate;
    private final @Nullable String senderImageId;
    private final String content;
    private final @Nullable String contentTemplate;
    private final long worldDay;
    private final List<ChatAction> actions;
    private final List<ChatAction.ActionItem> transactionInput;
    private final List<ChatAction.ActionItem> transactionOutput;
    private final @Nullable UUID playerUuid;

    private ChatMessage(MessageType type, UUID messageId, String entityId, String senderName,
                        @Nullable String senderNameTemplate, @Nullable String senderSubtitle,
                        @Nullable String senderSubtitleTemplate, @Nullable String senderImageId,
                        String content, @Nullable String contentTemplate, long worldDay, List<ChatAction> actions,
                        List<ChatAction.ActionItem> transactionInput, List<ChatAction.ActionItem> transactionOutput,
                        @Nullable UUID playerUuid) {
        this.type = type;
        this.messageId = messageId;
        this.entityId = entityId;
        this.senderName = senderName;
        this.senderNameTemplate = senderNameTemplate;
        this.senderSubtitle = senderSubtitle;
        this.senderSubtitleTemplate = senderSubtitleTemplate;
        this.senderImageId = senderImageId;
        this.content = content;
        this.contentTemplate = contentTemplate;
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
        return fromEntity(entityId, displayName, subtitle, imageId, content,
                null, null, null, worldDay, actions);
    }

    public static ChatMessage fromEntity(String entityId, String displayName, @Nullable String subtitle,
                                         String imageId, String content, @Nullable String nameTemplate,
                                         @Nullable String subtitleTemplate, @Nullable String contentTemplate,
                                         long worldDay, List<ChatAction> actions) {
        return new ChatMessage(MessageType.ENTITY, UUID.randomUUID(), entityId, displayName,
                nameTemplate, subtitle, subtitleTemplate, imageId, content, contentTemplate, worldDay, actions,
                Collections.emptyList(), Collections.emptyList(), null);
    }

    /**
     * Creates a player reply message.
     *
     * @param playerUuid Player's UUID for skin lookup
     * @param subtitleTemplate Subtitle template (use null for default {team:title})
     * @param worldDay The current world day (level.getDayTime() / 24000)
     */
    public static ChatMessage fromPlayer(String entityId, UUID playerUuid, String playerName,
                                         @Nullable String subtitleTemplate, String content,
                                         @Nullable String contentTemplate, long worldDay) {
        String subtitle = subtitleTemplate != null ? subtitleTemplate : DEFAULT_PLAYER_SUBTITLE;
        return new ChatMessage(MessageType.PLAYER, UUID.randomUUID(), entityId, playerName, null,
                subtitle, subtitle, null, content, contentTemplate, worldDay,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), playerUuid);
    }

    /**
     * Creates a system message (no sender, describes an event).
     *
     * @param worldDay The current world day (level.getDayTime() / 24000)
     */
    public static ChatMessage systemMessage(String entityId, String content, long worldDay) {
        return systemMessage(entityId, content, null, worldDay);
    }

    public static ChatMessage systemMessage(String entityId, String content, @Nullable String contentTemplate, long worldDay) {
        return new ChatMessage(MessageType.SYSTEM, UUID.randomUUID(), entityId, "", null, null,
                null, null, content, contentTemplate, worldDay, Collections.emptyList(),
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
        return new ChatMessage(MessageType.SYSTEM, UUID.randomUUID(), entityId, "", null, null,
                null, null, "", null, worldDay, Collections.emptyList(),
                inputItems, outputItems, null);
    }

    public MessageType type() {
        return type;
    }

    public UUID messageId() {
        return messageId;
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

    public @Nullable String senderNameTemplate() {
        return senderNameTemplate;
    }

    public @Nullable String senderSubtitle() {
        return senderSubtitle;
    }

    public @Nullable String senderSubtitleTemplate() {
        return senderSubtitleTemplate;
    }

    public @Nullable String senderImageId() {
        return senderImageId;
    }

    public String content() {
        return content;
    }

    public @Nullable String contentTemplate() {
        return contentTemplate;
    }

    /**
     * Gets the world day when this message was sent.
     * Use for display as "Day X".
     */
    public long worldDay() {
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
        return new ChatMessage(type, messageId, entityId, senderName, senderNameTemplate, senderSubtitle,
                senderSubtitleTemplate, senderImageId, content, contentTemplate, worldDay,
                Collections.emptyList(), transactionInput, transactionOutput, playerUuid);
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_TYPE, type.ordinal());
        tag.putUUID(TAG_MESSAGE_ID, messageId);
        tag.putString(TAG_ENTITY_ID, entityId);
        tag.putString(TAG_SENDER_NAME, senderName);
        if (senderNameTemplate != null) {
            tag.putString(TAG_SENDER_NAME_TEMPLATE, senderNameTemplate);
        }
        if (senderSubtitle != null) {
            tag.putString(TAG_SENDER_SUBTITLE, senderSubtitle);
        }
        if (senderSubtitleTemplate != null) {
            tag.putString(TAG_SENDER_SUBTITLE_TEMPLATE, senderSubtitleTemplate);
        }
        if (senderImageId != null) {
            tag.putString(TAG_SENDER_IMAGE, senderImageId);
        }
        tag.putString(TAG_CONTENT, content);
        if (contentTemplate != null) {
            tag.putString(TAG_CONTENT_TEMPLATE, contentTemplate);
        }
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

        UUID messageId = tag.hasUUID(TAG_MESSAGE_ID) ? tag.getUUID(TAG_MESSAGE_ID) : UUID.randomUUID();

        return new ChatMessage(
                type,
                messageId,
                tag.getString(TAG_ENTITY_ID),
                tag.getString(TAG_SENDER_NAME),
                tag.contains(TAG_SENDER_NAME_TEMPLATE) ? tag.getString(TAG_SENDER_NAME_TEMPLATE) : null,
                tag.contains(TAG_SENDER_SUBTITLE) ? tag.getString(TAG_SENDER_SUBTITLE) : null,
                tag.contains(TAG_SENDER_SUBTITLE_TEMPLATE) ? tag.getString(TAG_SENDER_SUBTITLE_TEMPLATE) : null,
                tag.contains(TAG_SENDER_IMAGE) ? tag.getString(TAG_SENDER_IMAGE) : null,
                tag.getString(TAG_CONTENT),
                tag.contains(TAG_CONTENT_TEMPLATE) ? tag.getString(TAG_CONTENT_TEMPLATE) : null,
                day,
                actions,
                transactionInput,
                transactionOutput,
                playerUuid
        );
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty(TAG_TYPE, type.ordinal());
        json.addProperty(TAG_MESSAGE_ID, messageId.toString());
        json.addProperty(TAG_ENTITY_ID, entityId);
        json.addProperty(TAG_SENDER_NAME, senderName);
        if (senderNameTemplate != null) json.addProperty(TAG_SENDER_NAME_TEMPLATE, senderNameTemplate);
        if (senderSubtitle != null) json.addProperty(TAG_SENDER_SUBTITLE, senderSubtitle);
        if (senderSubtitleTemplate != null) json.addProperty(TAG_SENDER_SUBTITLE_TEMPLATE, senderSubtitleTemplate);
        if (senderImageId != null) json.addProperty(TAG_SENDER_IMAGE, senderImageId);
        json.addProperty(TAG_CONTENT, content);
        if (contentTemplate != null) json.addProperty(TAG_CONTENT_TEMPLATE, contentTemplate);
        json.addProperty(TAG_WORLD_DAY, worldDay);

        if (!actions.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (ChatAction action : actions) arr.add(action.toJson());
            json.add(TAG_ACTIONS, arr);
        }
        if (!transactionInput.isEmpty()) json.add(TAG_TRANSACTION_INPUT, itemListToJson(transactionInput));
        if (!transactionOutput.isEmpty()) json.add(TAG_TRANSACTION_OUTPUT, itemListToJson(transactionOutput));
        if (playerUuid != null) json.addProperty(TAG_PLAYER_UUID, playerUuid.toString());
        return json;
    }

    private static JsonArray itemListToJson(List<ChatAction.ActionItem> items) {
        JsonArray arr = new JsonArray();
        for (ChatAction.ActionItem item : items) arr.add(item.toJson());
        return arr;
    }

    private static List<ChatAction.ActionItem> itemListFromJson(JsonObject json, String key) {
        List<ChatAction.ActionItem> items = new ArrayList<>();
        if (json.has(key)) {
            for (var el : GsonHelper.getAsJsonArray(json, key)) {
                items.add(ChatAction.ActionItem.fromJson(el.getAsJsonObject()));
            }
        }
        return items;
    }

    public static ChatMessage fromJson(JsonObject json) {
        List<ChatAction> actions = new ArrayList<>();
        if (json.has(TAG_ACTIONS)) {
            for (var el : GsonHelper.getAsJsonArray(json, TAG_ACTIONS)) {
                actions.add(ChatAction.fromJson(el.getAsJsonObject()));
            }
        }

        return new ChatMessage(
                MessageType.fromOrdinal(GsonHelper.getAsInt(json, TAG_TYPE)),
                UUID.fromString(GsonHelper.getAsString(json, TAG_MESSAGE_ID)),
                GsonHelper.getAsString(json, TAG_ENTITY_ID),
                GsonHelper.getAsString(json, TAG_SENDER_NAME),
                json.has(TAG_SENDER_NAME_TEMPLATE) ? GsonHelper.getAsString(json, TAG_SENDER_NAME_TEMPLATE) : null,
                json.has(TAG_SENDER_SUBTITLE) ? GsonHelper.getAsString(json, TAG_SENDER_SUBTITLE) : null,
                json.has(TAG_SENDER_SUBTITLE_TEMPLATE) ? GsonHelper.getAsString(json, TAG_SENDER_SUBTITLE_TEMPLATE) : null,
                json.has(TAG_SENDER_IMAGE) ? GsonHelper.getAsString(json, TAG_SENDER_IMAGE) : null,
                GsonHelper.getAsString(json, TAG_CONTENT),
                json.has(TAG_CONTENT_TEMPLATE) ? GsonHelper.getAsString(json, TAG_CONTENT_TEMPLATE) : null,
                GsonHelper.getAsLong(json, TAG_WORLD_DAY, 0),
                actions,
                itemListFromJson(json, TAG_TRANSACTION_INPUT),
                itemListFromJson(json, TAG_TRANSACTION_OUTPUT),
                json.has(TAG_PLAYER_UUID) ? UUID.fromString(GsonHelper.getAsString(json, TAG_PLAYER_UUID)) : null
        );
    }
}
