package com.yardenzamir.simchat.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
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
     * @param itemsVisual Items displayed on button (visual only)
     * @param itemsInput Items required from player inventory (consumed on click)
     * @param itemsOutput Items given to player on click
     * @param nextState Dialogue resource location to auto-send after this action (e.g., "mypack:npc/next")
     */
    public record DialogueAction(
            String label,
            List<String> commands,
            @Nullable String reply,
            List<ChatAction.ActionItem> itemsVisual,
            List<ChatAction.ActionItem> itemsInput,
            List<ChatAction.ActionItem> itemsOutput,
            @Nullable String nextState
    ) {
        private static List<ChatAction.ActionItem> parseItemArray(JsonObject json, String key) {
            List<ChatAction.ActionItem> items = new ArrayList<>();
            if (json.has(key)) {
                JsonArray arr = GsonHelper.getAsJsonArray(json, key);
                for (int i = 0; i < arr.size(); i++) {
                    items.add(ChatAction.ActionItem.fromJson(arr.get(i).getAsJsonObject()));
                }
            }
            return items;
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

            List<ChatAction.ActionItem> itemsVisual = parseItemArray(json, "itemsVisual");
            List<ChatAction.ActionItem> itemsInput = parseItemArray(json, "itemsInput");
            List<ChatAction.ActionItem> itemsOutput = parseItemArray(json, "itemsOutput");
            String nextState = GsonHelper.getAsString(json, "nextState", null);

            return new DialogueAction(label, commands, reply, itemsVisual, itemsInput, itemsOutput, nextState);
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
            chatActions.add(new ChatAction(action.label(), action.commands(), action.reply(),
                    action.itemsVisual(), action.itemsInput(), action.itemsOutput(), action.nextState()));
        }
        return ChatMessage.fromEntity(resolvedEntityId, resolvedName, resolvedSubtitle, resolvedAvatar, text, worldDay, chatActions);
    }
}
