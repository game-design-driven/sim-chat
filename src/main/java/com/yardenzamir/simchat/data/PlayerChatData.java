package com.yardenzamir.simchat.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stores all chat conversations for a single player.
 * Each conversation is keyed by entity ID.
 */
public class PlayerChatData {

    private static final String TAG_CONVERSATIONS = "conversations";
    private static final String TAG_ENTITY_ID = "entityId";
    private static final String TAG_MESSAGES = "messages";
    private static final String TAG_READ_COUNTS = "readCounts";
    private static final String TAG_COUNT = "count";

    private final Map<String, List<ChatMessage>> conversations = new LinkedHashMap<>();
    private final Map<String, Integer> readMessageCounts = new HashMap<>();
    private final Set<String> typingEntities = new HashSet<>();
    private int revision = 0;

    /**
     * Gets the revision number, incremented on each change.
     * Used by UI to detect when refresh is needed.
     */
    public int getRevision() {
        return revision;
    }

    /**
     * Adds a message to the conversation with the given entity.
     * Creates the conversation if it doesn't exist.
     */
    public void addMessage(ChatMessage message) {
        conversations.computeIfAbsent(message.entityId(), k -> new ArrayList<>())
                .add(message);
        revision++;
    }

    /**
     * Gets all messages for a conversation with the given entity.
     * Returns empty list if no conversation exists.
     */
    public List<ChatMessage> getMessages(String entityId) {
        return conversations.getOrDefault(entityId, Collections.emptyList());
    }

    /**
     * Gets entity IDs for all conversations, ordered by most recent message.
     */
    public List<String> getEntityIds() {
        List<Map.Entry<String, List<ChatMessage>>> entries = new ArrayList<>(conversations.entrySet());

        entries.sort((a, b) -> {
            long timeA = a.getValue().isEmpty() ? 0 : a.getValue().get(a.getValue().size() - 1).timestamp();
            long timeB = b.getValue().isEmpty() ? 0 : b.getValue().get(b.getValue().size() - 1).timestamp();
            return Long.compare(timeB, timeA);
        });

        List<String> result = new ArrayList<>();
        for (Map.Entry<String, List<ChatMessage>> entry : entries) {
            result.add(entry.getKey());
        }
        return result;
    }

