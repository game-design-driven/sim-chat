package com.yardenzamir.simchat.condition;

import com.yardenzamir.simchat.data.ChatMessage;
import com.yardenzamir.simchat.team.TeamData;
import org.jetbrains.annotations.Nullable;

/**
 * JS-friendly wrapper for entity/conversation data.
 * Uses getter methods for Rhino bean property access (getX -> x in JS).
 */
public class EntityContext {
    private final String id;
    @Nullable private final String displayName;
    @Nullable private final String imageId;
    @Nullable private final String subtitle;
    private final int messageCount;
    private final boolean typing;
    @Nullable private final String lastMessage;

    public EntityContext(TeamData team, String entityId) {
        this.id = entityId;
        this.displayName = team.getEntityDisplayName(entityId);
        this.imageId = team.getEntityImageId(entityId);
        this.subtitle = team.getEntitySubtitle(entityId);
        this.messageCount = team.getMessageCount(entityId);
        this.typing = team.isTyping(entityId);

        ChatMessage last = team.getLastMessage(entityId);
        this.lastMessage = last != null ? last.content() : null;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public String getDisplayName() {
        return displayName;
    }

    @Nullable
    public String getImageId() {
        return imageId;
    }

    @Nullable
    public String getSubtitle() {
        return subtitle;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public boolean isTyping() {
        return typing;
    }

    @Nullable
    public String getLastMessage() {
        return lastMessage;
    }

    @Nullable
    public static EntityContext of(@Nullable TeamData team, @Nullable String entityId) {
        if (team == null || entityId == null) return null;
        return new EntityContext(team, entityId);
    }
}
