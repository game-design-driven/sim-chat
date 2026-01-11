package com.yardenzamir.simchat.data;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a clickable action button on a chat message.
 *
 * @param label      Button text shown to player
 * @param command    Command to execute when clicked
 * @param replyText  If present, shows as player message before executing command
 */
public record ChatAction(String label, String command, @Nullable String replyText) {

    private static final String TAG_LABEL = "label";
    private static final String TAG_COMMAND = "command";
    private static final String TAG_REPLY = "reply";

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_LABEL, label);
        tag.putString(TAG_COMMAND, command);
        if (replyText != null) {
            tag.putString(TAG_REPLY, replyText);
        }
        return tag;
    }

    public static ChatAction fromNbt(CompoundTag tag) {
        return new ChatAction(
                tag.getString(TAG_LABEL),
                tag.getString(TAG_COMMAND),
                tag.contains(TAG_REPLY) ? tag.getString(TAG_REPLY) : null
        );
    }
}
