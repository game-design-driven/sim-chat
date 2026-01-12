package com.yardenzamir.simchat.team;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yardenzamir.simchat.data.ChatAction;
import com.yardenzamir.simchat.data.ChatMessage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Stores all shared data for a team: conversations, flags, and membership.
 * Persisted to JSON files in world/data/simchat/teams/.
 */
public class TeamData {

    private final String id;
    private String title;
    private int color;
    private final Set<UUID> members = new HashSet<>();
    private final Map<String, List<ChatMessage>> conversations = new LinkedHashMap<>();
    private final Map<String, String> flags = new HashMap<>();
    private final transient Set<String> typingEntities = new HashSet<>();
    private int revision = 0;

    public TeamData(String id, String title) {
        this.id = id;
        this.title = title;
        this.color = generateColorFromTitle(title);
    }

    /**
     * Generates a 7-character team ID from random UUID.
     */
    public static String generateId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 7).toLowerCase();
    }

    /**
     * Generates a color index (0-15) from title hash.
     */
    private static int generateColorFromTitle(String title) {
        return Math.abs(title.hashCode()) % 16;
    }

    // === Getters ===

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public int getColor() {
        return color;
    }

    public int getRevision() {
        return revision;
    }

    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    // === Title ===

    public void setTitle(String title) {
        this.title = title;
        this.color = generateColorFromTitle(title);
        revision++;
    }

    // === Membership ===

    public void addMember(UUID playerId) {
        if (members.add(playerId)) {
            revision++;
        }
    }

    public void removeMember(UUID playerId) {
        if (members.remove(playerId)) {
            revision++;
        }
    }

    public boolean isMember(UUID playerId) {
        return members.contains(playerId);
    }

    public int getMemberCount() {
        return members.size();
    }

    // === Conversations ===

    public void addMessage(ChatMessage message) {
        conversations.computeIfAbsent(message.entityId(), k -> new ArrayList<>()).add(message);
        revision++;
    }

    public List<ChatMessage> getMessages(String entityId) {
        return conversations.getOrDefault(entityId, Collections.emptyList());
    }

    public int getMessageCount(String entityId) {
        List<ChatMessage> msgs = conversations.get(entityId);
        return msgs != null ? msgs.size() : 0;
    }

    /**
     * Gets entity IDs ordered by most recent message.
     */
    public List<String> getEntityIds() {
        Map<String, List<ChatMessage>> snapshot = new LinkedHashMap<>(conversations);
        List<Map.Entry<String, List<ChatMessage>>> entries = new ArrayList<>(snapshot.entrySet());

        entries.sort((a, b) -> {
            long timeA = a.getValue().isEmpty() ? 0 : a.getValue().get(a.getValue().size() - 1).timestamp();
            long timeB = b.getValue().isEmpty() ? 0 : b.getValue().get(b.getValue().size() - 1).timestamp();
            return Long.compare(timeB, timeA);
        });

        List<String> result = new ArrayList<>();
        for (Map.Entry<String, List<ChatMessage>> entry : entries) {
            result.add(entry.getKey());
        }
        return result;
    }

    /**
     * Gets display name for entity from most recent non-player message.
     */
    public @Nullable String getEntityDisplayName(String entityId) {
        List<ChatMessage> messages = conversations.get(entityId);
        if (messages != null) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                ChatMessage msg = messages.get(i);
                if (!msg.isPlayerMessage()) {
                    return msg.senderName();
                }
            }
        }
        return null;
    }

    /**
     * Gets image ID for entity from most recent non-player message.
     */
    public @Nullable String getEntityImageId(String entityId) {
        List<ChatMessage> messages = conversations.get(entityId);
        if (messages != null) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                ChatMessage msg = messages.get(i);
                if (!msg.isPlayerMessage() && msg.senderImageId() != null) {
                    return msg.senderImageId();
                }
            }
        }
        return null;
    }

    /**
     * Gets subtitle for entity from most recent non-player message.
     */
    public @Nullable String getEntitySubtitle(String entityId) {
        List<ChatMessage> messages = conversations.get(entityId);
        if (messages != null) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                ChatMessage msg = messages.get(i);
                if (!msg.isPlayerMessage() && msg.senderSubtitle() != null) {
                    return msg.senderSubtitle();
                }
            }
        }
        return null;
    }

    public @Nullable ChatMessage getLastMessage(String entityId) {
        List<ChatMessage> messages = conversations.get(entityId);
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        return messages.get(messages.size() - 1);
    }

    public boolean consumeActions(String entityId, int messageIndex) {
        List<ChatMessage> messages = conversations.get(entityId);
        if (messages == null || messageIndex < 0 || messageIndex >= messages.size()) {
            return false;
        }
        ChatMessage original = messages.get(messageIndex);
        if (original.actions().isEmpty()) {
            return false;
        }
        messages.set(messageIndex, original.withoutActions());
        revision++;
        return true;
    }

    public void clearConversation(String entityId) {
        if (conversations.remove(entityId) != null) {
            revision++;
        }
    }

    public void clearAll() {
        conversations.clear();
        revision++;
    }

    public boolean hasConversations() {
        return !conversations.isEmpty();
    }

    // === Typing ===

    public void setTyping(String entityId, boolean typing) {
        if (typing) {
            if (typingEntities.add(entityId)) {
                conversations.computeIfAbsent(entityId, k -> new ArrayList<>());
                revision++;
            }
        } else {
            if (typingEntities.remove(entityId)) {
                revision++;
            }
        }
    }

    public boolean isTyping(String entityId) {
        return typingEntities.contains(entityId);
    }

    // === Flags ===

    public void setFlag(String key, String value) {
        flags.put(key, value);
        revision++;
    }

    public @Nullable String getFlag(String key) {
        return flags.get(key);
    }

    public void removeFlag(String key) {
        if (flags.remove(key) != null) {
            revision++;
        }
    }

    public Map<String, String> getFlags() {
        return Collections.unmodifiableMap(flags);
    }

    // === JSON Serialization ===

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("title", title);
        json.addProperty("color", color);

        JsonArray membersArray = new JsonArray();
        for (UUID member : members) {
            membersArray.add(member.toString());
        }
        json.add("members", membersArray);

        JsonObject convsObject = new JsonObject();
        for (Map.Entry<String, List<ChatMessage>> entry : conversations.entrySet()) {
            JsonArray messagesArray = new JsonArray();
            for (ChatMessage msg : entry.getValue()) {
                messagesArray.add(messageToJson(msg));
            }
            convsObject.add(entry.getKey(), messagesArray);
        }
        json.add("conversations", convsObject);

        JsonObject flagsObject = new JsonObject();
        for (Map.Entry<String, String> entry : flags.entrySet()) {
            flagsObject.addProperty(entry.getKey(), entry.getValue());
        }
        json.add("flags", flagsObject);

        return json;
    }

    public static TeamData fromJson(JsonObject json) {
        String id = GsonHelper.getAsString(json, "id");
        String title = GsonHelper.getAsString(json, "title");
        TeamData team = new TeamData(id, title);
        team.color = GsonHelper.getAsInt(json, "color", team.color);

        if (json.has("members")) {
            JsonArray membersArray = GsonHelper.getAsJsonArray(json, "members");
            for (JsonElement elem : membersArray) {
                team.members.add(UUID.fromString(elem.getAsString()));
            }
        }

        if (json.has("conversations")) {
            JsonObject convsObject = GsonHelper.getAsJsonObject(json, "conversations");
            for (Map.Entry<String, JsonElement> entry : convsObject.entrySet()) {
                List<ChatMessage> messages = new ArrayList<>();
                JsonArray messagesArray = entry.getValue().getAsJsonArray();
                for (JsonElement elem : messagesArray) {
                    messages.add(messageFromJson(elem.getAsJsonObject()));
                }
                team.conversations.put(entry.getKey(), messages);
            }
        }

        if (json.has("flags")) {
            JsonObject flagsObject = GsonHelper.getAsJsonObject(json, "flags");
            for (Map.Entry<String, JsonElement> entry : flagsObject.entrySet()) {
                team.flags.put(entry.getKey(), entry.getValue().getAsString());
            }
        }

        return team;
    }

    // === NBT for network packets ===

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("title", title);
        tag.putInt("color", color);
        tag.putInt("revision", revision);

        ListTag convList = new ListTag();
        for (Map.Entry<String, List<ChatMessage>> entry : conversations.entrySet()) {
            CompoundTag convTag = new CompoundTag();
            convTag.putString("entityId", entry.getKey());
            ListTag messageList = new ListTag();
            for (ChatMessage msg : entry.getValue()) {
                messageList.add(msg.toNbt());
            }
            convTag.put("messages", messageList);
            convList.add(convTag);
        }
        tag.put("conversations", convList);

        CompoundTag flagsTag = new CompoundTag();
        for (Map.Entry<String, String> entry : flags.entrySet()) {
            flagsTag.putString(entry.getKey(), entry.getValue());
        }
        tag.put("flags", flagsTag);

        return tag;
    }

    public static TeamData fromNbt(CompoundTag tag) {
        String id = tag.getString("id");
        String title = tag.getString("title");
        TeamData team = new TeamData(id, title);
        team.color = tag.getInt("color");
        team.revision = tag.getInt("revision");

        if (tag.contains("conversations")) {
            ListTag convList = tag.getList("conversations", Tag.TAG_COMPOUND);
            for (int i = 0; i < convList.size(); i++) {
                CompoundTag convTag = convList.getCompound(i);
                String entityId = convTag.getString("entityId");
                ListTag messageList = convTag.getList("messages", Tag.TAG_COMPOUND);
                List<ChatMessage> messages = new ArrayList<>();
                for (int j = 0; j < messageList.size(); j++) {
                    messages.add(ChatMessage.fromNbt(messageList.getCompound(j)));
                }
                team.conversations.put(entityId, messages);
            }
        }

        if (tag.contains("flags")) {
            CompoundTag flagsTag = tag.getCompound("flags");
            for (String key : flagsTag.getAllKeys()) {
                team.flags.put(key, flagsTag.getString(key));
            }
        }

        return team;
    }

    // === Helper methods for JSON message serialization ===

    private static JsonObject messageToJson(ChatMessage msg) {
        JsonObject json = new JsonObject();
        json.addProperty("isPlayer", msg.isPlayerMessage());
        json.addProperty("isSystem", msg.isSystemMessage());
        json.addProperty("entityId", msg.entityId());
        json.addProperty("senderName", msg.senderName());
        if (msg.senderSubtitle() != null) {
            json.addProperty("senderSubtitle", msg.senderSubtitle());
        }
        if (msg.senderImageId() != null) {
            json.addProperty("senderImage", msg.senderImageId());
        }
        json.addProperty("content", msg.content());
        json.addProperty("worldDay", msg.worldDay());
        if (msg.playerUuid() != null) {
            json.addProperty("playerUuid", msg.playerUuid().toString());
        }

        if (!msg.actions().isEmpty()) {
            JsonArray actionsArray = new JsonArray();
            for (ChatAction action : msg.actions()) {
                actionsArray.add(actionToJson(action));
            }
            json.add("actions", actionsArray);
        }

        if (!msg.transactionInput().isEmpty()) {
            json.add("transactionInput", actionItemsToJson(msg.transactionInput()));
        }
        if (!msg.transactionOutput().isEmpty()) {
            json.add("transactionOutput", actionItemsToJson(msg.transactionOutput()));
        }

        return json;
    }

    private static ChatMessage messageFromJson(JsonObject json) {
        // Delegate to ChatMessage's NBT since structure is similar
        // Convert JSON to NBT-like structure
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("isPlayer", GsonHelper.getAsBoolean(json, "isPlayer", false));
        tag.putBoolean("isSystem", GsonHelper.getAsBoolean(json, "isSystem", false));
        tag.putString("entityId", GsonHelper.getAsString(json, "entityId", ""));
        tag.putString("senderName", GsonHelper.getAsString(json, "senderName", ""));
        if (json.has("senderSubtitle")) {
            tag.putString("senderSubtitle", GsonHelper.getAsString(json, "senderSubtitle"));
        }
        if (json.has("senderImage")) {
            tag.putString("senderImage", GsonHelper.getAsString(json, "senderImage"));
        }
        tag.putString("content", GsonHelper.getAsString(json, "content", ""));
        tag.putLong("worldDay", GsonHelper.getAsLong(json, "worldDay", 0));
        if (json.has("playerUuid")) {
            tag.putString("playerUuid", GsonHelper.getAsString(json, "playerUuid"));
        }

        if (json.has("actions")) {
            ListTag actionList = new ListTag();
            JsonArray actionsArray = GsonHelper.getAsJsonArray(json, "actions");
            for (JsonElement elem : actionsArray) {
                actionList.add(actionFromJson(elem.getAsJsonObject()));
            }
            tag.put("actions", actionList);
        }

        if (json.has("transactionInput")) {
            tag.put("transactionInput", actionItemsFromJson(GsonHelper.getAsJsonArray(json, "transactionInput")));
        }
        if (json.has("transactionOutput")) {
            tag.put("transactionOutput", actionItemsFromJson(GsonHelper.getAsJsonArray(json, "transactionOutput")));
        }

        return ChatMessage.fromNbt(tag);
    }

    private static JsonObject actionToJson(ChatAction action) {
        JsonObject json = new JsonObject();
        json.addProperty("label", action.label());
        JsonArray cmds = new JsonArray();
        for (String cmd : action.commands()) {
            cmds.add(cmd);
        }
        json.add("commands", cmds);
        if (action.replyText() != null) {
            json.addProperty("reply", action.replyText());
        }
        if (!action.itemsVisual().isEmpty()) {
            json.add("itemsVisual", actionItemsToJson(action.itemsVisual()));
        }
        if (!action.itemsInput().isEmpty()) {
            json.add("itemsInput", actionItemsToJson(action.itemsInput()));
        }
        if (!action.itemsOutput().isEmpty()) {
            json.add("itemsOutput", actionItemsToJson(action.itemsOutput()));
        }
        return json;
    }

    private static CompoundTag actionFromJson(JsonObject json) {
        CompoundTag tag = new CompoundTag();
        tag.putString("label", GsonHelper.getAsString(json, "label", ""));
        ListTag cmds = new ListTag();
        if (json.has("commands")) {
            for (JsonElement elem : GsonHelper.getAsJsonArray(json, "commands")) {
                cmds.add(net.minecraft.nbt.StringTag.valueOf(elem.getAsString()));
            }
        }
        tag.put("commands", cmds);
        if (json.has("reply")) {
            tag.putString("reply", GsonHelper.getAsString(json, "reply"));
        }
        if (json.has("itemsVisual")) {
            tag.put("itemsVisual", actionItemsFromJson(GsonHelper.getAsJsonArray(json, "itemsVisual")));
        }
        if (json.has("itemsInput")) {
            tag.put("itemsInput", actionItemsFromJson(GsonHelper.getAsJsonArray(json, "itemsInput")));
        }
        if (json.has("itemsOutput")) {
            tag.put("itemsOutput", actionItemsFromJson(GsonHelper.getAsJsonArray(json, "itemsOutput")));
        }
        return tag;
    }

    private static JsonArray actionItemsToJson(List<ChatAction.ActionItem> items) {
        JsonArray arr = new JsonArray();
        for (ChatAction.ActionItem item : items) {
            JsonObject obj = new JsonObject();
            obj.addProperty("item", item.item());
            obj.addProperty("count", item.count());
            arr.add(obj);
        }
        return arr;
    }

    private static ListTag actionItemsFromJson(JsonArray arr) {
        ListTag list = new ListTag();
        for (JsonElement elem : arr) {
            JsonObject obj = elem.getAsJsonObject();
            CompoundTag tag = new CompoundTag();
            tag.putString("item", GsonHelper.getAsString(obj, "item", ""));
            tag.putInt("count", GsonHelper.getAsInt(obj, "count", 1));
            list.add(tag);
        }
        return list;
    }
}
