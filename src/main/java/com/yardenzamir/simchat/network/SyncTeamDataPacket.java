package com.yardenzamir.simchat.network;

import java.util.function.Supplier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import com.yardenzamir.simchat.client.ClientTeamCache;
import com.yardenzamir.simchat.client.RuntimeTemplateResolver;
import com.yardenzamir.simchat.team.TeamData;

/**
 * Syncs full team data from server to client.
 */
public class SyncTeamDataPacket {

    private final CompoundTag teamNbt;

    public SyncTeamDataPacket(TeamData team) {
        this.teamNbt = team.toNbt();
    }

    private SyncTeamDataPacket(CompoundTag teamNbt) {
        this.teamNbt = teamNbt;
    }

    public static void encode(SyncTeamDataPacket packet, FriendlyByteBuf buf) {
        buf.writeNbt(packet.teamNbt);
    }

    public static SyncTeamDataPacket decode(FriendlyByteBuf buf) {
        return new SyncTeamDataPacket(buf.readNbt());
    }

    public static void handle(SyncTeamDataPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(packet));
        });
        ctx.get().setPacketHandled(true);
    }

    private static void handleClient(SyncTeamDataPacket packet) {
        if (packet.teamNbt == null) {
            return;
        }
        TeamData team = TeamData.fromNbt(packet.teamNbt);
        ClientTeamCache.setTeam(team);
        RuntimeTemplateResolver.clear();
    }
}
