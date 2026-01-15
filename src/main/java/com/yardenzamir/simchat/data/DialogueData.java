package com.yardenzamir.simchat.data;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;

import com.yardenzamir.simchat.condition.CallbackContext;
import com.yardenzamir.simchat.condition.ConditionEvaluator;
import com.yardenzamir.simchat.condition.TemplateEngine;
import com.yardenzamir.simchat.condition.TemplateEngine.TemplateCompilation;
import com.yardenzamir.simchat.team.TeamData;

/**
 * Represents a dialogue loaded from a datapack.
 */
public record DialogueData(
        String entityId,
        String entityName,
        @Nullable String entitySubtitle,
        String text,
        List<String> textVariants,
        VariantMode textMode,
        List<DialogueAction> actions
) {
    public enum VariantMode {
        RANDOM("random"),
        SEQUENTIAL("sequential"),
        SEQUENTIAL_CYCLE("sequential_cycle");

        private final String id;

        VariantMode(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static VariantMode fromString(String value, String fieldName) {
            for (VariantMode mode : values()) {
                if (mode.id.equals(value)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Invalid " + fieldName + ": " + value);
        }
    }

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
            @Nullable String id,
            List<String> labelVariants,
            VariantMode labelMode,
            List<String> commands,
            @Nullable List<String> replyVariants,
            VariantMode replyMode,
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
            List<String> labelVariants = parseLabelVariants(json.get("label"));
            VariantMode labelMode = parseMode(json, "labelMode");
            List<String> commands = new ArrayList<>();
            if (json.has("commands")) {
                JsonArray arr = GsonHelper.getAsJsonArray(json, "commands");
                for (int i = 0; i < arr.size(); i++) {
                    commands.add(arr.get(i).getAsString());
                }
            }

            List<String> replyVariants = parseOptionalVariants(json.get("reply"));
            VariantMode replyMode = parseMode(json, "replyMode");
            String actionId = GsonHelper.getAsString(json, "id", null);

            if (replyVariants == null && json.has("replyMode")) {
                throw new IllegalArgumentException("replyMode requires reply variants");
            }

            if (requiresTracking(labelMode) || requiresTracking(replyMode)) {
                if (actionId == null || actionId.isEmpty()) {
                    throw new IllegalArgumentException("Action id is required for sequential variants");
                }
            }

            List<ChatAction.ActionItem> itemsVisual = parseItemArray(json, "itemsVisual");
            List<ChatAction.ActionItem> itemsInput = parseItemArray(json, "itemsInput");
            List<ChatAction.ActionItem> itemsOutput = parseItemArray(json, "itemsOutput");
            String nextState = GsonHelper.getAsString(json, "nextState", null);
            String condition = GsonHelper.getAsString(json, "condition", null);
            ChatAction.PlayerInputConfig playerInput = parsePlayerInput(json);

            return new DialogueAction(actionId, labelVariants, labelMode, commands, replyVariants, replyMode,
                    itemsVisual, itemsInput, itemsOutput, nextState, condition, playerInput);
        }

        private static @Nullable ChatAction.PlayerInputConfig parsePlayerInput(JsonObject json) {
            if (!json.has("playerInput")) {
                return null;
            }

            var element = json.get("playerInput");

            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                return new ChatAction.PlayerInputConfig(element.getAsString());
            }

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
        List<String> textVariants = parseTextVariants(json.get("text"));
        VariantMode textMode = parseMode(json, "textMode");

        List<DialogueAction> actions = new ArrayList<>();
        if (json.has("actions")) {
            JsonArray actionsArray = GsonHelper.getAsJsonArray(json, "actions");
            for (int i = 0; i < actionsArray.size(); i++) {
                actions.add(DialogueAction.fromJson(actionsArray.get(i).getAsJsonObject()));
            }
        }

        return new DialogueData(entityId, entityName, entitySubtitle, textVariants.get(0), textVariants, textMode, actions);
    }

    public ChatMessage toMessage(String fallbackEntityId, long worldDay) {
        return toMessage(fallbackEntityId, worldDay, null, null);
    }

    public ChatMessage toMessage(String fallbackEntityId, long worldDay, @Nullable CallbackContext ctx) {
        return toMessage(fallbackEntityId, worldDay, ctx, null);
    }

    public ChatMessage toMessage(String fallbackEntityId, long worldDay, @Nullable CallbackContext ctx, @Nullable ResourceLocation dialogueId) {
        String resolvedEntityId = this.entityId != null ? this.entityId : fallbackEntityId;

        String resolvedName = EntityConfigManager.getName(resolvedEntityId, entityName);
        String resolvedSubtitle = EntityConfigManager.getSubtitle(resolvedEntityId, entitySubtitle);
        String resolvedAvatar = EntityConfigManager.getAvatar(resolvedEntityId, null);

        TeamData team = ctx != null ? ctx.team() : null;
        if (team != null && dialogueId != null) {
            team.addData(dialogueCountKey(dialogueId), 1);
        }

        TemplateCompilation nameCompilation = ctx != null
                ? TemplateEngine.compile(resolvedName, ctx)
                : new TemplateCompilation(resolvedName, null);
        TemplateCompilation subtitleCompilation = resolvedSubtitle != null && ctx != null
                ? TemplateEngine.compile(resolvedSubtitle, ctx)
                : new TemplateCompilation(resolvedSubtitle, null);

        String selectedText = selectVariant(textVariants, textMode, team,
                team != null && dialogueId != null ? dialogueSeqKey(dialogueId) : null);
        TemplateCompilation textCompilation = ctx != null
                ? TemplateEngine.compile(selectedText, ctx)
                : new TemplateCompilation(selectedText, null);

        List<ChatAction> chatActions = new ArrayList<>();
        for (DialogueAction action : actions) {
            if (ctx != null && action.condition() != null) {
                if (!ConditionEvaluator.evaluate(action.condition(), ctx)) {
                    continue;
                }
            }

            String actionId = action.id();
            if (team != null && dialogueId != null && actionId != null && !actionId.isEmpty()) {
                team.addData(actionCountKey(dialogueId, actionId), 1);
            }

            String labelSeqKey = team != null && dialogueId != null && actionId != null
                    ? actionLabelSeqKey(dialogueId, actionId)
                    : null;
            String replySeqKey = team != null && dialogueId != null && actionId != null
                    ? actionReplySeqKey(dialogueId, actionId)
                    : null;

            String selectedLabel = selectVariant(action.labelVariants(), action.labelMode(), team, labelSeqKey);
            TemplateCompilation labelCompilation = ctx != null
                    ? TemplateEngine.compile(selectedLabel, ctx)
                    : new TemplateCompilation(selectedLabel, null);

            String selectedReply = null;
            if (action.replyVariants() != null) {
                selectedReply = selectVariant(action.replyVariants(), action.replyMode(), team, replySeqKey);
            }

            chatActions.add(new ChatAction(labelCompilation.compiledText(), labelCompilation.runtimeTemplate(),
                    action.commands(), selectedReply, action.itemsVisual(), action.itemsInput(), action.itemsOutput(),
                    action.nextState(), action.condition(), action.playerInput()));
        }
        return ChatMessage.fromEntity(resolvedEntityId, nameCompilation.compiledText(), subtitleCompilation.compiledText(),
                resolvedAvatar, textCompilation.compiledText(), nameCompilation.runtimeTemplate(),
                subtitleCompilation.runtimeTemplate(), textCompilation.runtimeTemplate(), worldDay, chatActions);
    }

    private static boolean requiresTracking(VariantMode mode) {
        return mode == VariantMode.SEQUENTIAL || mode == VariantMode.SEQUENTIAL_CYCLE;
    }

    private static VariantMode parseMode(JsonObject json, String key) {
        if (!json.has(key)) {
            return VariantMode.RANDOM;
        }
        String mode = GsonHelper.getAsString(json, key);
        return VariantMode.fromString(mode, key);
    }

    private static List<String> parseTextVariants(JsonElement element) {
        if (element == null) {
            throw new IllegalArgumentException("Missing text");
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String value = element.getAsString();
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Text must not be empty");
            }
            return List.of(value);
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            if (array.isEmpty()) {
                throw new IllegalArgumentException("Text variants must not be empty");
            }
            List<String> values = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                JsonElement entry = array.get(i);
                if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("Text variants must be strings");
                }
                String value = entry.getAsString();
                if (value.isEmpty()) {
                    throw new IllegalArgumentException("Text variants must not be empty");
                }
                values.add(value);
            }
            return values;
        }
        throw new IllegalArgumentException("Text must be a string or array of strings");
    }

    private static List<String> parseLabelVariants(JsonElement element) {
        List<String> variants = parseRequiredVariants(element, "label");
        for (String value : variants) {
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Label must not be empty");
            }
        }
        return variants;
    }

    private static @Nullable List<String> parseOptionalVariants(@Nullable JsonElement element) {
        if (element == null) {
            return null;
        }
        return parseRequiredVariants(element, "reply");
    }

    private static List<String> parseRequiredVariants(JsonElement element, String fieldName) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return List.of(element.getAsString());
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            if (array.isEmpty()) {
                throw new IllegalArgumentException(fieldName + " variants must not be empty");
            }
            List<String> values = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                JsonElement entry = array.get(i);
                if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException(fieldName + " variants must be strings");
                }
                values.add(entry.getAsString());
            }
            return values;
        }
        throw new IllegalArgumentException(fieldName + " must be a string or array of strings");
    }

    private static String selectVariant(List<String> variants, VariantMode mode, @Nullable TeamData team, @Nullable String seqKey) {
        if (variants.isEmpty()) {
            return "";
        }
        if (variants.size() == 1 || mode == VariantMode.RANDOM || team == null || seqKey == null) {
            if (variants.size() == 1) {
                return variants.get(0);
            }
            return variants.get(ThreadLocalRandom.current().nextInt(variants.size()));
        }

        int currentIndex = team.getDataInt(seqKey, 0);
        int clampedIndex = Math.max(0, Math.min(currentIndex, variants.size() - 1));
        int nextIndex = switch (mode) {
            case SEQUENTIAL -> Math.min(clampedIndex + 1, variants.size() - 1);
            case SEQUENTIAL_CYCLE -> (clampedIndex + 1) % variants.size();
            default -> clampedIndex;
        };
        team.setData(seqKey, nextIndex);
        return variants.get(clampedIndex);
    }

    private static String dialogueCountKey(ResourceLocation dialogueId) {
        return "conversation/" + dialogueId + "/count";
    }

    private static String dialogueSeqKey(ResourceLocation dialogueId) {
        return "conversation/" + dialogueId + "/seq";
    }

    private static String actionCountKey(ResourceLocation dialogueId, String actionId) {
        return "conversation/" + dialogueId + "/" + actionId + "/count";
    }

    private static String actionLabelSeqKey(ResourceLocation dialogueId, String actionId) {
        return "conversation/" + dialogueId + "/" + actionId + "/label/seq";
    }

    private static String actionReplySeqKey(ResourceLocation dialogueId, String actionId) {
        return "conversation/" + dialogueId + "/" + actionId + "/reply/seq";
    }
}
