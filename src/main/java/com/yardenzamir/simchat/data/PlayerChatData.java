package com.yardenzamir.simchat.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;

/**
 * Stores per-player read receipt data.
 * Conversations and flags are stored in TeamData.
 */
public class PlayerChatData {

    private static final String TAG_READ_COUNTS = "readCounts";
    private static final String TAG_ENTITY_ID = "entityId";
    private static final String TAG_COUNT = "count";

    private final Map<String, Integer> readMessageCounts = new HashMap<>();
    private int revision = 0;

    public int getRevision() {
        return revision;
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

        return data;
    }

    /**
     * Copies data from another PlayerChatData instance.
     */
    public void copyFrom(PlayerChatData other) {
        this.readMessageCounts.clear();
        this.readMessageCounts.putAll(other.readMessageCounts);
        this.revision++;
    }
}
