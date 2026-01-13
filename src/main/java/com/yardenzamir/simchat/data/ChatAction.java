package com.yardenzamir.simchat.data;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a clickable action button on a chat message.
 *
 * @param label        Button text shown to player
 * @param commands     Commands to execute when clicked (in order)
 * @param replyText    If present, shows as player message before executing commands
 * @param itemsVisual  Items displayed on button (visual only, no functionality)
 * @param itemsInput   Items required from player inventory (consumed on click, blue background)
 * @param itemsOutput  Items given to player on click (orange background)
 * @param nextState    If present, dialogue resource location to send after this action (e.g., "mypack:npc/next")
 * @param condition    If present, condition that must pass for action to be visible (e.g., "kjs:hasHighRep", "!flag:seen_intro")
 */
public record ChatAction(
        String label,
        List<String> commands,
        @Nullable String replyText,
        List<ActionItem> itemsVisual,
        List<ActionItem> itemsInput,
        List<ActionItem> itemsOutput,
        @Nullable String nextState,
        @Nullable String condition
) {

    private static final String TAG_LABEL = "label";
    private static final String TAG_COMMANDS = "commands";
    private static final String TAG_REPLY = "reply";
    private static final String TAG_ITEMS_VISUAL = "itemsVisual";
    private static final String TAG_ITEMS_INPUT = "itemsInput";
    private static final String TAG_ITEMS_OUTPUT = "itemsOutput";
    private static final String TAG_NEXT_STATE = "nextState";
    private static final String TAG_CONDITION = "condition";
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

        List<ActionItem> itemsVisual = itemListFromNbt(tag, TAG_ITEMS_VISUAL);
        List<ActionItem> itemsInput = itemListFromNbt(tag, TAG_ITEMS_INPUT);
        List<ActionItem> itemsOutput = itemListFromNbt(tag, TAG_ITEMS_OUTPUT);

        return new ChatAction(label, commands, replyText, itemsVisual, itemsInput, itemsOutput, nextState, condition);
    }

    /**
     * Checks if this action has any items to display.
     */
    public boolean hasAnyItems() {
        return !itemsVisual.isEmpty() || !itemsInput.isEmpty() || !itemsOutput.isEmpty();
    }
}
