package com.yardenzamir.simchat.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import org.jetbrains.annotations.Nullable;

import com.yardenzamir.simchat.SimChatMod;
import com.yardenzamir.simchat.data.ChatMessage;
import com.yardenzamir.simchat.team.TeamData;

public class SimChatDatabase {

    private static final Gson GSON = new Gson();

    private final Path databasePath;
    private @Nullable Connection connection;

    public SimChatDatabase(MinecraftServer server) {
        this.databasePath = server.getWorldPath(LevelResource.ROOT).resolve("data/simchat/simchat.db");
    }

    public void open() {
        try {
            Files.createDirectories(databasePath.getParent());
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("PRAGMA synchronous = NORMAL");
            }
            createTables();
        } catch (SQLException | IOException e) {
            SimChatMod.LOGGER.error("Failed to open SQLite database", e);
        }
    }

    public void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException e) {
            SimChatMod.LOGGER.error("Failed to close SQLite database", e);
        } finally {
            connection = null;
        }
    }

    public boolean isOpen() {
        return connection != null;
    }

    public Map<UUID, String> loadPlayerTeams() {
        Map<UUID, String> mappings = new HashMap<>();
        if (connection == null) {
            return mappings;
        }
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT player_id, team_id FROM player_team"
        )) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                mappings.put(UUID.fromString(rs.getString("player_id")), rs.getString("team_id"));
            }
        } catch (SQLException e) {
            SimChatMod.LOGGER.error("Failed to load player-team mappings", e);
        }
        return mappings;
    }

    public void setPlayerTeam(UUID playerId, String teamId) {
        if (connection == null) {
            return;
        }
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO player_team (player_id, team_id) VALUES (?, ?) " +
                        "ON CONFLICT(player_id) DO UPDATE SET team_id = excluded.team_id"
        )) {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, teamId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            SimChatMod.LOGGER.error("Failed to update player-team mapping", e);
        }
    }

    public void removePlayerTeam(UUID playerId) {
        if (connection == null) {
            return;
        }
        try (PreparedStatement stmt = connection.prepareStatement(
                "DELETE FROM player_team WHERE player_id = ?"
        )) {
            stmt.setString(1, playerId.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            SimChatMod.LOGGER.error("Failed to remove player-team mapping", e);
        }
    }

    public void upsertTeam(TeamData team) {
        if (connection == null) {
            return;
        }
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO teams (team_id, title, color, data_json) VALUES (?, ?, ?, ?) " +
                        "ON CONFLICT(team_id) DO UPDATE SET title = excluded.title, color = excluded.color, data_json = excluded.data_json"
        )) {
            stmt.setString(1, team.getId());
            stmt.setString(2, team.getTitle());
            stmt.setInt(3, team.getColor());
            stmt.setString(4, encodeTeamData(team.getAllData()));
            stmt.executeUpdate();
        } catch (SQLException e) {
            SimChatMod.LOGGER.error("Failed to upsert team {}", team.getId(), e);
        }

        updateTeamMembers(team);
    }

    public @Nullable TeamData loadTeam(String teamId) {
        if (connection == null) {
            return null;
        }
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT title, color, data_json FROM teams WHERE team_id = ?"
        )) {
            stmt.setString(1, teamId);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                return null;
            }

            String title = rs.getString("title");
            int color = rs.getInt("color");
            String dataJson = rs.getString("data_json");

            TeamData team = new TeamData(teamId, title);
            team.setColor(color);
            decodeTeamData(dataJson, team);
            loadTeamMembers(team);
            loadConversationMetadata(team);
            return team;
        } catch (SQLException e) {
            SimChatMod.LOGGER.error("Failed to load team {}", teamId, e);
            return null;
        }
    }

    public List<String> loadTeamIds() {
        List<String> ids = new ArrayList<>();
        if (connection == null) {
            return ids;
        }
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT team_id FROM teams"
        )) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                ids.add(rs.getString("team_id"));
            }
        } catch (SQLException e) {
            SimChatMod.LOGGER.error("Failed to load team ids", e);
        }
        return ids;
    }

    public boolean teamExists(String teamId) {
        if (connection == null) {
            return false;
        }
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT team_id FROM teams WHERE team_id = ?"
        )) {
            stmt.setString(1, teamId);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            SimChatMod.LOGGER.error("Failed to check team existence for {}", teamId, e);
            return false;
        }
    }

    public int insertMessage(String teamId, ChatMessage message) {
        if (connection == null) {
            return -1;
        }
        int messageIndex = -1;
        try {
            connection.setAutoCommit(false);
            messageIndex = getNextMessageIndex(teamId, message.entityId());

            try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT INTO messages (team_id, entity_id, message_index, message_id, world_day, payload) " +
                            "VALUES (?, ?, ?, ?, ?, ?)"
            )) {
                stmt.setString(1, teamId);
                stmt.setString(2, message.entityId());
                stmt.setInt(3, messageIndex);
                stmt.setString(4, message.messageId().toString());
                stmt.setLong(5, message.worldDay());
                stmt.setString(6, encodeMessage(message));
                stmt.executeUpdate();
            }

            boolean isPlayerMessage = message.isPlayerMessage();
            String updateSql = isPlayerMessage
                    ? "UPDATE conversations SET message_count = ?, last_message_index = ?, last_message_id = ?, last_message = ? " +
                    "WHERE team_id = ? AND entity_id = ?"
                    : "UPDATE conversations SET message_count = ?, last_message_index = ?, last_message_id = ?, last_message = ?, " +
                    "last_entity_message_id = ?, last_entity_message = ? WHERE team_id = ? AND entity_id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(updateSql)) {
                stmt.setInt(1, messageIndex + 1);
                stmt.setInt(2, messageIndex);
                stmt.setString(3, message.messageId().toString());
                stmt.setString(4, encodeMessage(message));
                if (isPlayerMessage) {
                    stmt.setString(5, teamId);
                    stmt.setString(6, message.entityId());
                } else {
                    stmt.setString(5, message.messageId().toString());
                    stmt.setString(6, encodeMessage(message));
                    stmt.setString(7, teamId);
                    stmt.setString(8, message.entityId());
                }
                stmt.executeUpdate();
            }

            connection.commit();
        } catch (SQLException e) {
            SimChatMod.LOGGER.error("Failed to insert message for team {}", teamId, e);
            messageIndex = -1;
            rollback();
        } finally {
            resetAutoCommit();
        }
        return messageIndex;
    }

    public List<ChatMessage> loadMessages(String teamId, String entityId, int startIndex, int count) {
        List<ChatMessage> messages = new ArrayList<>();
        if (connection == null || count <= 0) {
            return messages;
        }
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT payload FROM messages WHERE team_id = ? AND entity_id = ? AND message_index >= ? " +
                        "ORDER BY message_index ASC LIMIT ?"
        )) {
            stmt.setString(1, teamId);
            stmt.setString(2, entityId);
            stmt.setInt(3, startIndex);
            stmt.setInt(4, count);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                ChatMessage message = decodeMessage(rs.getString("payload"));
                if (message != null) {
                    messages.add(message);
                }
            }
        } catch (SQLException e) {
            SimChatMod.LOGGER.error("Failed to load messages for team {}", teamId, e);
        }
        return messages;
    }

    public List<ChatMessage> loadOlderMessages(String teamId, String entityId, int beforeIndex, int count) {
        List<ChatMessage> messages = new ArrayList<>();
        if (connection == null || count <= 0) {
            return messages;
        }
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT payload FROM messages WHERE team_id = ? AND entity_id = ? AND message_index < ? " +
                        "ORDER BY message_index DESC LIMIT ?"
        )) {
            stmt.setString(1, teamId);
            stmt.setString(2, entityId);
            stmt.setInt(3, beforeIndex);
            stmt.setInt(4, count);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                ChatMessage message = decodeMessage(rs.getString("payload"));
                if (message != null) {
                    messages.add(message);
                }
            }
        } catch (SQLException e) {
            SimChatMod.LOGGER.error("Failed to load older messages for team {}", teamId, e);
        }
        java.util.Collections.reverse(messages);
        return messages;
    }

    public int getMessageCount(String teamId, String entityId) {
        if (connection == null) {
            return 0;
        }
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT message_count FROM conversations WHERE team_id = ? AND entity_id = ?"
        )) {
            stmt.setString(1, teamId);
            stmt.setString(2, entityId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("message_count");
            }
        } catch (SQLException e) {
            SimChatMod.LOGGER.error("Failed to get message count for team {}", teamId, e);
        }
        return 0;
    }

    public @Nullable StoredMessage loadMessageById(String teamId, UUID messageId) {
        if (connection == null) {
            return null;
        }
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT entity_id, message_index, payload FROM messages WHERE team_id = ? AND message_id = ?"
        )) {
            stmt.setString(1, teamId);
            stmt.setString(2, messageId.toString());
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                return null;
            }
            String entityId = rs.getString("entity_id");
            int messageIndex = rs.getInt("message_index");
            ChatMessage message = decodeMessage(rs.getString("payload"));
            if (message == null) {
                return null;
            }
            return new StoredMessage(entityId, messageIndex, message);
        } catch (SQLException e) {
            SimChatMod.LOGGER.error("Failed to load message {}", messageId, e);
            return null;
        }
    }

    public boolean updateMessagePayload(String teamId, String entityId, int messageIndex, ChatMessage message) {
        if (connection == null) {
            return false;
        }
        try (PreparedStatement stmt = connection.prepareStatement(
                "UPDATE messages SET payload = ? WHERE team_id = ? AND entity_id = ? AND message_index = ?"
        )) {
            stmt.setString(1, encodeMessage(message));
            stmt.setString(2, teamId);
            stmt.setString(3, entityId);
            stmt.setInt(4, messageIndex);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            SimChatMod.LOGGER.error("Failed to update message payload for team {}", teamId, e);
            return false;
        }
    }

    public void clearConversation(String teamId, String entityId) {
        if (connection == null) {
            return;
        }
        try (PreparedStatement deleteMessages = connection.prepareStatement(
                "DELETE FROM messages WHERE team_id = ? AND entity_id = ?"
        ); PreparedStatement deleteConversation = connection.prepareStatement(
                "DELETE FROM conversations WHERE team_id = ? AND entity_id = ?"
        )) {
            deleteMessages.setString(1, teamId);
            deleteMessages.setString(2, entityId);
            deleteMessages.executeUpdate();

            deleteConversation.setString(1, teamId);
            deleteConversation.setString(2, entityId);
            deleteConversation.executeUpdate();
        } catch (SQLException e) {
            SimChatMod.LOGGER.error("Failed to clear conversation for team {}", teamId, e);
        }
    }

    public void clearAllConversations(String teamId) {
        if (connection == null) {
            return;
        }
        try (PreparedStatement deleteMessages = connection.prepareStatement(
                "DELETE FROM messages WHERE team_id = ?"
        ); PreparedStatement deleteConversations = connection.prepareStatement(
                "DELETE FROM conversations WHERE team_id = ?"
        )) {
            deleteMessages.setString(1, teamId);
            deleteMessages.executeUpdate();

            deleteConversations.setString(1, teamId);
            deleteConversations.executeUpdate();
        } catch (SQLException e) {
            SimChatMod.LOGGER.error("Failed to clear conversations for team {}", teamId, e);
        }
    }

    public void updateConversationLastMessage(String teamId, String entityId, int messageIndex, ChatMessage message) {
        if (connection == null) {
            return;
        }
        String updateSql = message.isPlayerMessage()
                ? "UPDATE conversations SET last_message_index = ?, last_message_id = ?, last_message = ? " +
                "WHERE team_id = ? AND entity_id = ?"
                : "UPDATE conversations SET last_message_index = ?, last_message_id = ?, last_message = ?, " +
                "last_entity_message_id = ?, last_entity_message = ? WHERE team_id = ? AND entity_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(updateSql)) {
            stmt.setInt(1, messageIndex);
            stmt.setString(2, message.messageId().toString());
            stmt.setString(3, encodeMessage(message));
            if (message.isPlayerMessage()) {
                stmt.setString(4, teamId);
                stmt.setString(5, entityId);
            } else {
                stmt.setString(4, message.messageId().toString());
                stmt.setString(5, encodeMessage(message));
                stmt.setString(6, teamId);
                stmt.setString(7, entityId);
            }
            stmt.executeUpdate();
        } catch (SQLException e) {
            SimChatMod.LOGGER.error("Failed to update conversation last message for team {}", teamId, e);
        }
    }

    public void updateLastEntityMessageIfMatch(String teamId, String entityId, ChatMessage message) {
        if (connection == null) {
            return;
        }
        try (PreparedStatement stmt = connection.prepareStatement(
                "UPDATE conversations SET last_entity_message = ? WHERE team_id = ? AND entity_id = ? AND last_entity_message_id = ?"
        )) {
            stmt.setString(1, encodeMessage(message));
            stmt.setString(2, teamId);
            stmt.setString(3, entityId);
            stmt.setString(4, message.messageId().toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            SimChatMod.LOGGER.error("Failed to update last entity message for team {}", teamId, e);
        }
    }

    private void updateTeamMembers(TeamData team) {
        if (connection == null) {
            return;
        }
        try (PreparedStatement deleteStmt = connection.prepareStatement(
                "DELETE FROM team_members WHERE team_id = ?"
        )) {
            deleteStmt.setString(1, team.getId());
            deleteStmt.executeUpdate();
        } catch (SQLException e) {
            SimChatMod.LOGGER.error("Failed to clear team members for {}", team.getId(), e);
        }

        try (PreparedStatement insertStmt = connection.prepareStatement(
                "INSERT INTO team_members (team_id, member_id) VALUES (?, ?)"
        )) {
            for (UUID member : team.getMembers()) {
                insertStmt.setString(1, team.getId());
                insertStmt.setString(2, member.toString());
                insertStmt.addBatch();
            }
            insertStmt.executeBatch();
        } catch (SQLException e) {
            SimChatMod.LOGGER.error("Failed to insert team members for {}", team.getId(), e);
        }
    }

    private void loadTeamMembers(TeamData team) {
        if (connection == null) {
            return;
        }
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT member_id FROM team_members WHERE team_id = ?"
        )) {
            stmt.setString(1, team.getId());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                team.addMember(UUID.fromString(rs.getString("member_id")));
            }
        } catch (SQLException e) {
            SimChatMod.LOGGER.error("Failed to load team members for {}", team.getId(), e);
        }
    }

    private void loadConversationMetadata(TeamData team) {
        if (connection == null) {
            return;
        }
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT entity_id, message_count, last_message, last_entity_message FROM conversations WHERE team_id = ? " +
                        "ORDER BY (last_message_index IS NULL), last_message_index ASC"
        )) {
            stmt.setString(1, team.getId());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String entityId = rs.getString("entity_id");
                int messageCount = rs.getInt("message_count");
                ChatMessage lastMessage = decodeMessage(rs.getString("last_message"));
                ChatMessage lastEntityMessage = decodeMessage(rs.getString("last_entity_message"));
                team.setConversationMeta(entityId, messageCount, lastMessage, lastEntityMessage);
            }
        } catch (SQLException e) {
            SimChatMod.LOGGER.error("Failed to load conversation metadata for {}", team.getId(), e);
        }
    }

    private int getNextMessageIndex(String teamId, String entityId) throws SQLException {
        int messageCount = 0;
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT message_count FROM conversations WHERE team_id = ? AND entity_id = ?"
        )) {
            stmt.setString(1, teamId);
            stmt.setString(2, entityId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                messageCount = rs.getInt("message_count");
            } else {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO conversations (team_id, entity_id, message_count, last_message_index) VALUES (?, ?, 0, NULL)"
                )) {
                    insert.setString(1, teamId);
                    insert.setString(2, entityId);
                    insert.executeUpdate();
                }
            }
        }
        return messageCount;
    }

    private static String encodeTeamData(Map<String, Object> data) {
        JsonObject json = new JsonObject();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Number number) {
                json.addProperty(entry.getKey(), number);
            } else if (value instanceof Boolean bool) {
                json.addProperty(entry.getKey(), bool);
            } else if (value != null) {
                json.addProperty(entry.getKey(), value.toString());
            }
        }
        return GSON.toJson(json);
    }

    private static void decodeTeamData(@Nullable String json, TeamData team) {
        if (json == null || json.isBlank()) {
            return;
        }
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            JsonElement val = entry.getValue();
            if (val.isJsonPrimitive()) {
                if (val.getAsJsonPrimitive().isBoolean()) {
                    team.setData(entry.getKey(), val.getAsBoolean());
                } else if (val.getAsJsonPrimitive().isNumber()) {
                    team.setData(entry.getKey(), val.getAsDouble());
                } else {
                    team.setData(entry.getKey(), val.getAsString());
                }
            }
        }
    }

    private static String encodeMessage(ChatMessage message) {
        return GSON.toJson(message.toJson());
    }

    private static @Nullable ChatMessage decodeMessage(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            return ChatMessage.fromJson(obj);
        } catch (JsonSyntaxException e) {
            SimChatMod.LOGGER.error("Failed to deserialize message: {}", e.getMessage());
            return null;
        }
    }

    private void createTables() throws SQLException {
        if (connection == null) {
            return;
        }
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS teams (" +
                    "team_id TEXT PRIMARY KEY, " +
                    "title TEXT NOT NULL, " +
                    "color INTEGER NOT NULL, " +
                    "data_json TEXT NOT NULL DEFAULT '{}'" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS team_members (" +
                    "team_id TEXT NOT NULL, " +
                    "member_id TEXT NOT NULL, " +
                    "PRIMARY KEY (team_id, member_id)" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS player_team (" +
                    "player_id TEXT PRIMARY KEY, " +
                    "team_id TEXT NOT NULL" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS conversations (" +
                    "team_id TEXT NOT NULL, " +
                    "entity_id TEXT NOT NULL, " +
                    "message_count INTEGER NOT NULL, " +
                    "last_message_index INTEGER, " +
                    "last_message_id TEXT, " +
                    "last_message TEXT, " +
                    "last_entity_message_id TEXT, " +
                    "last_entity_message TEXT, " +
                    "PRIMARY KEY (team_id, entity_id)" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS messages (" +
                    "team_id TEXT NOT NULL, " +
                    "entity_id TEXT NOT NULL, " +
                    "message_index INTEGER NOT NULL, " +
                    "message_id TEXT NOT NULL, " +
                    "world_day INTEGER NOT NULL, " +
                    "payload TEXT NOT NULL, " +
                    "PRIMARY KEY (team_id, entity_id, message_index)" +
                    ")");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_messages_message_id ON messages(message_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_messages_team_entity ON messages(team_id, entity_id, message_index)");
        }
    }

    private void rollback() {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException e) {
            SimChatMod.LOGGER.error("Failed to rollback database transaction", e);
        }
    }

    private void resetAutoCommit() {
        if (connection == null) {
            return;
        }
        try {
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            SimChatMod.LOGGER.error("Failed to reset auto-commit", e);
        }
    }

    public record StoredMessage(String entityId, int messageIndex, ChatMessage message) {}
}