    /**
     * Gets the display name for an entity from most recent message or EntityConfigManager.
     */
    public String getEntityDisplayName(String entityId) {
        List<ChatMessage> messages = conversations.get(entityId);
        if (messages != null) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                ChatMessage msg = messages.get(i);
                if (!msg.isPlayerMessage()) {
                    return msg.senderName();
                }
            }
        }
        return EntityConfigManager.getName(entityId, null);
    }

    /**
     * Gets the image ID for an entity from most recent message or EntityConfigManager.
     */
    public String getEntityImageId(String entityId) {
        List<ChatMessage> messages = conversations.get(entityId);
        if (messages != null) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                ChatMessage msg = messages.get(i);
                if (!msg.isPlayerMessage() && msg.senderImageId() != null) {
                    return msg.senderImageId();
                }
            }
        }
        return EntityConfigManager.getAvatar(entityId, null);
    }

    /**
     * Gets the subtitle for an entity from its most recent message.
     */
    public @Nullable String getEntitySubtitle(String entityId) {
        List<ChatMessage> messages = conversations.get(entityId);
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (!msg.isPlayerMessage() && msg.senderSubtitle() != null) {
                return msg.senderSubtitle();
            }
        }
        return null;
    }

    /**
     * Gets the last message in a conversation for preview.
     */
    public ChatMessage getLastMessage(String entityId) {
        List<ChatMessage> messages = conversations.get(entityId);
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        return messages.get(messages.size() - 1);
    }

    /**
     * Sets typing state for an entity.
     */
    public void setTyping(String entityId, boolean typing) {
        if (typing) {
            if (typingEntities.add(entityId)) {
                // Ensure entity appears in list even without messages
                conversations.computeIfAbsent(entityId, k -> new ArrayList<>());
                revision++;
            }
        } else {
            if (typingEntities.remove(entityId)) {
                revision++;
            }
        }
    }

    /**
     * Checks if an entity is currently typing.
     */
    public boolean isTyping(String entityId) {
        return typingEntities.contains(entityId);
    }

    /**
     * Checks if an entity has unread messages.
     */
    public boolean hasUnread(String entityId) {
        List<ChatMessage> messages = conversations.get(entityId);
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        int readCount = readMessageCounts.getOrDefault(entityId, 0);
        return messages.size() > readCount;
    }

    /**
     * Gets count of unread messages for an entity.
     */
    public int getUnreadCount(String entityId) {
        List<ChatMessage> messages = conversations.get(entityId);
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int readCount = readMessageCounts.getOrDefault(entityId, 0);
        return Math.max(0, messages.size() - readCount);
    }

    /**
     * Marks all messages for an entity as read.
     */
    public void markAsRead(String entityId) {
        List<ChatMessage> messages = conversations.get(entityId);
        if (messages != null) {
            int oldCount = readMessageCounts.getOrDefault(entityId, 0);
            if (oldCount != messages.size()) {
                readMessageCounts.put(entityId, messages.size());
                revision++;
            }
        }
    }

    /**
     * Clears actions from a specific message (after player clicks one).
     * Returns true if successful.
     */
    public boolean consumeActions(String entityId, int messageIndex) {
        List<ChatMessage> messages = conversations.get(entityId);
        if (messages == null || messageIndex < 0 || messageIndex >= messages.size()) {
            return false;
        }
        ChatMessage original = messages.get(messageIndex);
        if (original.actions().isEmpty()) {
            return false;
        }
        messages.set(messageIndex, original.withoutActions());
        revision++;
        return true;
    }

    /**
     * Clears conversation with a specific entity.
     */
    public void clearConversation(String entityId) {
        conversations.remove(entityId);
        readMessageCounts.remove(entityId);
        revision++;
    }

    /**
     * Clears all conversations.
     */
    public void clearAll() {
        conversations.clear();
        readMessageCounts.clear();
        revision++;
    }

    /**
     * Checks if any conversations exist.
     */
    public boolean hasConversations() {
        return !conversations.isEmpty();
    }

    public CompoundTag toNbt() {
        CompoundTag root = new CompoundTag();
        ListTag convList = new ListTag();

        for (Map.Entry<String, List<ChatMessage>> entry : conversations.entrySet()) {
            CompoundTag convTag = new CompoundTag();
            convTag.putString(TAG_ENTITY_ID, entry.getKey());

            ListTag messageList = new ListTag();
            for (ChatMessage msg : entry.getValue()) {
                messageList.add(msg.toNbt());
            }
            convTag.put(TAG_MESSAGES, messageList);
            convList.add(convTag);
        }

        root.put(TAG_CONVERSATIONS, convList);

        // Save read counts
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

        if (root.contains(TAG_CONVERSATIONS)) {
            ListTag convList = root.getList(TAG_CONVERSATIONS, Tag.TAG_COMPOUND);
            for (int i = 0; i < convList.size(); i++) {
                CompoundTag convTag = convList.getCompound(i);
                String entityId = convTag.getString(TAG_ENTITY_ID);
                ListTag messageList = convTag.getList(TAG_MESSAGES, Tag.TAG_COMPOUND);

                List<ChatMessage> messages = new ArrayList<>();
                for (int j = 0; j < messageList.size(); j++) {
                    messages.add(ChatMessage.fromNbt(messageList.getCompound(j)));
                }
                data.conversations.put(entityId, messages);
            }
        }

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
        this.conversations.clear();
        this.readMessageCounts.clear();
        for (Map.Entry<String, List<ChatMessage>> entry : other.conversations.entrySet()) {
            this.conversations.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        this.readMessageCounts.putAll(other.readMessageCounts);
        this.revision++;
    }
}
