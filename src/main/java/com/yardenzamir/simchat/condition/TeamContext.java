package com.yardenzamir.simchat.condition;

import com.yardenzamir.simchat.team.TeamData;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * JS-friendly wrapper for TeamData.
 * Uses getter methods for Rhino bean property access (getX -> x in JS).
 */
public class TeamContext {
    private final TeamData teamData;
    private final Map<String, Object> dataMap;

    public TeamContext(TeamData team) {
        this.teamData = team;
        this.dataMap = team.getAllData();
    }

    public String getId() {
        return teamData.getId();
    }

    public String getTitle() {
        return teamData.getTitle();
    }

    public int getMemberCount() {
        return teamData.getMemberCount();
    }

    public int getColor() {
        return teamData.getColor();
    }

    /** All team data as a Map - JS can access via data.keyname or data['keyname'] */
    public Map<String, Object> getData() {
        return dataMap;
    }

    /** Get data value by key */
    public Object getData(String key) {
        return teamData.getData(key);
    }

    /** Check if data key is truthy */
    public boolean hasData(String key) {
        return teamData.hasData(key);
    }

    @Nullable
    public static TeamContext of(@Nullable TeamData team) {
        return team != null ? new TeamContext(team) : null;
    }
}
