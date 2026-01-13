package com.yardenzamir.simchat.condition;

import com.yardenzamir.simchat.team.TeamData;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * JS-friendly wrapper for TeamData with public fields.
 * Rhino can access public fields but not getter methods.
 */
public class TeamContext {
    public final String id;
    public final String title;
    public final int memberCount;
    public final int color;

    /** All team data as a Map - JS can access via data.keyname or data['keyname'] */
    public final Map<String, Object> data;

    // Keep reference for data access methods
    private final TeamData teamData;

    public TeamContext(TeamData team) {
        this.teamData = team;
        this.id = team.getId();
        this.title = team.getTitle();
        this.memberCount = team.getMemberCount();
        this.color = team.getColor();
        this.data = team.getAllData();
    }

    /** Get data value by key (for JS) */
    public Object getData(String key) {
        return teamData.getData(key);
    }

    /** Check if data key is truthy (for JS) */
    public boolean hasData(String key) {
        return teamData.hasData(key);
    }

    @Nullable
    public static TeamContext of(@Nullable TeamData team) {
        return team != null ? new TeamContext(team) : null;
    }
}
