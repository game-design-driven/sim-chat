package com.yardenzamir.simchat.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
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
         */
        public record ActionItem(String itemId, int count) {
            public static ActionItem fromJson(JsonObject json) {
                String id = json.get("id").getAsString();
                int count = json.has("count") ? json.get("count").getAsInt() : 1;
                return new ActionItem(id, count);
            }

            public @Nullable ItemStack toItemStack() {
                ResourceLocation loc = ResourceLocation.tryParse(itemId);
                if (loc == null) return null;
                var item = ForgeRegistries.ITEMS.getValue(loc);
                if (item == null) return null;
                return new ItemStack(item, count);
            }
        }

        public static DialogueAction fromJson(JsonObject json) {
            String label = json.get("label").getAsString();

            List<String> commands = new ArrayList<>();
            if (json.has("commands")) {
                JsonArray arr = json.getAsJsonArray("commands");
                for (JsonElement el : arr) {
                    commands.add(el.getAsString());
                }
            }

            String reply = json.has("reply") ? json.get("reply").getAsString() : null;

            List<ActionItem> items = new ArrayList<>();
            if (json.has("items")) {
                JsonArray arr = json.getAsJsonArray("items");
                for (JsonElement el : arr) {
                    items.add(ActionItem.fromJson(el.getAsJsonObject()));
                }
            }

            return new DialogueAction(label, commands, reply, items);
        }
    }

    public static DialogueData fromJson(JsonObject json) {
        String entityId = json.get("entityId").getAsString();
        String entityName = json.get("entityName").getAsString();
        String entitySubtitle = json.has("entitySubtitle")
                ? json.get("entitySubtitle").getAsString()
                : null;
        String text = json.get("text").getAsString();

        List<DialogueAction> actions = new ArrayList<>();
        if (json.has("actions")) {
            JsonArray actionsArray = json.getAsJsonArray("actions");
            for (JsonElement element : actionsArray) {
                actions.add(DialogueAction.fromJson(element.getAsJsonObject()));
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
                chatItems.add(new ChatAction.ActionItem(item.itemId(), item.count()));
            }
            chatActions.add(new ChatAction(action.label(), action.commands(), action.reply(), chatItems));
        }
        return ChatMessage.fromEntity(resolvedEntityId, resolvedName, resolvedSubtitle, resolvedAvatar, text, worldDay, chatActions);
    }
}
