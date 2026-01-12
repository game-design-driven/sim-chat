package com.yardenzamir.simchat.data;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a clickable action button on a chat message.
 *
 * @param label      Button text shown to player
 * @param commands   Commands to execute when clicked (in order)
 * @param replyText  If present, shows as player message before executing commands
 * @param items      Items to display on the button
 */
public record ChatAction(String label, List<String> commands, @Nullable String replyText, List<ActionItem> items) {

    private static final String TAG_LABEL = "label";
    private static final String TAG_COMMANDS = "commands";
    private static final String TAG_REPLY = "reply";
    private static final String TAG_ITEMS = "items";
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
            // Support legacy format with "id" field
            String item = tag.contains(TAG_ITEM) ? tag.getString(TAG_ITEM) : tag.getString("id");
            return new ActionItem(item, tag.getInt(TAG_ITEM_COUNT));
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

        if (!items.isEmpty()) {
            ListTag itemsList = new ListTag();
            for (ActionItem item : items) {
                itemsList.add(item.toNbt());
            }
            tag.put(TAG_ITEMS, itemsList);
        }

        return tag;
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

        List<ActionItem> items = new ArrayList<>();
        if (tag.contains(TAG_ITEMS)) {
            ListTag itemsList = tag.getList(TAG_ITEMS, Tag.TAG_COMPOUND);
            for (int i = 0; i < itemsList.size(); i++) {
                items.add(ActionItem.fromNbt(itemsList.getCompound(i)));
            }
        }

        return new ChatAction(label, commands, replyText, items);
    }
}
