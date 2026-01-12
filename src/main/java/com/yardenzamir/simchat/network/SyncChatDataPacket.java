package com.yardenzamir.simchat.network;

import com.yardenzamir.simchat.capability.ChatCapability;
import com.yardenzamir.simchat.data.PlayerChatData;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Syncs per-player read receipt data from server to client.
 * Conversation data is synced via SyncTeamDataPacket.
 */
public class SyncChatDataPacket {

    private final CompoundTag data;

    public SyncChatDataPacket(PlayerChatData chatData) {
        this.data = chatData.toNbt();
    }

    private SyncChatDataPacket(CompoundTag data) {
        this.data = data;
    }

    public static void encode(SyncChatDataPacket packet, FriendlyByteBuf buf) {
        buf.writeNbt(packet.data);
    }

    public static SyncChatDataPacket decode(FriendlyByteBuf buf) {
        return new SyncChatDataPacket(buf.readNbt());
    }

    public static void handle(SyncChatDataPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(packet))
        );
        ctx.get().setPacketHandled(true);
    }

    private static void handleClient(SyncChatDataPacket packet) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            ChatCapability.get(player).ifPresent(data -> {
                PlayerChatData loaded = PlayerChatData.fromNbt(packet.data);
                data.copyFrom(loaded);
            });
        }
    }
}
