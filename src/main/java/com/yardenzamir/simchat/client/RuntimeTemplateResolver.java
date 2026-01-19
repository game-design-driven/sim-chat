package com.yardenzamir.simchat.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.yardenzamir.simchat.SimChatMod;
import com.yardenzamir.simchat.config.ClientConfig;
import com.yardenzamir.simchat.data.ChatAction;
import com.yardenzamir.simchat.data.ChatMessage;
import com.yardenzamir.simchat.network.NetworkHandler;
import com.yardenzamir.simchat.network.ResolveTemplateRequestPacket;

public final class RuntimeTemplateResolver {

    private static final Map<CacheKey, String> localCache = new HashMap<>();
    private static final Map<CacheKey, String> serverCache = new HashMap<>();
    private static final Set<CacheKey> pending = new HashSet<>();
    private static final Deque<TemplateRequest> highPriorityQueue = new ArrayDeque<>();
    private static final Deque<TemplateRequest> lowPriorityQueue = new ArrayDeque<>();
    private static final Map<CacheKey, ResolutionPriority> queuedPriority = new HashMap<>();

    public enum ResolutionPriority {
        HIGH,
        LOW
    }

    private RuntimeTemplateResolver() {}

    public static void clear() {
        localCache.clear();
        serverCache.clear();
        pending.clear();
        highPriorityQueue.clear();
        lowPriorityQueue.clear();
        queuedPriority.clear();
    }

    public static boolean needsPreload() {
        return localCache.isEmpty() && serverCache.isEmpty();
    }

    /**
     * Preload templates for a list of messages.
     * Always processes newest→oldest (backwards) for better UX.
     */
    public static void preloadMessages(List<ChatMessage> messages) {
        preloadMessages(messages, ResolutionPriority.LOW);
    }

    public static void preloadMessages(List<ChatMessage> messages, ResolutionPriority priority) {
        // Always iterate backwards (newest first)
        for (int i = messages.size() - 1; i >= 0; i--) {
            preloadMessage(messages.get(i), priority);
        }
    }

    public static void preloadMessage(ChatMessage message) {
        preloadMessage(message, ResolutionPriority.LOW);
    }

    public static void preloadMessage(ChatMessage message, ResolutionPriority priority) {
        resolveAndCache(message, "content", message.contentTemplate(), message.content(), priority);
        resolveAndCache(message, "senderName", message.senderNameTemplate(), message.senderName(), priority);
        if (message.senderSubtitle() != null || message.senderSubtitleTemplate() != null) {
            resolveAndCache(message, "senderSubtitle", message.senderSubtitleTemplate(), message.senderSubtitle(), priority);
        }
        for (int i = 0; i < message.actions().size(); i++) {
            ChatAction action = message.actions().get(i);
            resolveAndCache(message, "actionLabel:" + i, action.labelTemplate(), action.label(), priority);
        }
    }

    public static String resolveContent(ChatMessage message) {
        return resolveContent(message, ResolutionPriority.LOW);
    }

    public static String resolveContent(ChatMessage message, ResolutionPriority priority) {
        return getCachedValue(message, "content", message.contentTemplate(), message.content(), priority);
    }

    public static String resolveSenderName(ChatMessage message) {
        return resolveSenderName(message, ResolutionPriority.LOW);
    }

    public static String resolveSenderName(ChatMessage message, ResolutionPriority priority) {
        return getCachedValue(message, "senderName", message.senderNameTemplate(), message.senderName(), priority);
    }

    public static @Nullable String resolveSenderSubtitle(ChatMessage message) {
        return resolveSenderSubtitle(message, ResolutionPriority.LOW);
    }

    public static @Nullable String resolveSenderSubtitle(ChatMessage message, ResolutionPriority priority) {
        if (message.senderSubtitle() == null && message.senderSubtitleTemplate() == null) {
            return null;
        }
        return getCachedValue(message, "senderSubtitle", message.senderSubtitleTemplate(), message.senderSubtitle(), priority);
    }

    public static String resolveActionLabel(ChatMessage message, int actionIndex, ChatAction action) {
        return resolveActionLabel(message, actionIndex, action, ResolutionPriority.LOW);
    }

    public static String resolveActionLabel(ChatMessage message, int actionIndex, ChatAction action, ResolutionPriority priority) {
        return getCachedValue(message, "actionLabel:" + actionIndex, action.labelTemplate(), action.label(), priority);
    }

    public static void updateFromServer(UUID messageId, String fieldKey, String value) {
        CacheKey key = new CacheKey(messageId, fieldKey);
        serverCache.put(key, value);
        pending.remove(key);
        queuedPriority.remove(key);
        if (ClientConfig.DEBUG.get()) {
            SimChatMod.LOGGER.info("[RuntimeResolver] {}.{} resolved from server -> '{}'", messageId, fieldKey, value);
        }
    }

