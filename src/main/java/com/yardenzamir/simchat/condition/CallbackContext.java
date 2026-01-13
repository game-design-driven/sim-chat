package com.yardenzamir.simchat.condition;

import com.yardenzamir.simchat.team.TeamData;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Context passed to KubeJS callbacks.
 * Uses getter methods for Rhino/KubeJS compatibility (getX -> x property in JS).
 *
 * Access in JS:
 *   ctx.player.inventory.items
 *   ctx.player.persistentData.getInt('key')
 *   ctx.team.data.myKey
 *   ctx.entity.displayName
 */
public class CallbackContext {
    private final ServerPlayer player;
    @Nullable private final TeamData teamData;
    @Nullable private final TeamContext teamContext;
    @Nullable private final EntityContext entityContext;
    private final Map<String, String> inputValues;

    public CallbackContext(ServerPlayer player, @Nullable TeamData team, @Nullable String entityId) {
        this(player, team, entityId, Collections.emptyMap());
    }

    private CallbackContext(ServerPlayer player, @Nullable TeamData team, @Nullable String entityId, Map<String, String> inputValues) {
        this.player = player;
        this.teamData = team;
        this.teamContext = TeamContext.of(team);
        this.entityContext = EntityContext.of(team, entityId);
        this.inputValues = inputValues;
    }

    /** Get the player - accessible as ctx.player in JS */
    public ServerPlayer getPlayer() {
        return player;
    }

    /** Get team context - accessible as ctx.team in JS */
    @Nullable
    public TeamContext getTeam() {
        return teamContext;
    }

    /** Get entity context - accessible as ctx.entity in JS */
    @Nullable
    public EntityContext getEntity() {
        return entityContext;
    }

    // Factory methods
    public static CallbackContext of(ServerPlayer player, @Nullable TeamData team, @Nullable String entityId) {
        return new CallbackContext(player, team, entityId);
    }

    public static CallbackContext ofPlayer(ServerPlayer player) {
        return of(player, null, null);
    }

    /**
     * Creates a new context with an additional input value.
     * Used for player input actions where {input:key} templates need resolution.
     */
    public CallbackContext withInputValue(String key, String value) {
        Map<String, String> newInputs = new HashMap<>(this.inputValues);
        newInputs.put(key, value);
        return new CallbackContext(player, teamData, entityContext != null ? entityContext.getId() : null, newInputs);
    }

    /**
     * Gets a player input value by key.
     * @return The input value or null if not set
     */
    @Nullable
    public String getInputValue(String key) {
        return inputValues.get(key);
    }

    // Java code compatibility (for TemplateEngine, ConditionEvaluator)
    public ServerPlayer player() { return player; }
    @Nullable public TeamData team() { return teamData; }
}
