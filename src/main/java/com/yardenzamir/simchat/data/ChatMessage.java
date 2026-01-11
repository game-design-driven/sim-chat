package com.yardenzamir.simchat.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a single chat message in a conversation.
 */
public final class ChatMessage {

    private static final String TAG_IS_PLAYER = "isPlayer";
    private static final String TAG_ENTITY_ID = "entityId";
    private static final String TAG_SENDER_NAME = "senderName";
    private static final String TAG_SENDER_IMAGE = "senderImage";
    private static final String TAG_CONTENT = "content";
    private static final String TAG_WORLD_DAY = "worldDay";
    private static final String TAG_ACTIONS = "actions";
    // Legacy support
    private static final String TAG_TIMESTAMP = "timestamp";

    private final boolean isPlayerMessage;
    private final String entityId;
    private final String senderName;
    private final @Nullable String senderImageId;
    private final String content;
    private final long worldDay;
    private final List<ChatAction> actions;

    private ChatMessage(boolean isPlayerMessage, String entityId, String senderName,
                        @Nullable String senderImageId, String content, long worldDay,
                        List<ChatAction> actions) {
        this.isPlayerMessage = isPlayerMessage;
        this.entityId = entityId;
        this.senderName = senderName;
        this.senderImageId = senderImageId;
        this.content = content;
        this.worldDay = worldDay;
        this.actions = List.copyOf(actions);
    }

    /**
     * Creates an entity message with optional action buttons.
     *
     * @param worldDay The current world day (level.getDayTime() / 24000)
     */
    public static ChatMessage fromEntity(String entityId, String displayName, String imageId,
                                         String content, long worldDay, List<ChatAction> actions) {
        return new ChatMessage(false, entityId, displayName, imageId, content, worldDay, actions);
    }

    /**
     * Creates a player reply message.
     *
     * @param worldDay The current world day (level.getDayTime() / 24000)
     */
    public static ChatMessage fromPlayer(String entityId, String playerName, String content, long worldDay) {
        return new ChatMessage(true, entityId, playerName, null, content, worldDay, Collections.emptyList());
    }

    public boolean isPlayerMessage() {
        return isPlayerMessage;
    }

    public String entityId() {
        return entityId;
    }

    public String senderName() {
        return senderName;
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

    /**
     * Returns a copy of this message with actions cleared.
     */
    public ChatMessage withoutActions() {
        return new ChatMessage(isPlayerMessage, entityId, senderName, senderImageId,
                content, worldDay, Collections.emptyList());
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TAG_IS_PLAYER, isPlayerMessage);
        tag.putString(TAG_ENTITY_ID, entityId);
        tag.putString(TAG_SENDER_NAME, senderName);
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
        return tag;
    }

    public static ChatMessage fromNbt(CompoundTag tag) {
        List<ChatAction> actions = new ArrayList<>();
        if (tag.contains(TAG_ACTIONS)) {
            ListTag actionList = tag.getList(TAG_ACTIONS, Tag.TAG_COMPOUND);
            for (int i = 0; i < actionList.size(); i++) {
                actions.add(ChatAction.fromNbt(actionList.getCompound(i)));
            }
        }

        // Support both new worldDay and legacy timestamp
        long day;
        if (tag.contains(TAG_WORLD_DAY)) {
            day = tag.getLong(TAG_WORLD_DAY);
        } else if (tag.contains(TAG_TIMESTAMP)) {
            // Legacy: convert old timestamp to day 0 (can't recover actual day)
            day = 0;
        } else {
            day = 0;
        }

        return new ChatMessage(
                tag.getBoolean(TAG_IS_PLAYER),
                tag.getString(TAG_ENTITY_ID),
                tag.getString(TAG_SENDER_NAME),
                tag.contains(TAG_SENDER_IMAGE) ? tag.getString(TAG_SENDER_IMAGE) : null,
                tag.getString(TAG_CONTENT),
                day,
                actions
        );
    }
}
