package com.yardenzamir.simchat.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stores per-player read receipt data.
 * Conversations and flags are stored in TeamData.
 */
public class PlayerChatData {

    private static final String TAG_READ_COUNTS = "readCounts";
    private static final String TAG_ENTITY_ID = "entityId";
    private static final String TAG_COUNT = "count";
    private static final String TAG_FOCUS_ENTRIES = "focusEntries";
    private static final String TAG_MESSAGE_ID = "messageId";
    private static final String TAG_MESSAGE_INDEX = "messageIndex";
    private static final String TAG_LAST_FOCUSED_ENTITY = "lastFocusedEntity";

    private final Map<String, Integer> readMessageCounts = new HashMap<>();
    private final Map<String, FocusInfo> focusedMessages = new HashMap<>();
    private String lastFocusedEntityId = "";
    private int revision = 0;

    public record FocusInfo(String entityId, UUID messageId, int messageIndex) {}

    public int getRevision() {
        return revision;
    }

    public String getLastFocusedEntityId() {
        return lastFocusedEntityId;
    }

    public FocusInfo getFocusedMessage(String entityId) {
        return focusedMessages.get(entityId);
    }

    public void setFocusedMessage(String entityId, UUID messageId, int messageIndex) {
        FocusInfo current = focusedMessages.get(entityId);
        if (current != null
                && current.messageId().equals(messageId)
                && current.messageIndex() == messageIndex) {
            lastFocusedEntityId = entityId;
            return;
        }
        focusedMessages.put(entityId, new FocusInfo(entityId, messageId, messageIndex));
        lastFocusedEntityId = entityId;
        revision++;
    }

    public void clearFocusedMessage(String entityId) {
        boolean changed = focusedMessages.remove(entityId) != null;
        if (entityId.equals(lastFocusedEntityId)) {
            lastFocusedEntityId = "";
            changed = true;
        }
        if (changed) {
            revision++;
        }
    }

    public void clearAllFocus() {
        if (!focusedMessages.isEmpty() || !lastFocusedEntityId.isEmpty()) {
            focusedMessages.clear();
            lastFocusedEntityId = "";
            revision++;
        }
    }

    /**
     * Gets the read message count for an entity.
     */
    public int getReadCount(String entityId) {
        return readMessageCounts.getOrDefault(entityId, 0);
    }

    /**
     * Checks if there are unread messages for an entity.
     * @param totalMessages Total message count from TeamData
     */
    public boolean hasUnread(String entityId, int totalMessages) {
        int readCount = readMessageCounts.getOrDefault(entityId, 0);
        return totalMessages > readCount;
    }

    /**
     * Gets the count of unread messages for an entity.
     * @param totalMessages Total message count from TeamData
     */
    public int getUnreadCount(String entityId, int totalMessages) {
        int readCount = readMessageCounts.getOrDefault(entityId, 0);
        return Math.max(0, totalMessages - readCount);
    }

    /**
     * Marks messages as read up to the given count.
     */
    public void markAsRead(String entityId, int messageCount) {
        int oldCount = readMessageCounts.getOrDefault(entityId, 0);
        if (oldCount != messageCount) {
            readMessageCounts.put(entityId, messageCount);
            revision++;
        }
    }

    /**
     * Clears read count for an entity (used when conversation is cleared).
     */
    public void clearReadCount(String entityId) {
        if (readMessageCounts.remove(entityId) != null) {
            revision++;
        }
    }

    /**
     * Clears all read counts.
     */
    public void clearAll() {
        readMessageCounts.clear();
        revision++;
    }

    public CompoundTag toNbt() {
        CompoundTag root = new CompoundTag();

        ListTag readList = new ListTag();
        for (Map.Entry<String, Integer> entry : readMessageCounts.entrySet()) {
            CompoundTag readTag = new CompoundTag();
            readTag.putString(TAG_ENTITY_ID, entry.getKey());
            readTag.putInt(TAG_COUNT, entry.getValue());
            readList.add(readTag);
        }
        root.put(TAG_READ_COUNTS, readList);

        if (!focusedMessages.isEmpty()) {
            ListTag focusList = new ListTag();
            for (FocusInfo info : focusedMessages.values()) {
                CompoundTag focusTag = new CompoundTag();
                focusTag.putString(TAG_ENTITY_ID, info.entityId());
                focusTag.putString(TAG_MESSAGE_ID, info.messageId().toString());
                focusTag.putInt(TAG_MESSAGE_INDEX, info.messageIndex());
                focusList.add(focusTag);
            }
            root.put(TAG_FOCUS_ENTRIES, focusList);
        }
        if (!lastFocusedEntityId.isEmpty()) {
            root.putString(TAG_LAST_FOCUSED_ENTITY, lastFocusedEntityId);
        }

        return root;
    }

    public static PlayerChatData fromNbt(CompoundTag root) {
        PlayerChatData data = new PlayerChatData();

        if (root.contains(TAG_READ_COUNTS)) {
            ListTag readList = root.getList(TAG_READ_COUNTS, Tag.TAG_COMPOUND);
            for (int i = 0; i < readList.size(); i++) {
                CompoundTag readTag = readList.getCompound(i);
                data.readMessageCounts.put(
                        readTag.getString(TAG_ENTITY_ID),
                        readTag.getInt(TAG_COUNT)
                );
            }
        }

        if (root.contains(TAG_FOCUS_ENTRIES)) {
            ListTag focusList = root.getList(TAG_FOCUS_ENTRIES, Tag.TAG_COMPOUND);
            for (int i = 0; i < focusList.size(); i++) {
                CompoundTag focusTag = focusList.getCompound(i);
                String entityId = focusTag.getString(TAG_ENTITY_ID);
                String messageId = focusTag.getString(TAG_MESSAGE_ID);
                int messageIndex = focusTag.getInt(TAG_MESSAGE_INDEX);
                try {
                    data.focusedMessages.put(entityId, new FocusInfo(entityId, UUID.fromString(messageId), messageIndex));
                } catch (IllegalArgumentException ignored) {
                    // Skip invalid UUID
                }
            }
        }

        if (root.contains(TAG_LAST_FOCUSED_ENTITY)) {
            data.lastFocusedEntityId = root.getString(TAG_LAST_FOCUSED_ENTITY);
        }

        return data;
    }

    /**
     * Copies data from another PlayerChatData instance.
     */
    public void copyFrom(PlayerChatData other) {
        this.readMessageCounts.clear();
        this.readMessageCounts.putAll(other.readMessageCounts);
        this.focusedMessages.clear();
        this.focusedMessages.putAll(other.focusedMessages);
        this.lastFocusedEntityId = other.lastFocusedEntityId;
        this.revision++;
    }
}
