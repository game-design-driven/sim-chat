package com.yardenzamir.simchat.client;

import com.yardenzamir.simchat.team.TeamData;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side cache for the player's current team data.
 */
@OnlyIn(Dist.CLIENT)
public class ClientTeamCache {

    private static @Nullable TeamData team;

    public static @Nullable TeamData getTeam() {
        return team;
    }

    public static void setTeam(TeamData newTeam) {
        team = newTeam;
    }

    public static void clear() {
        team = null;
    }

    public static boolean hasTeam() {
        return team != null;
    }
}
