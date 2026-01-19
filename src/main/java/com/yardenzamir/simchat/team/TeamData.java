package com.yardenzamir.simchat.team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.yardenzamir.simchat.data.ChatMessage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Stores all shared data for a team: conversations, flags, and membership.
 * Persisted to SQLite via SimChatDatabase.
 */
public class TeamData {

    private final String id;
    private String title;
    private int color;
    private final Set<UUID> members = new HashSet<>();
    private final Map<String, List<ChatMessage>> conversations = new LinkedHashMap<>();
    private final Map<String, ConversationMeta> conversationMeta = new LinkedHashMap<>();
    private final Map<String, Object> data = new HashMap<>();
    private final transient Set<String> typingEntities = new HashSet<>();
    private int revision = 0;

    public static final String[] COLOR_NAMES = {
            "black", "dark_blue", "dark_green", "dark_aqua",
            "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua",
            "red", "light_purple", "yellow", "white"
    };

    public static final int[] SOFT_COLOR_VALUES = {
            0xFF2B2B2B, // black
            0xFF3B4D8F, // dark_blue
            0xFF3E7A5A, // dark_green
            0xFF3A6E7A, // dark_aqua
            0xFF8A4A4A, // dark_red
            0xFF7A4A8A, // dark_purple
            0xFFB48A55, // gold
            0xFF9A9A9A, // gray
            0xFF5A5A5A, // dark_gray
            0xFF6B7DD9, // blue
            0xFF7BCB8B, // green
            0xFF7BCACD, // aqua
            0xFFE07A7A, // red
            0xFFC590E0, // light_purple
            0xFFE6D37A, // yellow
            0xFFF2F2F2  // white
    };

    public static class ConversationMeta {
        private int messageCount;
        private @Nullable ChatMessage lastMessage;
        private @Nullable ChatMessage lastEntityMessage;

        public ConversationMeta(int messageCount, @Nullable ChatMessage lastMessage, @Nullable ChatMessage lastEntityMessage) {
            this.messageCount = messageCount;
            this.lastMessage = lastMessage;
            this.lastEntityMessage = lastEntityMessage;
        }

        public int getMessageCount() {
            return messageCount;
        }

        public void setMessageCount(int messageCount) {
            this.messageCount = messageCount;
        }

        public @Nullable ChatMessage getLastMessage() {
            return lastMessage;
        }

        public void setLastMessage(@Nullable ChatMessage lastMessage) {
            this.lastMessage = lastMessage;
        }

        public @Nullable ChatMessage getLastEntityMessage() {
            return lastEntityMessage;
        }

        public void setLastEntityMessage(@Nullable ChatMessage lastEntityMessage) {
            this.lastEntityMessage = lastEntityMessage;
        }
    }

    public TeamData(String id, String title) {
        this.id = id;
        this.title = title;
        this.color = generateColorFromTitle(title);
    }

