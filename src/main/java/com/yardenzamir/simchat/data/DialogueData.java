package com.yardenzamir.simchat.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a dialogue loaded from a datapack.
 */
public record DialogueData(
        String sender,
        String image,
        String text,
        List<DialogueAction> actions
) {
    /**
     * An action button within a dialogue.
     */
    public record DialogueAction(
            String label,
            String command,
            @Nullable String reply
    ) {
        public static DialogueAction fromJson(JsonObject json) {
            String label = json.get("label").getAsString();
            String command = json.has("command") ? json.get("command").getAsString() : "";
            String reply = json.has("reply") ? json.get("reply").getAsString() : null;
            return new DialogueAction(label, command, reply);
        }
    }

    public static DialogueData fromJson(JsonObject json) {
        String sender = json.get("sender").getAsString();
        String image = json.get("image").getAsString();
        String text = json.get("text").getAsString();

        List<DialogueAction> actions = new ArrayList<>();
        if (json.has("actions")) {
            JsonArray actionsArray = json.getAsJsonArray("actions");
            for (JsonElement element : actionsArray) {
                actions.add(DialogueAction.fromJson(element.getAsJsonObject()));
            }
        }

        return new DialogueData(sender, image, text, actions);
    }

    /**
     * Converts this dialogue to a ChatMessage.
     */
    public ChatMessage toMessage(String entityId, long worldDay) {
        List<ChatAction> chatActions = new ArrayList<>();
        for (DialogueAction action : actions) {
            chatActions.add(new ChatAction(action.label(), action.command(), action.reply()));
        }
        return ChatMessage.fromEntity(entityId, sender, image, text, worldDay, chatActions);
    }
}
