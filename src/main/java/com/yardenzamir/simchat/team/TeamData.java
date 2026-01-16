package com.yardenzamir.simchat.team;

import java.util.*;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.GsonHelper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;

import com.yardenzamir.simchat.data.ChatAction;
import com.yardenzamir.simchat.data.ChatMessage;
import com.yardenzamir.simchat.data.MessageType;

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
    private final Map<String, Object> data = new HashMap<>();
    private final transient Set<String> typingEntities = new HashSet<>();
    private int revision = 0;

    public static final String[] COLOR_NAMES = {
            "black", "dark_blue", "dark_green", "dark_aqua",
            "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua",
            "red", "light_purple", "yellow", "white"
    };

    public static final int[] SOFT_COLOR_VALUES = {
            0xFF2B2B2B, // black
            0xFF3B4D8F, // dark_blue
            0xFF3E7A5A, // dark_green
            0xFF3A6E7A, // dark_aqua
            0xFF8A4A4A, // dark_red
            0xFF7A4A8A, // dark_purple
            0xFFB48A55, // gold
            0xFF9A9A9A, // gray
            0xFF5A5A5A, // dark_gray
            0xFF6B7DD9, // blue
            0xFF7BCB8B, // green
            0xFF7BCACD, // aqua
            0xFFE07A7A, // red
            0xFFC590E0, // light_purple
            0xFFE6D37A, // yellow
            0xFFF2F2F2  // white
    };

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

    public String getColorName() {
        return getColorName(color);
    }

    public int getColorValue() {
        return getColorValue(color);
    }

    public static String getColorName(int colorIndex) {
        int index = Math.max(0, Math.min(15, colorIndex));
        return COLOR_NAMES[index];
    }

    public static int getColorValue(int colorIndex) {
        int index = Math.max(0, Math.min(15, colorIndex));
        return SOFT_COLOR_VALUES[index];
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

    public void setColor(int color) {
        this.color = Math.max(0, Math.min(15, color));
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
        String entityId = message.entityId();
        // Remove and re-add to update insertion order in LinkedHashMap (most recent last)
        List<ChatMessage> msgs = conversations.remove(entityId);
        if (msgs == null) {
            msgs = new ArrayList<>();
        }
        msgs.add(message);
        conversations.put(entityId, msgs);
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
     * Gets entity IDs ordered by most recent message (reverse insertion order).
     */
    public List<String> getEntityIds() {
        List<String> result = new ArrayList<>(conversations.keySet());
        Collections.reverse(result);
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
     * Gets display name template for entity from most recent non-player message.
     */
    public @Nullable String getEntityDisplayNameTemplate(String entityId) {
        List<ChatMessage> messages = conversations.get(entityId);
        if (messages != null) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                ChatMessage msg = messages.get(i);
                if (!msg.isPlayerMessage()) {
                    return msg.senderNameTemplate();
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

    /**
     * Sets typing state for an entity.
     * Note: Does NOT increment revision since typing is transient UI state
     * and revision is used for data sync detection.
     */
    public void setTyping(String entityId, boolean typing) {
        if (typing) {
            typingEntities.add(entityId);
            conversations.computeIfAbsent(entityId, k -> new ArrayList<>());
        } else {
            typingEntities.remove(entityId);
        }
    }

    public boolean isTyping(String entityId) {
        return typingEntities.contains(entityId);
    }

    // === Data ===

    /**
     * Sets a data value (string or number).
     */
    public void setData(String key, Object value) {
        data.put(key, value);
        revision++;
    }

    /**
     * Gets a data value, or null if not set.
     */
    @Nullable
    public Object getData(String key) {
        return data.get(key);
    }

    /**
     * Gets a data value as string.
     */
    public String getDataString(String key, String defaultValue) {
        Object val = data.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    /**
     * Gets a data value as number.
     */
    public double getDataNumber(String key, double defaultValue) {
        Object val = data.get(key);
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }

    /**
     * Gets a data value as int.
     */
    public int getDataInt(String key, int defaultValue) {
        return (int) getDataNumber(key, defaultValue);
    }

    /**
     * Adds to a numeric data value (creates if doesn't exist).
     */
    public void addData(String key, double amount) {
        double current = getDataNumber(key, 0);
        data.put(key, current + amount);
        revision++;
    }

    /**
     * Checks if data key exists and is truthy (non-null, non-zero, non-empty).
     */
    public boolean hasData(String key) {
        Object val = data.get(key);
        if (val == null) return false;
        if (val instanceof Boolean b) return b;
        if (val instanceof Number n) return n.doubleValue() != 0;
        if (val instanceof String s) return !s.isEmpty();
        return true;
    }

    /**
     * Removes a data key.
     */
    public void removeData(String key) {
        if (data.remove(key) != null) {
            revision++;
        }
    }

    /**
     * Gets all data keys.
     */
    public Set<String> getDataKeys() {
        return Collections.unmodifiableSet(data.keySet());
    }

    /**
     * Gets all data as unmodifiable map.
     */
    public Map<String, Object> getAllData() {
        return Collections.unmodifiableMap(data);
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

        JsonObject dataObject = new JsonObject();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            Object val = entry.getValue();
            if (val instanceof Number n) {
                dataObject.addProperty(entry.getKey(), n);
            } else {
                dataObject.addProperty(entry.getKey(), val.toString());
            }
        }
        json.add("data", dataObject);

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

        if (json.has("data")) {
            JsonObject dataObject = GsonHelper.getAsJsonObject(json, "data");
            for (Map.Entry<String, JsonElement> entry : dataObject.entrySet()) {
                JsonElement val = entry.getValue();
                if (val.isJsonPrimitive() && val.getAsJsonPrimitive().isNumber()) {
                    team.data.put(entry.getKey(), val.getAsDouble());
                } else {
                    team.data.put(entry.getKey(), val.getAsString());
                }
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

        CompoundTag dataTag = new CompoundTag();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            Object val = entry.getValue();
            if (val instanceof Number n) {
                dataTag.putDouble(entry.getKey(), n.doubleValue());
            } else {
                dataTag.putString(entry.getKey(), val.toString());
            }
        }
        tag.put("data", dataTag);

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

        if (tag.contains("data")) {
            CompoundTag dataTag = tag.getCompound("data");
            for (String key : dataTag.getAllKeys()) {
                byte type = dataTag.getTagType(key);
                if (type == Tag.TAG_DOUBLE || type == Tag.TAG_FLOAT || type == Tag.TAG_INT || type == Tag.TAG_LONG) {
                    team.data.put(key, dataTag.getDouble(key));
                } else {
                    team.data.put(key, dataTag.getString(key));
                }
            }
        }

        return team;
    }

    // === Helper methods for JSON message serialization ===

    private static JsonObject messageToJson(ChatMessage msg) {
        JsonObject json = new JsonObject();
        json.addProperty("type", msg.type().name());
        json.addProperty("messageId", msg.messageId().toString());
        json.addProperty("entityId", msg.entityId());
        json.addProperty("senderName", msg.senderName());
        if (msg.senderNameTemplate() != null) {
            json.addProperty("senderNameTemplate", msg.senderNameTemplate());
        }
        if (msg.senderSubtitle() != null) {
            json.addProperty("senderSubtitle", msg.senderSubtitle());
        }
        if (msg.senderSubtitleTemplate() != null) {
            json.addProperty("senderSubtitleTemplate", msg.senderSubtitleTemplate());
        }
        if (msg.senderImageId() != null) {
            json.addProperty("senderImage", msg.senderImageId());
        }
        json.addProperty("content", msg.content());
        if (msg.contentTemplate() != null) {
            json.addProperty("contentTemplate", msg.contentTemplate());
        }
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
        String typeName = GsonHelper.getAsString(json, "type", "ENTITY");
        MessageType type;
        try {
            type = MessageType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            type = MessageType.ENTITY;
        }
        tag.putInt("type", type.ordinal());
        if (json.has("messageId")) {
            tag.putUUID("messageId", UUID.fromString(GsonHelper.getAsString(json, "messageId")));
        }
        tag.putString("entityId", GsonHelper.getAsString(json, "entityId", ""));
        tag.putString("senderName", GsonHelper.getAsString(json, "senderName", ""));
        if (json.has("senderNameTemplate")) {
            tag.putString("senderNameTemplate", GsonHelper.getAsString(json, "senderNameTemplate"));
        }
        if (json.has("senderSubtitle")) {
            tag.putString("senderSubtitle", GsonHelper.getAsString(json, "senderSubtitle"));
        }
        if (json.has("senderSubtitleTemplate")) {
            tag.putString("senderSubtitleTemplate", GsonHelper.getAsString(json, "senderSubtitleTemplate"));
        }
        if (json.has("senderImage")) {
            tag.putString("senderImage", GsonHelper.getAsString(json, "senderImage"));
        }
        tag.putString("content", GsonHelper.getAsString(json, "content", ""));
        if (json.has("contentTemplate")) {
            tag.putString("contentTemplate", GsonHelper.getAsString(json, "contentTemplate"));
        }
        tag.putLong("worldDay", GsonHelper.getAsLong(json, "worldDay", 0));
        if (json.has("playerUuid")) {
            tag.putUUID("playerUuid", UUID.fromString(GsonHelper.getAsString(json, "playerUuid")));
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
        if (action.labelTemplate() != null) {
            json.addProperty("labelTemplate", action.labelTemplate());
        }
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
        if (action.nextState() != null) {
            json.addProperty("nextState", action.nextState());
        }
        if (action.condition() != null) {
            json.addProperty("condition", action.condition());
        }
        return json;
    }

    private static CompoundTag actionFromJson(JsonObject json) {
        CompoundTag tag = new CompoundTag();
        tag.putString("label", GsonHelper.getAsString(json, "label", ""));
        if (json.has("labelTemplate")) {
            tag.putString("labelTemplate", GsonHelper.getAsString(json, "labelTemplate"));
        }
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
        if (json.has("nextState")) {
            tag.putString("nextState", GsonHelper.getAsString(json, "nextState"));
        }
        if (json.has("condition")) {
            tag.putString("condition", GsonHelper.getAsString(json, "condition"));
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