    /**
     * Generates a 7-character team ID from random UUID.
     */
    public static String generateId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 7).toLowerCase();
    }

    /**
     * Generates a color index (0-15) from title hash.
     */
    private static int generateColorFromTitle(String title) {
        return Math.abs(title.hashCode()) % 16;
    }

    // === Getters ===

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public int getColor() {
        return color;
    }

    public String getColorName() {
        return getColorName(color);
    }

    public int getColorValue() {
        return getColorValue(color);
    }

    public static String getColorName(int colorIndex) {
        int index = Math.max(0, Math.min(15, colorIndex));
        return COLOR_NAMES[index];
    }

    public static int getColorValue(int colorIndex) {
        int index = Math.max(0, Math.min(15, colorIndex));
        return SOFT_COLOR_VALUES[index];
    }

    public int getRevision() {
        return revision;
    }

    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    // === Title ===

    public void setTitle(String title) {
        this.title = title;
        this.color = generateColorFromTitle(title);
        revision++;
    }

    public void setColor(int color) {
        this.color = Math.max(0, Math.min(15, color));
        revision++;
    }

    // === Membership ===

    public void addMember(UUID playerId) {
        if (members.add(playerId)) {
            revision++;
        }
    }

    public void removeMember(UUID playerId) {
        if (members.remove(playerId)) {
            revision++;
        }
    }

    public boolean isMember(UUID playerId) {
        return members.contains(playerId);
    }

    public int getMemberCount() {
        return members.size();
    }

    // === Conversations ===

    public void addMessage(ChatMessage message) {
        String entityId = message.entityId();
        // Remove and re-add to update insertion order in LinkedHashMap (most recent last)
        List<ChatMessage> msgs = conversations.remove(entityId);
        if (msgs == null) {
            msgs = new ArrayList<>();
        }
        msgs.add(message);
        conversations.put(entityId, msgs);

        ConversationMeta meta = conversationMeta.get(entityId);
        int newCount = meta != null ? Math.max(meta.getMessageCount() + 1, msgs.size()) : msgs.size();
        ChatMessage lastEntityMessage = message.isPlayerMessage() ? getLastEntityMessage(entityId) : message;
        setConversationMetaInternal(entityId, newCount, message, lastEntityMessage, true);
        revision++;
    }

    public List<ChatMessage> getMessages(String entityId) {
        return conversations.getOrDefault(entityId, Collections.emptyList());
    }

    public void setConversation(String entityId, List<ChatMessage> messages) {
        conversations.put(entityId, new ArrayList<>(messages));
        ChatMessage lastMessage = messages.isEmpty() ? null : messages.get(messages.size() - 1);
        ChatMessage lastEntityMessage = findLastEntityMessage(messages);
        int messageCount = Math.max(messages.size(), getMessageCount(entityId));
        setConversationMetaInternal(entityId, messageCount, lastMessage, lastEntityMessage, false);
        revision++;
    }

    public void setConversationMeta(String entityId, int messageCount, @Nullable ChatMessage lastMessage, @Nullable ChatMessage lastEntityMessage) {
        setConversationMetaInternal(entityId, messageCount, lastMessage, lastEntityMessage, true);
        revision++;
    }

    public void updateConversationMeta(String entityId, int messageCount, @Nullable ChatMessage lastMessage, @Nullable ChatMessage lastEntityMessage) {
        setConversationMetaInternal(entityId, messageCount, lastMessage, lastEntityMessage, false);
        revision++;
    }

    public void recordMessageAdded(String entityId, ChatMessage message, int newTotalCount) {
        ChatMessage lastEntityMessage = message.isPlayerMessage() ? getLastEntityMessage(entityId) : message;
        setConversationMetaInternal(entityId, newTotalCount, message, lastEntityMessage, true);
        revision++;
    }

    private void setConversationMetaInternal(String entityId, int messageCount, @Nullable ChatMessage lastMessage,
                                            @Nullable ChatMessage lastEntityMessage, boolean reorder) {
        ConversationMeta meta = conversationMeta.get(entityId);
        if (meta == null) {
            meta = new ConversationMeta(messageCount, lastMessage, lastEntityMessage);
        } else {
            meta.setMessageCount(messageCount);
            if (lastMessage != null) {
                meta.setLastMessage(lastMessage);
            }
            if (lastEntityMessage != null) {
                meta.setLastEntityMessage(lastEntityMessage);
            }
        }

        if (reorder) {
            conversationMeta.remove(entityId);
        }
        conversationMeta.put(entityId, meta);
    }

    private @Nullable ChatMessage getLastEntityMessage(String entityId) {
        ConversationMeta meta = conversationMeta.get(entityId);
        if (meta != null && meta.getLastEntityMessage() != null) {
            return meta.getLastEntityMessage();
        }
        return findLastEntityMessage(conversations.get(entityId));
    }

    private @Nullable ChatMessage findLastEntityMessage(@Nullable List<ChatMessage> messages) {
        if (messages == null) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (!msg.isPlayerMessage()) {
                return msg;
            }
        }
        return null;
    }

    public int getMessageCount(String entityId) {
        ConversationMeta meta = conversationMeta.get(entityId);
        if (meta != null) {
            return meta.getMessageCount();
        }
        List<ChatMessage> msgs = conversations.get(entityId);
        return msgs != null ? msgs.size() : 0;
    }

    public Map<String, List<ChatMessage>> getConversations() {
        return conversations;
    }

    public @Nullable ConversationMeta getConversationMeta(String entityId) {
        return conversationMeta.get(entityId);
    }

    /**
     * Gets entity IDs ordered by most recent message (reverse insertion order).
     */
    public List<String> getEntityIds() {
        List<String> result = new ArrayList<>(!conversationMeta.isEmpty() ? conversationMeta.keySet() : conversations.keySet());
        Collections.reverse(result);
        return result;
    }

    /**
     * Gets display name for entity from most recent non-player message.
     */
    public @Nullable String getEntityDisplayName(String entityId) {
        ChatMessage message = getLastEntityMessage(entityId);
        return message != null ? message.senderName() : null;
    }

    /**
     * Gets display name template for entity from most recent non-player message.
     */
    public @Nullable String getEntityDisplayNameTemplate(String entityId) {
        ChatMessage message = getLastEntityMessage(entityId);
        return message != null ? message.senderNameTemplate() : null;
    }

    /**
     * Gets image ID for entity from most recent non-player message.
     */
    public @Nullable String getEntityImageId(String entityId) {
        ChatMessage message = getLastEntityMessage(entityId);
        return message != null ? message.senderImageId() : null;
    }

    /**
     * Gets subtitle for entity from most recent non-player message.
     */
    public @Nullable String getEntitySubtitle(String entityId) {
        ChatMessage message = getLastEntityMessage(entityId);
        return message != null ? message.senderSubtitle() : null;
    }

    public @Nullable ChatMessage getLastMessage(String entityId) {
        ConversationMeta meta = conversationMeta.get(entityId);
        if (meta != null && meta.getLastMessage() != null) {
            return meta.getLastMessage();
        }
        List<ChatMessage> messages = conversations.get(entityId);
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        return messages.get(messages.size() - 1);
    }

    public boolean consumeActions(String entityId, UUID messageId) {
        List<ChatMessage> messages = conversations.get(entityId);
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage original = messages.get(i);
            if (!original.messageId().equals(messageId)) {
                continue;
            }
            if (original.actions().isEmpty()) {
                return false;
            }
            messages.set(i, original.withoutActions());
            revision++;
            return true;
        }
        return false;
    }

    public void clearConversation(String entityId) {
        boolean removed = conversations.remove(entityId) != null;
        removed = conversationMeta.remove(entityId) != null || removed;
        if (removed) {
            revision++;
        }
    }

    public void clearAll() {
        conversations.clear();
        conversationMeta.clear();
        revision++;
    }

    public boolean hasConversations() {
        return !conversationMeta.isEmpty() || !conversations.isEmpty();
    }

    // === Typing ===

    /**
     * Sets typing state for an entity.
     * Note: Does NOT increment revision since typing is transient UI state
     * and revision is used for data sync detection.
     */
    public void setTyping(String entityId, boolean typing) {
        if (typing) {
            typingEntities.add(entityId);
            conversations.computeIfAbsent(entityId, k -> new ArrayList<>());
        } else {
            typingEntities.remove(entityId);
        }
    }

    public boolean isTyping(String entityId) {
        return typingEntities.contains(entityId);
    }

    // === Data ===

    /**
     * Sets a data value (string or number).
     */
    public void setData(String key, Object value) {
        data.put(key, value);
        revision++;
    }

    /**
     * Gets a data value, or null if not set.
     */
    @Nullable
    public Object getData(String key) {
        return data.get(key);
    }

    /**
     * Gets a data value as string.
     */
    public String getDataString(String key, String defaultValue) {
        Object val = data.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    /**
     * Gets a data value as number.
     */
    public double getDataNumber(String key, double defaultValue) {
        Object val = data.get(key);
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }

    /**
     * Gets a data value as int.
     */
    public int getDataInt(String key, int defaultValue) {
        return (int) getDataNumber(key, defaultValue);
    }

    /**
     * Adds to a numeric data value (creates if doesn't exist).
     */
    public void addData(String key, double amount) {
        double current = getDataNumber(key, 0);
        data.put(key, current + amount);
        revision++;
    }

    /**
     * Checks if data key exists and is truthy (non-null, non-zero, non-empty).
     */
    public boolean hasData(String key) {
        Object val = data.get(key);
        if (val == null) return false;
        if (val instanceof Boolean b) return b;
        if (val instanceof Number n) return n.doubleValue() != 0;
        if (val instanceof String s) return !s.isEmpty();
        return true;
    }

    /**
     * Removes a data key.
     */
    public void removeData(String key) {
        if (data.remove(key) != null) {
            revision++;
        }
    }

    /**
     * Gets all data keys.
     */
    public Set<String> getDataKeys() {
        return Collections.unmodifiableSet(data.keySet());
    }

    /**
     * Gets all data as unmodifiable map.
     */
    public Map<String, Object> getAllData() {
        return Collections.unmodifiableMap(data);
    }

}
