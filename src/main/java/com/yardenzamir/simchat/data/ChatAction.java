package com.yardenzamir.simchat.data;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a clickable action button on a chat message.
 *
 * @param label        Button text shown to player
 * @param labelTemplate Runtime template for label (if present)
 * @param commands     Commands to execute when clicked (in order)
 * @param replyText    If present, shows as player message before executing commands
 * @param itemsVisual  Items displayed on button (visual only, no functionality)
 * @param itemsInput   Items required from player inventory (consumed on click, blue background)
 * @param itemsOutput  Items given to player on click (orange background)
 * @param nextState    If present, dialogue resource location to send after this action (e.g., "mypack:npc/next")
 * @param condition    If present, condition that must pass for action to be visible (e.g., "kjs:hasHighRep", "!flag:seen_intro")
 * @param playerInput  If present, transforms button into text input field
 */
public record ChatAction(
        String label,
        @Nullable String labelTemplate,
        List<String> commands,
        @Nullable String replyText,
        List<ActionItem> itemsVisual,
        List<ActionItem> itemsInput,
        List<ActionItem> itemsOutput,
        @Nullable String nextState,
        @Nullable String condition,
        @Nullable PlayerInputConfig playerInput
) {

    /**
     * Configuration for player text input action.
     *
     * @param id         Variable name, accessible via {input:id} in reply/commands
     * @param maxLength  Maximum input length (1-256, default 64)
     * @param pattern    Regex pattern for validation (null = any non-empty string)
     * @param error      Tooltip shown when input doesn't match pattern
     * @param saveAsData If true, saves input to team data as data:<id>
     */
    public record PlayerInputConfig(
            String id,
            int maxLength,
            @Nullable String pattern,
            @Nullable String error,
            boolean saveAsData
    ) {
        private static final String TAG_ID = "id";
        private static final String TAG_MAX_LENGTH = "maxLength";
        private static final String TAG_PATTERN = "pattern";
        private static final String TAG_ERROR = "error";
        private static final String TAG_SAVE_AS_DATA = "saveAsData";

        public static final int DEFAULT_MAX_LENGTH = 64;

        public PlayerInputConfig(String id) {
            this(id, DEFAULT_MAX_LENGTH, null, null, false);
        }

        public CompoundTag toNbt() {
            CompoundTag tag = new CompoundTag();
            tag.putString(TAG_ID, id);
            tag.putInt(TAG_MAX_LENGTH, maxLength);
            if (pattern != null) {
                tag.putString(TAG_PATTERN, pattern);
            }
            if (error != null) {
                tag.putString(TAG_ERROR, error);
            }
            tag.putBoolean(TAG_SAVE_AS_DATA, saveAsData);
            return tag;
        }

        public static PlayerInputConfig fromNbt(CompoundTag tag) {
            return new PlayerInputConfig(
                    tag.getString(TAG_ID),
                    tag.contains(TAG_MAX_LENGTH) ? tag.getInt(TAG_MAX_LENGTH) : DEFAULT_MAX_LENGTH,
                    tag.contains(TAG_PATTERN) ? tag.getString(TAG_PATTERN) : null,
                    tag.contains(TAG_ERROR) ? tag.getString(TAG_ERROR) : null,
                    tag.getBoolean(TAG_SAVE_AS_DATA)
            );
        }

        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("id", id);
            json.addProperty("maxLength", maxLength);
            if (pattern != null) json.addProperty("pattern", pattern);
            if (error != null) json.addProperty("error", error);
            json.addProperty("saveAsData", saveAsData);
            return json;
        }

        public static PlayerInputConfig fromJson(JsonObject json) {
            return new PlayerInputConfig(
                    GsonHelper.getAsString(json, "id"),
                    GsonHelper.getAsInt(json, "maxLength", DEFAULT_MAX_LENGTH),
                    json.has("pattern") ? GsonHelper.getAsString(json, "pattern") : null,
                    json.has("error") ? GsonHelper.getAsString(json, "error") : null,
                    GsonHelper.getAsBoolean(json, "saveAsData", false)
            );
        }
    }

    private static final String TAG_LABEL = "label";
    private static final String TAG_LABEL_TEMPLATE = "labelTemplate";
    private static final String TAG_COMMANDS = "commands";
    private static final String TAG_REPLY = "reply";
    private static final String TAG_ITEMS_VISUAL = "itemsVisual";
    private static final String TAG_ITEMS_INPUT = "itemsInput";
    private static final String TAG_ITEMS_OUTPUT = "itemsOutput";
    private static final String TAG_NEXT_STATE = "nextState";
    private static final String TAG_CONDITION = "condition";
    private static final String TAG_PLAYER_INPUT = "playerInput";
    private static final String TAG_ITEM = "item";
    private static final String TAG_ITEM_COUNT = "count";

    /**
     * Item to display on an action button.
     * @param item Full item string with optional NBT (e.g., "minecraft:diamond{display:{Name:'\"Custom\"'}}")
     * @param count Item count
     */
    public record ActionItem(String item, int count) {
        public CompoundTag toNbt() {
            CompoundTag tag = new CompoundTag();
            tag.putString(TAG_ITEM, item);
            tag.putInt(TAG_ITEM_COUNT, count);
            return tag;
        }

        public static ActionItem fromNbt(CompoundTag tag) {
            return new ActionItem(tag.getString(TAG_ITEM), tag.getInt(TAG_ITEM_COUNT));
        }

        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("id", item);
            json.addProperty("count", count);
            return json;
        }

        public static ActionItem fromJson(JsonObject json) {
            String item = GsonHelper.getAsString(json, "id");
            int count = GsonHelper.getAsInt(json, "count", 1);
            return new ActionItem(item, count);
        }

        public @Nullable ItemStack toItemStack() {
            try {
                ItemParser.ItemResult result = ItemParser.parseForItem(
                        BuiltInRegistries.ITEM.asLookup(),
                        new StringReader(item)
                );
                ItemStack stack = new ItemStack(result.item(), count);
                if (result.nbt() != null) {
                    stack.setTag(result.nbt());
                }
                return stack;
            } catch (CommandSyntaxException e) {
                return null;
            }
        }
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_LABEL, label);
        if (labelTemplate != null) {
            tag.putString(TAG_LABEL_TEMPLATE, labelTemplate);
        }

        // Save commands as list
        ListTag commandsList = new ListTag();
        for (String cmd : commands) {
            commandsList.add(StringTag.valueOf(cmd));
        }
        tag.put(TAG_COMMANDS, commandsList);

        if (replyText != null) {
            tag.putString(TAG_REPLY, replyText);
        }

        if (!itemsVisual.isEmpty()) {
            tag.put(TAG_ITEMS_VISUAL, itemListToNbt(itemsVisual));
        }
        if (!itemsInput.isEmpty()) {
            tag.put(TAG_ITEMS_INPUT, itemListToNbt(itemsInput));
        }
        if (!itemsOutput.isEmpty()) {
            tag.put(TAG_ITEMS_OUTPUT, itemListToNbt(itemsOutput));
        }
        if (nextState != null) {
            tag.putString(TAG_NEXT_STATE, nextState);
        }
        if (condition != null) {
            tag.putString(TAG_CONDITION, condition);
        }
        if (playerInput != null) {
            tag.put(TAG_PLAYER_INPUT, playerInput.toNbt());
        }

        return tag;
    }

    private static ListTag itemListToNbt(List<ActionItem> items) {
        ListTag list = new ListTag();
        for (ActionItem item : items) {
            list.add(item.toNbt());
        }
        return list;
    }

    private static List<ActionItem> itemListFromNbt(CompoundTag tag, String key) {
        List<ActionItem> items = new ArrayList<>();
        if (tag.contains(key)) {
            ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                items.add(ActionItem.fromNbt(list.getCompound(i)));
            }
        }
        return items;
    }

    public static ChatAction fromNbt(CompoundTag tag) {
        String label = tag.getString(TAG_LABEL);
        String labelTemplate = tag.contains(TAG_LABEL_TEMPLATE) ? tag.getString(TAG_LABEL_TEMPLATE) : null;

        List<String> commands = new ArrayList<>();
        if (tag.contains(TAG_COMMANDS)) {
            ListTag commandsList = tag.getList(TAG_COMMANDS, Tag.TAG_STRING);
            for (int i = 0; i < commandsList.size(); i++) {
                commands.add(commandsList.getString(i));
            }
        }

        String replyText = tag.contains(TAG_REPLY) ? tag.getString(TAG_REPLY) : null;
        String nextState = tag.contains(TAG_NEXT_STATE) ? tag.getString(TAG_NEXT_STATE) : null;
        String condition = tag.contains(TAG_CONDITION) ? tag.getString(TAG_CONDITION) : null;
        PlayerInputConfig playerInput = tag.contains(TAG_PLAYER_INPUT)
                ? PlayerInputConfig.fromNbt(tag.getCompound(TAG_PLAYER_INPUT))
                : null;

        List<ActionItem> itemsVisual = itemListFromNbt(tag, TAG_ITEMS_VISUAL);
        List<ActionItem> itemsInput = itemListFromNbt(tag, TAG_ITEMS_INPUT);
        List<ActionItem> itemsOutput = itemListFromNbt(tag, TAG_ITEMS_OUTPUT);

        return new ChatAction(label, labelTemplate, commands, replyText, itemsVisual, itemsInput, itemsOutput, nextState, condition, playerInput);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("label", label);
        if (labelTemplate != null) json.addProperty("labelTemplate", labelTemplate);

        JsonArray cmds = new JsonArray();
        for (String cmd : commands) cmds.add(cmd);
        json.add("commands", cmds);

        if (replyText != null) json.addProperty("reply", replyText);
        if (!itemsVisual.isEmpty()) json.add("itemsVisual", itemListToJson(itemsVisual));
        if (!itemsInput.isEmpty()) json.add("itemsInput", itemListToJson(itemsInput));
        if (!itemsOutput.isEmpty()) json.add("itemsOutput", itemListToJson(itemsOutput));
        if (nextState != null) json.addProperty("nextState", nextState);
        if (condition != null) json.addProperty("condition", condition);
        if (playerInput != null) json.add("playerInput", playerInput.toJson());
        return json;
    }

    private static JsonArray itemListToJson(List<ActionItem> items) {
        JsonArray arr = new JsonArray();
        for (ActionItem item : items) arr.add(item.toJson());
        return arr;
    }

    private static List<ActionItem> itemListFromJson(JsonObject json, String key) {
        List<ActionItem> items = new ArrayList<>();
        if (json.has(key)) {
            for (var el : GsonHelper.getAsJsonArray(json, key)) {
                items.add(ActionItem.fromJson(el.getAsJsonObject()));
            }
        }
        return items;
    }

    public static ChatAction fromJson(JsonObject json) {
        String label = GsonHelper.getAsString(json, "label");
        String labelTemplate = json.has("labelTemplate") ? GsonHelper.getAsString(json, "labelTemplate") : null;

        List<String> commands = new ArrayList<>();
        if (json.has("commands")) {
            for (var el : GsonHelper.getAsJsonArray(json, "commands")) {
                commands.add(el.getAsString());
            }
        }

        String replyText = json.has("reply") ? GsonHelper.getAsString(json, "reply") : null;
        String nextState = json.has("nextState") ? GsonHelper.getAsString(json, "nextState") : null;
        String condition = json.has("condition") ? GsonHelper.getAsString(json, "condition") : null;
        PlayerInputConfig playerInput = json.has("playerInput")
                ? PlayerInputConfig.fromJson(GsonHelper.getAsJsonObject(json, "playerInput"))
                : null;

        return new ChatAction(label, labelTemplate, commands, replyText,
                itemListFromJson(json, "itemsVisual"),
                itemListFromJson(json, "itemsInput"),
                itemListFromJson(json, "itemsOutput"),
                nextState, condition, playerInput);
    }

    /**
     * Checks if this action has any items to display.
     */
    public boolean hasAnyItems() {
        return !itemsVisual.isEmpty() || !itemsInput.isEmpty() || !itemsOutput.isEmpty();
    }
}
