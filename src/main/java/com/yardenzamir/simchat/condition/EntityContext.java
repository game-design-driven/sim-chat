package com.yardenzamir.simchat.condition;

import com.yardenzamir.simchat.data.ChatMessage;
import com.yardenzamir.simchat.team.TeamData;
import org.jetbrains.annotations.Nullable;

/**
 * JS-friendly wrapper for entity/conversation data with public fields.
 */
public class EntityContext {
    public final String id;
    @Nullable
    public final String displayName;
    @Nullable
    public final String imageId;
    @Nullable
    public final String subtitle;
    public final int messageCount;
    public final boolean isTyping;
    @Nullable
    public final String lastMessage;

    public EntityContext(TeamData team, String entityId) {
        this.id = entityId;
        this.displayName = team.getEntityDisplayName(entityId);
        this.imageId = team.getEntityImageId(entityId);
        this.subtitle = team.getEntitySubtitle(entityId);
        this.messageCount = team.getMessageCount(entityId);
        this.isTyping = team.isTyping(entityId);

        ChatMessage last = team.getLastMessage(entityId);
        this.lastMessage = last != null ? last.content() : null;
    }

    @Nullable
    public static EntityContext of(@Nullable TeamData team, @Nullable String entityId) {
        if (team == null || entityId == null) return null;
        return new EntityContext(team, entityId);
    }
}