    public static void flushQueuedRequests() {
        int maxRequests = ClientConfig.TEMPLATE_REQUESTS_PER_TICK.get();
        int sent = 0;
        while (sent < maxRequests) {
            TemplateRequest request = pollNextRequest();
            if (request == null) {
                break;
            }
            if (!pending.contains(request.key())) {
                continue;
            }
            NetworkHandler.CHANNEL.sendToServer(
                    new ResolveTemplateRequestPacket(request.key().messageId(), request.key().fieldKey(), request.entityId(), request.template())
            );
            sent++;
        }
    }

    public static void retainMessages(Set<UUID> messageIds) {
        localCache.keySet().removeIf(key -> !messageIds.contains(key.messageId()));
        serverCache.keySet().removeIf(key -> !messageIds.contains(key.messageId()));
        pending.removeIf(key -> !messageIds.contains(key.messageId()));
        queuedPriority.keySet().removeIf(key -> !messageIds.contains(key.messageId()));
        highPriorityQueue.removeIf(request -> !messageIds.contains(request.key().messageId()));
        lowPriorityQueue.removeIf(request -> !messageIds.contains(request.key().messageId()));
    }

    private static void queueRequest(CacheKey key, String entityId, String template, ResolutionPriority priority) {
        ResolutionPriority existing = queuedPriority.get(key);
        if (existing == ResolutionPriority.HIGH) {
            return;
        }
        
        TemplateRequest request = new TemplateRequest(key, entityId, template);
        if (existing == ResolutionPriority.LOW && priority == ResolutionPriority.HIGH) {
            // Upgrade: remove from low, add to front of high (preserve newest-first order)
            lowPriorityQueue.removeIf(r -> r.key().equals(key));
            highPriorityQueue.addFirst(request);
        } else if (existing == ResolutionPriority.LOW) {
            return;
        } else if (priority == ResolutionPriority.HIGH) {
            highPriorityQueue.add(request);
        } else {
            lowPriorityQueue.add(request);
        }
        queuedPriority.put(key, priority);
    }

    private static @Nullable TemplateRequest pollNextRequest() {
        TemplateRequest request = highPriorityQueue.pollFirst();
        if (request != null) {
            return request;
        }
        return lowPriorityQueue.pollFirst();
    }

    /**
     * Returns template with placeholders replaced by "..." or fallback if no template.
     */
    private static String templateWithPlaceholders(@Nullable String template, @Nullable String fallback) {
        if (template == null || template.isEmpty()) {
            return fallback != null ? fallback : "...";
        }
        // Replace all {placeholder} patterns with "..."
        return template.replaceAll("\\{[^}]+\\}", "...");
    }

    private static String getCachedValue(ChatMessage message, String fieldKey, @Nullable String template,
                                          @Nullable String fallback, ResolutionPriority priority) {
        CacheKey key = new CacheKey(message.messageId(), fieldKey);

        // Return server-resolved value if available
        String serverValue = serverCache.get(key);
        if (serverValue != null) {
            return serverValue;
        }

        // Return template with placeholders replaced by "..." if waiting for server resolution
        if (pending.contains(key)) {
            if (template != null && !template.isEmpty()) {
                queueRequest(key, message.entityId(), template, priority);
            }
            return templateWithPlaceholders(template, fallback);
        }

        // Return local value if available
        String localValue = localCache.get(key);
        if (localValue != null) {
            return localValue;
        }

        // Resolve and cache
        resolveAndCache(message, fieldKey, template, fallback, priority);

        // Check if now pending after resolution attempt
        if (pending.contains(key)) {
            return templateWithPlaceholders(template, fallback);
        }

        String cached = localCache.get(key);
        return cached != null ? cached : (fallback != null ? fallback : "");
    }

    private static void resolveAndCache(ChatMessage message, String fieldKey, @Nullable String template,
                                        @Nullable String fallback, ResolutionPriority priority) {
        CacheKey key = new CacheKey(message.messageId(), fieldKey);
        if (localCache.containsKey(key) || serverCache.containsKey(key)) {
            return;
        }
        if (pending.contains(key)) {
            queueRequest(key, message.entityId(), template, priority);
            return;
        }
        boolean debug = ClientConfig.DEBUG.get();
        if (template == null || template.isEmpty()) {
            String value = fallback != null ? fallback : "";
            localCache.put(key, value);
            if (debug) {
                SimChatMod.LOGGER.info("[RuntimeResolver] {}.{} cached fallback -> '{}'", message.messageId(), fieldKey, value);
            }
            return;
        }

        String locallyResolved = ClientTemplateEngine.process(template);
        localCache.put(key, locallyResolved);
        if (debug) {
            SimChatMod.LOGGER.info("[RuntimeResolver] {}.{} cached local -> '{}'", message.messageId(), fieldKey, locallyResolved);
        }

        if (!ClientTemplateEngine.hasPlaceholders(locallyResolved)) {
            return;
        }

        pending.add(key);
        queueRequest(key, message.entityId(), template, priority);
        if (debug) {
            SimChatMod.LOGGER.info("[RuntimeResolver] {}.{} queued server resolution for '{}'", message.messageId(), fieldKey, template);
        }
    }

    private record TemplateRequest(CacheKey key, String entityId, String template) {}

    private record CacheKey(UUID messageId, String fieldKey) {}
}
