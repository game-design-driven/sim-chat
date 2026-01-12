package com.yardenzamir.simchat.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a dialogue loaded from a datapack.
 */
public record DialogueData(
        String entityId,
        String entityName,
        @Nullable String entitySubtitle,
        String text,
        List<DialogueAction> actions
) {
    /**
     * An action button within a dialogue.
     * @param items List of item stacks to display on the button
     */
    public record DialogueAction(
            String label,
            List<String> commands,
            @Nullable String reply,
            List<ActionItem> items
    ) {
        /**
         * Item to display on an action button.
         * @param item Full item string with optional NBT (e.g., "minecraft:diamond{display:{Name:'\"Custom\"'}}")
         * @param count Item count (applied after parsing)
         */
        public record ActionItem(String item, int count) {
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

        public static DialogueAction fromJson(JsonObject json) {
            String label = GsonHelper.getAsString(json, "label");

            List<String> commands = new ArrayList<>();
            if (json.has("commands")) {
                JsonArray arr = GsonHelper.getAsJsonArray(json, "commands");
                for (int i = 0; i < arr.size(); i++) {
                    commands.add(arr.get(i).getAsString());
                }
            }

            String reply = GsonHelper.getAsString(json, "reply", null);

            List<ActionItem> items = new ArrayList<>();
            if (json.has("items")) {
                JsonArray arr = GsonHelper.getAsJsonArray(json, "items");
                for (int i = 0; i < arr.size(); i++) {
                    items.add(ActionItem.fromJson(arr.get(i).getAsJsonObject()));
                }
            }

            return new DialogueAction(label, commands, reply, items);
        }
    }

    public static DialogueData fromJson(JsonObject json) {
        String entityId = GsonHelper.getAsString(json, "entityId");
        String entityName = GsonHelper.getAsString(json, "entityName");
        String entitySubtitle = GsonHelper.getAsString(json, "entitySubtitle", null);
        String text = GsonHelper.getAsString(json, "text");

        List<DialogueAction> actions = new ArrayList<>();
        if (json.has("actions")) {
            JsonArray actionsArray = GsonHelper.getAsJsonArray(json, "actions");
            for (int i = 0; i < actionsArray.size(); i++) {
                actions.add(DialogueAction.fromJson(actionsArray.get(i).getAsJsonObject()));
            }
        }

        return new DialogueData(entityId, entityName, entitySubtitle, text, actions);
    }

    /**
     * Converts this dialogue to a ChatMessage.
     * Uses the entityId from dialogue data, or falls back to the provided one.
     * Entity name, subtitle, and avatar fall back to EntityConfigManager values.
     */
    public ChatMessage toMessage(String fallbackEntityId, long worldDay) {
        String resolvedEntityId = this.entityId != null ? this.entityId : fallbackEntityId;

        // Apply entity config fallbacks
        String resolvedName = EntityConfigManager.getName(resolvedEntityId, entityName);
        String resolvedSubtitle = EntityConfigManager.getSubtitle(resolvedEntityId, entitySubtitle);
        String resolvedAvatar = EntityConfigManager.getAvatar(resolvedEntityId, null);

        List<ChatAction> chatActions = new ArrayList<>();
        for (DialogueAction action : actions) {
            List<ChatAction.ActionItem> chatItems = new ArrayList<>();
            for (DialogueAction.ActionItem item : action.items()) {
                chatItems.add(new ChatAction.ActionItem(item.item(), item.count()));
            }
            chatActions.add(new ChatAction(action.label(), action.commands(), action.reply(), chatItems));
        }
        return ChatMessage.fromEntity(resolvedEntityId, resolvedName, resolvedSubtitle, resolvedAvatar, text, worldDay, chatActions);
    }
}
