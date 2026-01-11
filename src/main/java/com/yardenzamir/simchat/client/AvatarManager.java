package com.yardenzamir.simchat.client;

import com.yardenzamir.simchat.SimChatMod;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages loading and caching avatar images from the config folder.
 * Images are loaded from: config/simchat/avatars/<imageId>.png
 * Automatically reloads when files are added or modified.
 */
public class AvatarManager {

    private static final Map<String, CachedTexture> TEXTURE_CACHE = new HashMap<>();
    private static final ResourceLocation FALLBACK_TEXTURE = new ResourceLocation("textures/misc/unknown_server.png");

    private static Path avatarsFolder;

    private record CachedTexture(ResourceLocation location, long modTime, boolean isFallback) {}

    public static void init() {
        avatarsFolder = FMLPaths.CONFIGDIR.get().resolve("simchat").resolve("avatars");
        try {
            Files.createDirectories(avatarsFolder);
        } catch (IOException e) {
            SimChatMod.LOGGER.error("Failed to create avatars folder", e);
        }
    }

    /**
     * Gets the texture ResourceLocation for an avatar image.
     * Automatically reloads if the file has been added or modified.
     *
     * @param imageId The image ID (filename without extension)
     * @return ResourceLocation for the texture, or fallback if not found
     */
    public static ResourceLocation getTexture(String imageId) {
        if (imageId == null || imageId.isEmpty()) {
            return FALLBACK_TEXTURE;
        }

        Path imagePath = avatarsFolder.resolve(imageId + ".png");
        long currentModTime = getModTime(imagePath);

        CachedTexture cached = TEXTURE_CACHE.get(imageId);

        // Reload if: no cache, file modified, or was fallback but file now exists
        if (cached == null ||
            (currentModTime != 0 && currentModTime != cached.modTime) ||
            (cached.isFallback && currentModTime != 0)) {

            // Release old texture if it wasn't fallback
            if (cached != null && !cached.isFallback) {
                Minecraft.getInstance().getTextureManager().release(cached.location);
            }

            CachedTexture newTexture = loadTexture(imageId, imagePath, currentModTime);
            TEXTURE_CACHE.put(imageId, newTexture);
            return newTexture.location;
        }

        return cached.location;
    }

    /**
     * Clears the texture cache. Call when reloading resources.
     */
    public static void clearCache() {
        for (CachedTexture cached : TEXTURE_CACHE.values()) {
            if (!cached.isFallback) {
                Minecraft.getInstance().getTextureManager().release(cached.location);
            }
        }
        TEXTURE_CACHE.clear();
    }

    private static long getModTime(Path path) {
        try {
            if (Files.exists(path)) {
                return Files.getLastModifiedTime(path).toMillis();
            }
        } catch (IOException ignored) {}
        return 0;
    }

    private static CachedTexture loadTexture(String imageId, Path imagePath, long modTime) {
        if (!Files.exists(imagePath)) {
            SimChatMod.LOGGER.warn("Avatar image not found: {}", imagePath);
            return new CachedTexture(FALLBACK_TEXTURE, 0, true);
        }

        try (InputStream stream = Files.newInputStream(imagePath)) {
            NativeImage image = NativeImage.read(stream);
            DynamicTexture texture = new DynamicTexture(image);

            ResourceLocation location = SimChatMod.id("avatar/" + imageId);
            Minecraft.getInstance().getTextureManager().register(location, texture);

            SimChatMod.LOGGER.debug("Loaded avatar texture: {}", imageId);
            return new CachedTexture(location, modTime, false);
        } catch (IOException e) {
            SimChatMod.LOGGER.error("Failed to load avatar image: {}", imagePath, e);
            return new CachedTexture(FALLBACK_TEXTURE, 0, true);
        }
    }

    /**
     * Gets the path to the avatars folder for users to add images.
     */
    public static Path getAvatarsFolder() {
        return avatarsFolder;
    }
}
