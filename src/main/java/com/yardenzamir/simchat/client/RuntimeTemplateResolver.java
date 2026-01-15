package com.yardenzamir.simchat.client;

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

    private RuntimeTemplateResolver() {}

    public static void clear() {
        localCache.clear();
        serverCache.clear();
        pending.clear();
    }

    public static boolean needsPreload() {
        return localCache.isEmpty() && serverCache.isEmpty();
    }

    public static void preloadMessages(List<ChatMessage> messages) {
        for (ChatMessage message : messages) {
            preloadMessage(message);
        }
    }

    public static void preloadMessage(ChatMessage message) {
        resolveAndCache(message, "content", message.contentTemplate(), message.content());
        resolveAndCache(message, "senderName", message.senderNameTemplate(), message.senderName());
        if (message.senderSubtitle() != null || message.senderSubtitleTemplate() != null) {
            resolveAndCache(message, "senderSubtitle", message.senderSubtitleTemplate(), message.senderSubtitle());
        }
        for (int i = 0; i < message.actions().size(); i++) {
            ChatAction action = message.actions().get(i);
            resolveAndCache(message, "actionLabel:" + i, action.labelTemplate(), action.label());
        }
    }

    public static String resolveContent(ChatMessage message) {
        return getCachedValue(message, "content", message.contentTemplate(), message.content());
    }

    public static String resolveSenderName(ChatMessage message) {
        return getCachedValue(message, "senderName", message.senderNameTemplate(), message.senderName());
    }

    public static @Nullable String resolveSenderSubtitle(ChatMessage message) {
        if (message.senderSubtitle() == null && message.senderSubtitleTemplate() == null) {
            return null;
        }
        return getCachedValue(message, "senderSubtitle", message.senderSubtitleTemplate(), message.senderSubtitle());
    }

    public static String resolveActionLabel(ChatMessage message, int actionIndex, ChatAction action) {
        return getCachedValue(message, "actionLabel:" + actionIndex, action.labelTemplate(), action.label());
    }

    public static void updateFromServer(UUID messageId, String fieldKey, String value) {
        CacheKey key = new CacheKey(messageId, fieldKey);
        serverCache.put(key, value);
        pending.remove(key);
        if (ClientConfig.DEBUG.get()) {
            SimChatMod.LOGGER.info("[RuntimeResolver] {}.{} resolved from server -> '{}'", messageId, fieldKey, value);
        }
    }

    private static String getCachedValue(ChatMessage message, String fieldKey, @Nullable String template, @Nullable String fallback) {
        CacheKey key = new CacheKey(message.messageId(), fieldKey);
        String serverValue = serverCache.get(key);
        if (serverValue != null) {
            return serverValue;
        }
        String localValue = localCache.get(key);
        if (localValue != null) {
            return localValue;
        }
        resolveAndCache(message, fieldKey, template, fallback);
        String cached = localCache.get(key);
        return cached != null ? cached : (fallback != null ? fallback : "");
    }

    private static void resolveAndCache(ChatMessage message, String fieldKey, @Nullable String template, @Nullable String fallback) {
        CacheKey key = new CacheKey(message.messageId(), fieldKey);
        if (localCache.containsKey(key) || serverCache.containsKey(key)) {
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

        if (!pending.contains(key)) {
            pending.add(key);
            if (debug) {
                SimChatMod.LOGGER.info("[RuntimeResolver] {}.{} requesting server resolution for '{}'", message.messageId(), fieldKey, template);
            }
            NetworkHandler.CHANNEL.sendToServer(
                    new ResolveTemplateRequestPacket(message.messageId(), fieldKey, message.entityId(), template)
            );
        }
    }

    private record CacheKey(UUID messageId, String fieldKey) {}
}
