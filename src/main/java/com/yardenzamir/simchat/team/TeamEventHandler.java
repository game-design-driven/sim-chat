package com.yardenzamir.simchat.team;

import com.yardenzamir.simchat.SimChatMod;
import com.yardenzamir.simchat.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles player login/logout and server lifecycle events for teams.
 */
@Mod.EventBusSubscriber(modid = SimChatMod.MOD_ID)
public class TeamEventHandler {

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        SimChatTeamManager manager = SimChatTeamManager.get(player.server);
        TeamData team = manager.getOrCreatePlayerTeam(player);

        // Sync player to vanilla scoreboard team (for player list colors)
        manager.syncPlayerToVanillaTeam(player, team);

        // Sync team data to the joining player
        NetworkHandler.syncTeamToPlayer(player, team);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        SimChatTeamManager manager = SimChatTeamManager.get(player.server);
        TeamData team = manager.getPlayerTeam(player);

        // Save team data when player logs out
        if (team != null) {
            manager.saveTeam(team);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Re-sync team data after respawn
        SimChatTeamManager manager = SimChatTeamManager.get(player.server);
        TeamData team = manager.getPlayerTeam(player);
        if (team != null) {
            NetworkHandler.syncTeamToPlayer(player, team);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Re-sync team data after dimension change
        SimChatTeamManager manager = SimChatTeamManager.get(player.server);
        TeamData team = manager.getPlayerTeam(player);
        if (team != null) {
            NetworkHandler.syncTeamToPlayer(player, team);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // Save all teams when server stops
        SimChatTeamManager manager = SimChatTeamManager.get(event.getServer());
        manager.saveAllTeams();
    }
}
