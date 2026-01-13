package com.yardenzamir.simchat.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yardenzamir.simchat.condition.CallbackContext;
import com.yardenzamir.simchat.condition.ConditionEvaluator;
import com.yardenzamir.simchat.condition.TemplateEngine;
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
     * @param condition Condition that must pass for action to be visible (e.g., "kjs:hasHighRep", "!flag:seen_intro")
     * @param playerInput If present, transforms button into text input field
     */
    public record DialogueAction(
            String label,
            List<String> commands,
            @Nullable String reply,
            List<ChatAction.ActionItem> itemsVisual,
            List<ChatAction.ActionItem> itemsInput,
            List<ChatAction.ActionItem> itemsOutput,
            @Nullable String nextState,
            @Nullable String condition,
            @Nullable ChatAction.PlayerInputConfig playerInput
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
            String condition = GsonHelper.getAsString(json, "condition", null);
            ChatAction.PlayerInputConfig playerInput = parsePlayerInput(json);

            return new DialogueAction(label, commands, reply, itemsVisual, itemsInput, itemsOutput, nextState, condition, playerInput);
        }

        private static @Nullable ChatAction.PlayerInputConfig parsePlayerInput(JsonObject json) {
            if (!json.has("playerInput")) {
                return null;
            }

            var element = json.get("playerInput");

            // Shorthand form: "playerInput": "varName"
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                return new ChatAction.PlayerInputConfig(element.getAsString());
            }

            // Full object form
            if (element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();
                String id = GsonHelper.getAsString(obj, "id");
                int maxLength = GsonHelper.getAsInt(obj, "maxLength", ChatAction.PlayerInputConfig.DEFAULT_MAX_LENGTH);
                String pattern = GsonHelper.getAsString(obj, "pattern", null);
                String error = GsonHelper.getAsString(obj, "error", null);
                boolean saveAsData = GsonHelper.getAsBoolean(obj, "saveAsData", false);
                return new ChatAction.PlayerInputConfig(id, maxLength, pattern, error, saveAsData);
            }

            return null;
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
     * Converts this dialogue to a ChatMessage without condition filtering or template processing.
     */
    public ChatMessage toMessage(String fallbackEntityId, long worldDay) {
        return toMessage(fallbackEntityId, worldDay, null);
    }

    /**
     * Converts this dialogue to a ChatMessage with condition filtering and template processing.
     * Uses the entityId from dialogue data, or falls back to the provided one.
     * Entity name, subtitle, and avatar fall back to EntityConfigManager values.
     *
     * @param ctx If provided, filters actions by condition and processes templates in labels/text
     */
    public ChatMessage toMessage(String fallbackEntityId, long worldDay, @Nullable CallbackContext ctx) {
        String resolvedEntityId = this.entityId != null ? this.entityId : fallbackEntityId;

        // Apply entity config fallbacks
        String resolvedName = EntityConfigManager.getName(resolvedEntityId, entityName);
        String resolvedSubtitle = EntityConfigManager.getSubtitle(resolvedEntityId, entitySubtitle);
        String resolvedAvatar = EntityConfigManager.getAvatar(resolvedEntityId, null);

        // Process message text with templates if context provided
        String processedText = ctx != null ? TemplateEngine.process(text, ctx) : text;

        List<ChatAction> chatActions = new ArrayList<>();
        for (DialogueAction action : actions) {
            // Skip action if condition fails
            if (ctx != null && action.condition() != null) {
                if (!ConditionEvaluator.evaluate(action.condition(), ctx)) {
                    continue;
                }
            }

            // Process label with templates if context provided
            String processedLabel = ctx != null ? TemplateEngine.process(action.label(), ctx) : action.label();

            chatActions.add(new ChatAction(processedLabel, action.commands(), action.reply(),
                    action.itemsVisual(), action.itemsInput(), action.itemsOutput(), action.nextState(), action.condition(), action.playerInput()));
        }
        return ChatMessage.fromEntity(resolvedEntityId, resolvedName, resolvedSubtitle, resolvedAvatar, processedText, worldDay, chatActions);
    }
}
