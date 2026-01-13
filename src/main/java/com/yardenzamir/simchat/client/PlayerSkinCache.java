package com.yardenzamir.simchat.client;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache for player skin textures.
 * Handles fetching skins for offline players asynchronously.
 */
@OnlyIn(Dist.CLIENT)
public class PlayerSkinCache {

    // Cache only for fetched skins (offline players)
    private static final Map<UUID, ResourceLocation> fetchedCache = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> pending = new ConcurrentHashMap<>();

    /**
     * Gets the skin texture for a player UUID.
     * For online players, queries PlayerInfo directly (not cached).
     * For offline players, fetches from Mojang and caches result.
     */
    public static ResourceLocation getSkin(UUID playerUuid) {
        if (playerUuid == null) {
            return DefaultPlayerSkin.getDefaultSkin();
        }

        Minecraft mc = Minecraft.getInstance();

        // Check if this is the local player - always use their current skin
        if (mc.player != null && mc.player.getUUID().equals(playerUuid)) {
            return mc.player.getSkinTextureLocation();
        }

        // Check if player is online (has PlayerInfo) - use their live skin
        if (mc.getConnection() != null) {
            PlayerInfo playerInfo = mc.getConnection().getPlayerInfo(playerUuid);
            if (playerInfo != null) {
                return playerInfo.getSkinLocation();
            }
        }

        // Player is offline - check our fetched cache
        ResourceLocation cached = fetchedCache.get(playerUuid);
        if (cached != null) {
            return cached;
        }

        // Not cached - request async fetch
        if (!pending.containsKey(playerUuid)) {
            pending.put(playerUuid, true);
            fetchSkinAsync(mc, playerUuid);
        }

        // Return default while loading
        return DefaultPlayerSkin.getDefaultSkin(playerUuid);
    }

    private static void fetchSkinAsync(Minecraft mc, UUID playerUuid) {
        Thread fetchThread = new Thread(() -> {
            try {
                // Create profile and fill properties from Mojang's session server
                GameProfile profile = new GameProfile(playerUuid, "");
                GameProfile filled = mc.getMinecraftSessionService().fillProfileProperties(profile, false);

                // Register skin and cache the result
                mc.execute(() -> {
                    mc.getSkinManager().registerSkins(filled, (type, location, texture) -> {
                        if (type == MinecraftProfileTexture.Type.SKIN) {
                            fetchedCache.put(playerUuid, location);
                            pending.remove(playerUuid);
                        }
                    }, true);
                });
            } catch (Exception e) {
                // Failed to fetch - allow retry next time by just clearing pending
                pending.remove(playerUuid);
            }
        }, "SimChat-SkinFetch-" + playerUuid.toString().substring(0, 8));
        fetchThread.setDaemon(true);
        fetchThread.start();
    }

    /**
     * Clears only the pending requests. Called on disconnect.
     * Fetched skins remain cached as they are loaded via SkinManager.
     */
    public static void clearPending() {
        pending.clear();
    }
}
