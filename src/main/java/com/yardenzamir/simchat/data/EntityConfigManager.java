package com.yardenzamir.simchat.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.yardenzamir.simchat.SimChatMod;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads entity configuration from config/simchat/entities/<entityId>.json files.
 * Falls back to default.json if entity-specific config not found.
 * Provides fallback values for entity name, subtitle, and avatar when not specified in dialogues.
 */
public class EntityConfigManager {

    private static final Gson GSON = new GsonBuilder().create();
    private static final Path ENTITIES_DIR = FMLPaths.CONFIGDIR.get().resolve("simchat").resolve("entities");
    private static final String DEFAULT_ENTITY_ID = "default";

    private static final Map<String, EntityConfig> cache = new HashMap<>();
    private static final Map<String, Long> lastModified = new HashMap<>();

    /**
     * Entity configuration loaded from config file.
     */
    public record EntityConfig(
            @Nullable String name,
            @Nullable String subtitle,
            @Nullable String avatar
    ) {
        public static EntityConfig fromJson(JsonObject json) {
            String name = GsonHelper.getAsString(json, "name", null);
            String subtitle = GsonHelper.getAsString(json, "subtitle", null);
            String avatar = GsonHelper.getAsString(json, "avatar", null);
            return new EntityConfig(name, subtitle, avatar);
        }
    }

    /**
     * Gets entity configuration for the given entity ID.
     * Falls back to default.json if entity-specific config not found.
     * Automatically reloads if file was modified.
     */
    @Nullable
    public static EntityConfig getConfig(String entityId) {
        if (entityId == null || entityId.isEmpty()) {
            entityId = DEFAULT_ENTITY_ID;
        }

        Path configFile = ENTITIES_DIR.resolve(entityId + ".json");

        if (!Files.exists(configFile)) {
            cache.remove(entityId);
            lastModified.remove(entityId);
            // Fall back to default.json if entity-specific config not found
            if (!DEFAULT_ENTITY_ID.equals(entityId)) {
                return getConfig(DEFAULT_ENTITY_ID);
            }
            return null;
        }

        try {
            long currentModTime = Files.getLastModifiedTime(configFile).toMillis();
            Long cachedModTime = lastModified.get(entityId);

            if (cachedModTime == null || cachedModTime != currentModTime) {
                String content = Files.readString(configFile);
                JsonObject json = GSON.fromJson(content, JsonObject.class);
                EntityConfig config = EntityConfig.fromJson(json);
                cache.put(entityId, config);
                lastModified.put(entityId, currentModTime);
                SimChatMod.LOGGER.debug("Loaded entity config for {}", entityId);
                return config;
            }

            return cache.get(entityId);
        } catch (IOException e) {
            SimChatMod.LOGGER.error("Failed to load entity config for {}: {}", entityId, e.getMessage());
            return null;
        }
    }

    /**
     * Gets entity name, with fallback to config file.
     */
    public static String getName(String entityId, @Nullable String dialogueName) {
        if (dialogueName != null && !dialogueName.isEmpty()) {
            return dialogueName;
        }
        EntityConfig config = getConfig(entityId);
        if (config != null && config.name() != null) {
            return config.name();
        }
        return entityId;
    }

    /**
     * Gets entity subtitle, with fallback to config file.
     */
    @Nullable
    public static String getSubtitle(String entityId, @Nullable String dialogueSubtitle) {
        if (dialogueSubtitle != null && !dialogueSubtitle.isEmpty()) {
            return dialogueSubtitle;
        }
        EntityConfig config = getConfig(entityId);
        return config != null ? config.subtitle() : null;
    }

    /**
     * Gets entity avatar ID, with fallback to config file, then entityId itself.
     */
    public static String getAvatar(String entityId, @Nullable String dialogueAvatar) {
        if (dialogueAvatar != null && !dialogueAvatar.isEmpty()) {
            return dialogueAvatar;
        }
        EntityConfig config = getConfig(entityId);
        if (config != null && config.avatar() != null) {
            return config.avatar();
        }
        return entityId;
    }

    /**
     * Ensures the entities directory exists.
     */
    public static void init() {
        try {
            Files.createDirectories(ENTITIES_DIR);
        } catch (IOException e) {
            SimChatMod.LOGGER.error("Failed to create entities config directory: {}", e.getMessage());
        }
    }
}
