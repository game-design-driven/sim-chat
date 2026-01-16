package com.yardenzamir.simchat.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.yardenzamir.simchat.SimChatMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads and manages dialogues from datapacks.
 * Dialogues are loaded from: data/<namespace>/simchat/<path>.json
 * Only files with .json extension are loaded - other files (README.md, scripts, etc.) are ignored.
 */
public class DialogueManager extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().create();
    private static final String DIRECTORY = "simchat";

    private static DialogueManager instance;
    private Map<ResourceLocation, DialogueData> dialogues = new HashMap<>();

    public DialogueManager() {
        super(GSON, DIRECTORY);
        instance = this;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager, ProfilerFiller profiler) {
        dialogues.clear();

        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                if (!entry.getValue().isJsonObject()) {
                    SimChatMod.LOGGER.warn("Skipping non-object dialogue file: {}", id);
                    continue;
                }
                DialogueData dialogue = DialogueData.fromJson(entry.getValue().getAsJsonObject());
                dialogues.put(id, dialogue);
            } catch (Exception e) {
                SimChatMod.LOGGER.error("Failed to load dialogue {}: {}", id, e.getMessage());
            }
        }

        SimChatMod.LOGGER.info("Loaded {} dialogues", dialogues.size());
    }

    /**
     * Gets a dialogue by its resource location.
     */
    @Nullable
    public DialogueData getDialogue(ResourceLocation id) {
        return dialogues.get(id);
    }

    /**
     * Gets the singleton instance.
     */
    @Nullable
    public static DialogueManager getInstance() {
        return instance;
    }

    /**
     * Convenience method to get a dialogue from the singleton.
     */
    @Nullable
    public static DialogueData get(ResourceLocation id) {
        return instance != null ? instance.getDialogue(id) : null;
    }

    /**
     * Gets all loaded dialogue IDs for command suggestions.
     */
    public static Collection<ResourceLocation> getDialogueIds() {
        return instance != null ? instance.dialogues.keySet() : java.util.Collections.emptySet();
    }
}
