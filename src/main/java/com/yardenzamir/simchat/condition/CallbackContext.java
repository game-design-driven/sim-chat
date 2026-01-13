package com.yardenzamir.simchat.condition;

import com.yardenzamir.simchat.team.TeamData;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Context passed to KubeJS callbacks.
 *
 * Only wraps SimChat-specific data. Use player for Minecraft data:
 *   ctx.player.level.dayTime
 *   ctx.player.persistentData
 *   ctx.player.inventory
 *   etc.
 */
public class CallbackContext {
    /** Minecraft player - KubeJS wraps this with full property access */
    public final ServerPlayer player;

    /** Team data wrapper (null if no team) */
    @Nullable
    public final TeamContext team;

    /** Current entity/conversation data wrapper (null if no entity context) */
    @Nullable
    public final EntityContext entity;

    // Raw TeamData for Java code
    @Nullable
    private final TeamData teamData;

    public CallbackContext(ServerPlayer player, @Nullable TeamData team, @Nullable String entityId) {
        this.player = player;
        this.teamData = team;
        this.team = TeamContext.of(team);
        this.entity = EntityContext.of(team, entityId);
    }

    public static CallbackContext of(ServerPlayer player, @Nullable TeamData team, @Nullable String entityId) {
        return new CallbackContext(player, team, entityId);
    }

    public static CallbackContext ofPlayer(ServerPlayer player) {
        return of(player, null, null);
    }

    // Java code compatibility
    public ServerPlayer player() { return player; }
    @Nullable public TeamData team() { return teamData; }
}
