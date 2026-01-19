package com.yardenzamir.simchat.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

import com.yardenzamir.simchat.data.ChatMessage;
import com.yardenzamir.simchat.team.TeamData;

/**
 * Client-side cache for the player's current team data.
 * Supports partial message history with lazy loading.
 */
@OnlyIn(Dist.CLIENT)
public class ClientTeamCache {

    private static @Nullable TeamData team;

    // Track loaded message ranges per entity
    private static final Map<String, MessageCache> messageCaches = new HashMap<>();

    /**
     * Tracks loaded messages for a single conversation.
     */
    private static class MessageCache {
        final TreeMap<Integer, ChatMessage> messages = new TreeMap<>(); // Index -> Message
        int totalCount = 0;
        boolean hasOlderMessages = false;

        int getLoadedStart() {
            return messages.isEmpty() ? 0 : messages.firstKey();
        }

        int getLoadedEnd() {
            return messages.isEmpty() ? 0 : messages.lastKey() + 1;
        }

        boolean isLoaded(int index) {
            return messages.containsKey(index);
        }

        List<ChatMessage> getMessageList() {
            return new ArrayList<>(messages.values());
        }
    }

    public static @Nullable TeamData getTeam() {
        return team;
    }

    public static void setTeam(TeamData newTeam) {
        team = newTeam;
        messageCaches.clear();
    }

    /**
     * Set team metadata without messages (used for lazy loading).
     */
    public static void setTeamMetadata(String teamId, String title, int color,
                                       List<UUID> members,
                                       List<String> entityOrder,
                                       Map<String, Integer> messageCountPerEntity,
                                       Map<String, Object> teamData) {
        // Create TeamData with empty conversations
        team = new TeamData(teamId, title);
        team.setColor(color);
        for (UUID member : members) {
            team.addMember(member);
        }
        for (Map.Entry<String, Object> entry : teamData.entrySet()) {
            team.setData(entry.getKey(), entry.getValue());
        }

        messageCaches.clear();
        Set<String> seenEntities = new HashSet<>();
        for (String entityId : entityOrder) {
            int count = messageCountPerEntity.getOrDefault(entityId, 0);
            team.setConversationMeta(entityId, count, null, null);
            MessageCache cache = new MessageCache();
            cache.totalCount = count;
            cache.hasOlderMessages = count > 0;
            messageCaches.put(entityId, cache);
            seenEntities.add(entityId);
        }

        for (Map.Entry<String, Integer> entry : messageCountPerEntity.entrySet()) {
            if (seenEntities.contains(entry.getKey())) {
                continue;
            }
            team.setConversationMeta(entry.getKey(), entry.getValue(), null, null);
            MessageCache cache = new MessageCache();
            cache.totalCount = entry.getValue();
            cache.hasOlderMessages = entry.getValue() > 0;
            messageCaches.put(entry.getKey(), cache);
        }

        // Clear template cache for new team
        RuntimeTemplateResolver.clear();
    }

    /**
     * Add a batch of messages for lazy loading.
     */
    public static void addMessages(String entityId, List<ChatMessage> messages, int totalCount,
                                   int startIndex, boolean hasOlder) {
        MessageCache cache = messageCaches.computeIfAbsent(entityId, k -> new MessageCache());
        int previousLoadedStart = cache.messages.isEmpty() ? Integer.MAX_VALUE : cache.getLoadedStart();

        cache.totalCount = totalCount;

        // Insert messages at their indices
        for (int i = 0; i < messages.size(); i++) {
            cache.messages.put(startIndex + i, messages.get(i));
        }

        cache.hasOlderMessages = cache.totalCount > cache.messages.size();

        boolean loadingOlder = startIndex < previousLoadedStart;
        boolean includesLatest = startIndex + messages.size() >= totalCount;

        if (team != null) {
            TeamData.ConversationMeta meta = team.getConversationMeta(entityId);
            ChatMessage lastMessage = meta != null ? meta.getLastMessage() : null;
            ChatMessage lastEntityMessage = meta != null ? meta.getLastEntityMessage() : null;

            List<ChatMessage> allLoaded = cache.getMessageList();
            team.setConversation(entityId, allLoaded);

            if (includesLatest && !messages.isEmpty()) {
                lastMessage = messages.get(messages.size() - 1);
                ChatMessage entityInBatch = findLastEntityMessage(messages);
                if (entityInBatch != null) {
                    lastEntityMessage = entityInBatch;
                }
            }

            if (loadingOlder || !includesLatest) {
                team.updateConversationMeta(entityId, totalCount, lastMessage, lastEntityMessage);
            } else {
                team.setConversationMeta(entityId, totalCount, lastMessage, lastEntityMessage);
            }
        }
    }

    public static void clear() {
        team = null;
        messageCaches.clear();
    }

    public static boolean hasTeam() {
        return team != null;
    }

    /**
     * Get message cache for an entity (for lazy loading tracking).
     */
    public static @Nullable MessageCache getMessageCache(String entityId) {
        return messageCaches.get(entityId);
    }

    /**
     * Get total message count for an entity.
     */
    public static int getTotalMessageCount(String entityId) {
        MessageCache cache = messageCaches.get(entityId);
        return cache != null ? cache.totalCount : 0;
    }

    /**
     * Check if there are older messages to load.
     */
    public static boolean hasOlderMessages(String entityId) {
        MessageCache cache = messageCaches.get(entityId);
        return cache != null && cache.hasOlderMessages;
    }

    /**
     * Get the index of the oldest loaded message.
     */
    public static int getOldestLoadedIndex(String entityId) {
        MessageCache cache = messageCaches.get(entityId);
        return cache != null ? cache.getLoadedStart() : 0;
    }

    public static Set<UUID> trimToLatest(int maxMessagesPerEntity) {
        Set<UUID> retained = new HashSet<>();
        for (Map.Entry<String, MessageCache> entry : messageCaches.entrySet()) {
            MessageCache cache = entry.getValue();
            if (maxMessagesPerEntity <= 0) {
                cache.messages.clear();
            } else {
                while (cache.messages.size() > maxMessagesPerEntity) {
                    cache.messages.pollFirstEntry();
                }
            }
            cache.hasOlderMessages = cache.totalCount > cache.messages.size();

            if (team != null) {
                team.setConversation(entry.getKey(), cache.getMessageList());
            }

            for (ChatMessage message : cache.messages.values()) {
                retained.add(message.messageId());
            }
        }
        return retained;
    }

    private static @Nullable ChatMessage findLastEntityMessage(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (!message.isPlayerMessage()) {
                return message;
            }
        }
        return null;
    }
}
