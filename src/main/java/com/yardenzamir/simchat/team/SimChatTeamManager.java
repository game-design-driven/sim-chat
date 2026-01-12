package com.yardenzamir.simchat.team;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yardenzamir.simchat.SimChatMod;
import com.yardenzamir.simchat.network.NetworkHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * World-level manager for all teams. Stores team index in SavedData,
 * individual team data in JSON files.
 */
public class SimChatTeamManager extends SavedData {

    private static final String DATA_NAME = "simchat_teams";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final MinecraftServer server;
    private final Map<String, TeamData> teamCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerToTeam = new ConcurrentHashMap<>();

    private SimChatTeamManager(MinecraftServer server) {
        this.server = server;
    }

    public static SimChatTeamManager get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                tag -> load(server, tag),
                () -> new SimChatTeamManager(server),
                DATA_NAME
        );
    }

    // === Team Operations ===

    /**
     * Creates a new team and adds the creator as the first member.
     */
    public TeamData createTeam(ServerPlayer creator, String title) {
        String id = TeamData.generateId();
        // Ensure unique ID (extremely unlikely collision but be safe)
        while (teamCache.containsKey(id) || teamFileExists(id)) {
            id = TeamData.generateId();
        }

        TeamData team = new TeamData(id, title);
        team.addMember(creator.getUUID());

        // Update mappings
        String oldTeamId = playerToTeam.put(creator.getUUID(), id);
        teamCache.put(id, team);

        // Remove from old team if existed
        if (oldTeamId != null) {
            TeamData oldTeam = getTeam(oldTeamId);
            if (oldTeam != null) {
                oldTeam.removeMember(creator.getUUID());
                saveTeam(oldTeam);
            }
        }

        saveTeam(team);
        setDirty();

        // Sync with vanilla scoreboard team
        addPlayerToVanillaTeam(creator, team);

        return team;
    }

    /**
     * Gets a team by ID, loading from disk if needed.
     */
    public @Nullable TeamData getTeam(String teamId) {
        TeamData team = teamCache.get(teamId);
        if (team == null) {
            team = loadTeam(teamId);
            if (team != null) {
                teamCache.put(teamId, team);
            }
        }
        return team;
    }

    /**
     * Gets the team for a player, or null if not assigned.
     */
    public @Nullable TeamData getPlayerTeam(ServerPlayer player) {
        String teamId = playerToTeam.get(player.getUUID());
        return teamId != null ? getTeam(teamId) : null;
    }

    /**
     * Gets or creates a team for a player. Used on first join.
     */
    public TeamData getOrCreatePlayerTeam(ServerPlayer player) {
        TeamData team = getPlayerTeam(player);
        if (team == null) {
            // Create solo team with default name
            String defaultTitle = "Team " + TeamData.generateId().substring(0, 4).toUpperCase();
            team = createTeam(player, defaultTitle);
        }
        return team;
    }

    /**
     * Changes a player's team. Removes from old team, adds to new.
     */
    public void changeTeam(ServerPlayer player, String newTeamId) {
        TeamData newTeam = getTeam(newTeamId);
        if (newTeam == null) {
            SimChatMod.LOGGER.warn("Attempted to join non-existent team: {}", newTeamId);
            return;
        }

        UUID playerId = player.getUUID();
        String oldTeamId = playerToTeam.get(playerId);

        // Remove from old team
        if (oldTeamId != null && !oldTeamId.equals(newTeamId)) {
            TeamData oldTeam = getTeam(oldTeamId);
            if (oldTeam != null) {
                oldTeam.removeMember(playerId);
                saveTeam(oldTeam);
            }
        }

        // Add to new team
        newTeam.addMember(playerId);
        playerToTeam.put(playerId, newTeamId);
        saveTeam(newTeam);
        setDirty();

        // Sync with vanilla scoreboard team
        addPlayerToVanillaTeam(player, newTeam);

        // Sync to player
        NetworkHandler.syncTeamToPlayer(player, newTeam);
    }

    /**
     * Gets all online members of a team.
     */
    public List<ServerPlayer> getOnlineTeamMembers(TeamData team) {
        List<ServerPlayer> online = new ArrayList<>();
        for (UUID memberId : team.getMembers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player != null) {
                online.add(player);
            }
        }
        return online;
    }

    /**
     * Gets all teams.
     */
    public Collection<TeamData> getAllTeams() {
        // Load all teams from disk
        Path teamsDir = getTeamsDirectory();
        if (Files.exists(teamsDir)) {
            try (var stream = Files.list(teamsDir)) {
                stream.filter(p -> p.toString().endsWith(".json"))
                        .forEach(p -> {
                            String filename = p.getFileName().toString();
                            String teamId = filename.substring(0, filename.length() - 5);
                            if (!teamCache.containsKey(teamId)) {
                                TeamData team = loadTeam(teamId);
                                if (team != null) {
                                    teamCache.put(teamId, team);
                                }
                            }
                        });
            } catch (IOException e) {
                SimChatMod.LOGGER.error("Failed to list teams directory", e);
            }
        }
        return teamCache.values();
    }

    /**
     * Finds a team by ID or title. Tries exact ID match first, then title match (case-insensitive).
     */
    @Nullable
    public TeamData findTeam(String idOrName) {
        // Try exact ID match first
        TeamData team = getTeam(idOrName);
        if (team != null) {
            return team;
        }

        // Try title match (case-insensitive)
        for (TeamData t : getAllTeams()) {
            if (t.getTitle().equalsIgnoreCase(idOrName)) {
                return t;
            }
        }

        return null;
    }

    // === Persistence ===

    private Path getTeamsDirectory() {
        return server.getWorldPath(LevelResource.ROOT).resolve("data/simchat/teams");
    }

    private Path getTeamFile(String teamId) {
        return getTeamsDirectory().resolve(teamId + ".json");
    }

    private boolean teamFileExists(String teamId) {
        return Files.exists(getTeamFile(teamId));
    }

    public void saveTeam(TeamData team) {
        Path file = getTeamFile(team.getId());
        try {
            Files.createDirectories(file.getParent());
            String json = GSON.toJson(team.toJson());
            Files.writeString(file, json);
        } catch (IOException e) {
            SimChatMod.LOGGER.error("Failed to save team {}", team.getId(), e);
        }
    }

    private @Nullable TeamData loadTeam(String teamId) {
        Path file = getTeamFile(teamId);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            String json = Files.readString(file);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            return TeamData.fromJson(obj);
        } catch (Exception e) {
            SimChatMod.LOGGER.error("Failed to load team {}", teamId, e);
            return null;
        }
    }

    public void saveAllTeams() {
        for (TeamData team : teamCache.values()) {
            saveTeam(team);
        }
    }

    // === SavedData for player-to-team index ===

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag mappings = new ListTag();
        for (Map.Entry<UUID, String> entry : playerToTeam.entrySet()) {
            CompoundTag mapping = new CompoundTag();
            mapping.putUUID("player", entry.getKey());
            mapping.putString("team", entry.getValue());
            mappings.add(mapping);
        }
        tag.put("playerToTeam", mappings);
        return tag;
    }

    private static SimChatTeamManager load(MinecraftServer server, CompoundTag tag) {
        SimChatTeamManager manager = new SimChatTeamManager(server);
        if (tag.contains("playerToTeam")) {
            ListTag mappings = tag.getList("playerToTeam", Tag.TAG_COMPOUND);
            for (int i = 0; i < mappings.size(); i++) {
                CompoundTag mapping = mappings.getCompound(i);
                UUID playerId = mapping.getUUID("player");
                String teamId = mapping.getString("team");
                manager.playerToTeam.put(playerId, teamId);
            }
        }
        return manager;
    }

    // === Vanilla Team Sync ===

    private static final String VANILLA_TEAM_PREFIX = "simchat_";

    /**
     * Maps our color index (0-15) to ChatFormatting values.
     */
    private static final ChatFormatting[] COLOR_MAP = {
            ChatFormatting.BLACK,        // 0
            ChatFormatting.DARK_BLUE,    // 1
            ChatFormatting.DARK_GREEN,   // 2
            ChatFormatting.DARK_AQUA,    // 3
            ChatFormatting.DARK_RED,     // 4
            ChatFormatting.DARK_PURPLE,  // 5
            ChatFormatting.GOLD,         // 6
            ChatFormatting.GRAY,         // 7
            ChatFormatting.DARK_GRAY,    // 8
            ChatFormatting.BLUE,         // 9
            ChatFormatting.GREEN,        // 10
            ChatFormatting.AQUA,         // 11
            ChatFormatting.RED,          // 12
            ChatFormatting.LIGHT_PURPLE, // 13
            ChatFormatting.YELLOW,       // 14
            ChatFormatting.WHITE         // 15
    };

    /**
     * Gets or creates the vanilla team for a SimChat team.
     */
    private PlayerTeam getOrCreateVanillaTeam(TeamData team) {
        Scoreboard scoreboard = server.getScoreboard();
        String vanillaName = VANILLA_TEAM_PREFIX + team.getId();

        PlayerTeam vanillaTeam = scoreboard.getPlayerTeam(vanillaName);
        if (vanillaTeam == null) {
            vanillaTeam = scoreboard.addPlayerTeam(vanillaName);
        }

        // Update color based on team color
        int colorIndex = Math.max(0, Math.min(15, team.getColor()));
        vanillaTeam.setColor(COLOR_MAP[colorIndex]);

        return vanillaTeam;
    }

    /**
     * Syncs a player to the vanilla team (public for login handler).
     */
    public void syncPlayerToVanillaTeam(ServerPlayer player, TeamData team) {
        addPlayerToVanillaTeam(player, team);
    }

    /**
     * Adds a player to the vanilla team.
     */
    private void addPlayerToVanillaTeam(ServerPlayer player, TeamData team) {
        Scoreboard scoreboard = server.getScoreboard();
        PlayerTeam vanillaTeam = getOrCreateVanillaTeam(team);
        scoreboard.addPlayerToTeam(player.getScoreboardName(), vanillaTeam);
    }

    /**
     * Removes a player from their vanilla team.
     */
    private void removePlayerFromVanillaTeam(ServerPlayer player) {
        Scoreboard scoreboard = server.getScoreboard();
        scoreboard.removePlayerFromTeam(player.getScoreboardName());
    }

    /**
     * Updates the vanilla team color (call when team title changes).
     */
    public void updateVanillaTeamColor(TeamData team) {
        getOrCreateVanillaTeam(team);
    }
}
