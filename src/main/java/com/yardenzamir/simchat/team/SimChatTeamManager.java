package com.yardenzamir.simchat.team;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import org.jetbrains.annotations.Nullable;

import com.yardenzamir.simchat.SimChatMod;
import com.yardenzamir.simchat.data.ChatMessage;
import com.yardenzamir.simchat.network.NetworkHandler;
import com.yardenzamir.simchat.storage.SimChatDatabase;

/**
 * World-level manager for all teams. Persists team data and messages in SQLite.
 */
public class SimChatTeamManager {

    private static final Map<MinecraftServer, SimChatTeamManager> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;
    private final SimChatDatabase database;
    private final Map<String, TeamData> teamCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerToTeam = new ConcurrentHashMap<>();

    private SimChatTeamManager(MinecraftServer server) {
        this.server = server;
        this.database = new SimChatDatabase(server);
        this.database.open();
        this.playerToTeam.putAll(database.loadPlayerTeams());
    }

    public static SimChatTeamManager get(MinecraftServer server) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(server, SimChatTeamManager::new);
        }
    }

    public void shutdown() {
        database.close();
        synchronized (INSTANCES) {
            INSTANCES.remove(server);
        }
    }

    // === Team Operations ===

    /**
     * Creates a new team and adds the creator as the first member.
     */
    public TeamData createTeam(ServerPlayer creator, String title) {
        String id = TeamData.generateId();
        while (teamCache.containsKey(id) || database.teamExists(id)) {
            id = TeamData.generateId();
        }

        TeamData team = new TeamData(id, title);
        team.addMember(creator.getUUID());

        String oldTeamId = playerToTeam.put(creator.getUUID(), id);
        database.setPlayerTeam(creator.getUUID(), id);
        teamCache.put(id, team);

        if (oldTeamId != null) {
            TeamData oldTeam = getTeam(oldTeamId);
            if (oldTeam != null) {
                oldTeam.removeMember(creator.getUUID());
                saveTeam(oldTeam);
            }
        }

        saveTeam(team);
        addPlayerToVanillaTeam(creator, team);
        return team;
    }

    /**
     * Gets a team by ID, loading from SQLite if needed.
     */
    public @Nullable TeamData getTeam(String teamId) {
        TeamData team = teamCache.get(teamId);
        if (team == null) {
            team = database.loadTeam(teamId);
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

        if (oldTeamId != null && !oldTeamId.equals(newTeamId)) {
            TeamData oldTeam = getTeam(oldTeamId);
            if (oldTeam != null) {
                oldTeam.removeMember(playerId);
                saveTeam(oldTeam);
            }
        }

        newTeam.addMember(playerId);
        playerToTeam.put(playerId, newTeamId);
        database.setPlayerTeam(playerId, newTeamId);
        saveTeam(newTeam);

        addPlayerToVanillaTeam(player, newTeam);
        NetworkHandler.syncTeamWithLazyLoad(player, newTeam);
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
        for (String teamId : database.loadTeamIds()) {
            teamCache.computeIfAbsent(teamId, database::loadTeam);
        }
        return teamCache.values();
    }

    /**
     * Finds a team by ID or title. Tries exact ID match first, then title match (case-insensitive).
     */
    public @Nullable TeamData findTeam(String idOrName) {
        TeamData team = getTeam(idOrName);
        if (team != null) {
            return team;
        }

        for (TeamData t : getAllTeams()) {
            if (t.getTitle().equalsIgnoreCase(idOrName)) {
                return t;
            }
        }

        return null;
    }

    // === Persistence ===

    public void saveTeam(TeamData team) {
        database.upsertTeam(team);
    }

    public void saveAllTeams() {
        for (TeamData team : teamCache.values()) {
            saveTeam(team);
        }
    }

    // === Message Operations ===

    public int appendMessage(TeamData team, ChatMessage message) {
        int messageIndex = database.insertMessage(team.getId(), message);
        if (messageIndex >= 0) {
            team.recordMessageAdded(message.entityId(), message, messageIndex + 1);
        }
        return messageIndex;
    }

    public List<ChatMessage> loadMessages(TeamData team, String entityId, int startIndex, int count) {
        return database.loadMessages(team.getId(), entityId, startIndex, count);
    }

    public List<ChatMessage> loadOlderMessages(TeamData team, String entityId, int beforeIndex, int count) {
        return database.loadOlderMessages(team.getId(), entityId, beforeIndex, count);
    }

    public int getMessageCount(TeamData team, String entityId) {
        int count = team.getMessageCount(entityId);
        if (count == 0) {
            return database.getMessageCount(team.getId(), entityId);
        }
        return count;
    }

    public @Nullable SimChatDatabase.StoredMessage getMessageById(TeamData team, UUID messageId) {
        return database.loadMessageById(team.getId(), messageId);
    }

    public boolean consumeActions(TeamData team, UUID messageId) {
        SimChatDatabase.StoredMessage stored = database.loadMessageById(team.getId(), messageId);
        if (stored == null) {
            return false;
        }

        ChatMessage message = stored.message();
        if (message.actions().isEmpty()) {
            return false;
        }

        ChatMessage updated = message.withoutActions();
        database.updateMessagePayload(team.getId(), stored.entityId(), stored.messageIndex(), updated);

        TeamData.ConversationMeta meta = team.getConversationMeta(stored.entityId());
        if (meta != null) {
            ChatMessage lastMessage = meta.getLastMessage();
            ChatMessage lastEntityMessage = meta.getLastEntityMessage();
            boolean updatedLastMessage = lastMessage != null && lastMessage.messageId().equals(messageId);
            boolean updatedLastEntity = lastEntityMessage != null && lastEntityMessage.messageId().equals(messageId);

            if (updatedLastMessage || updatedLastEntity) {
                ChatMessage nextLastMessage = updatedLastMessage ? updated : lastMessage;
                ChatMessage nextLastEntity = updatedLastEntity ? updated : lastEntityMessage;
                team.updateConversationMeta(stored.entityId(), meta.getMessageCount(), nextLastMessage, nextLastEntity);
            }
        }

        int lastIndex = team.getMessageCount(stored.entityId()) - 1;
        if (stored.messageIndex() == lastIndex) {
            database.updateConversationLastMessage(team.getId(), stored.entityId(), stored.messageIndex(), updated);
        }
        if (!message.isPlayerMessage()) {
            database.updateLastEntityMessageIfMatch(team.getId(), stored.entityId(), updated);
        }

        return true;
    }

    public void clearConversation(TeamData team, String entityId) {
        database.clearConversation(team.getId(), entityId);
        team.clearConversation(entityId);
    }

    public void clearAllConversations(TeamData team) {
        database.clearAllConversations(team.getId());
        team.clearAll();
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
