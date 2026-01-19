package com.yardenzamir.simchat.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import com.yardenzamir.simchat.client.ClientTeamCache;

/**
 * Syncs team metadata (without messages) from server to client.
 * Messages are synced separately via SyncMessagesPacket.
 */
public class SyncTeamMetadataPacket {

    private final String teamId;
    private final String title;
    private final int color;
    private final CompoundTag members; // List of UUIDs
    private final CompoundTag entityOrder; // Ordered entity list
    private final CompoundTag messageCountPerEntity; // Map<String, Integer>
    private final CompoundTag teamData; // Custom team data

    public SyncTeamMetadataPacket(String teamId, String title, int color,
                                   java.util.List<UUID> members,
                                   java.util.List<String> entityOrder,
                                   Map<String, Integer> messageCountPerEntity,
                                   Map<String, Object> teamData) {
        this.teamId = teamId;
        this.title = title;
        this.color = color;

        // Encode members
        this.members = new CompoundTag();
        ListTag membersList = new ListTag();
        for (UUID member : members) {
            membersList.add(StringTag.valueOf(member.toString()));
        }
        this.members.put("members", membersList);

        // Encode entity order
        this.entityOrder = new CompoundTag();
        ListTag orderList = new ListTag();
        for (String entityId : entityOrder) {
            orderList.add(StringTag.valueOf(entityId));
        }
        this.entityOrder.put("order", orderList);

        // Encode message counts
        this.messageCountPerEntity = new CompoundTag();
        for (Map.Entry<String, Integer> entry : messageCountPerEntity.entrySet()) {
            this.messageCountPerEntity.putInt(entry.getKey(), entry.getValue());
        }

        // Encode team data
        this.teamData = new CompoundTag();
        for (Map.Entry<String, Object> entry : teamData.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Number number) {
                this.teamData.putDouble(entry.getKey(), number.doubleValue());
            } else if (value instanceof Boolean bool) {
                this.teamData.putBoolean(entry.getKey(), bool);
            } else if (value != null) {
                this.teamData.putString(entry.getKey(), value.toString());
            }
        }
    }

    private SyncTeamMetadataPacket(String teamId, String title, int color,
                                    CompoundTag members, CompoundTag entityOrder,
                                    CompoundTag messageCountPerEntity, CompoundTag teamData) {
        this.teamId = teamId;
        this.title = title;
        this.color = color;
        this.members = members;
        this.entityOrder = entityOrder;
        this.messageCountPerEntity = messageCountPerEntity;
        this.teamData = teamData;
    }

    public static void encode(SyncTeamMetadataPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.teamId);
        buf.writeUtf(packet.title);
        buf.writeInt(packet.color);
        buf.writeNbt(packet.members);
        buf.writeNbt(packet.entityOrder);
        buf.writeNbt(packet.messageCountPerEntity);
        buf.writeNbt(packet.teamData);
    }

    public static SyncTeamMetadataPacket decode(FriendlyByteBuf buf) {
        return new SyncTeamMetadataPacket(
            buf.readUtf(),
            buf.readUtf(),
            buf.readInt(),
            buf.readNbt(),
            buf.readNbt(),
            buf.readNbt(),
            buf.readNbt()
        );
    }

    public static void handle(SyncTeamMetadataPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(packet));
        });
        ctx.get().setPacketHandled(true);
    }

    private static void handleClient(SyncTeamMetadataPacket packet) {
        // Decode members
        java.util.List<UUID> members = new java.util.ArrayList<>();
        ListTag membersList = packet.members.getList("members", Tag.TAG_STRING);
        for (int i = 0; i < membersList.size(); i++) {
            members.add(UUID.fromString(membersList.getString(i)));
        }

        // Decode entity order
        java.util.List<String> entityOrder = new java.util.ArrayList<>();
        ListTag orderList = packet.entityOrder.getList("order", Tag.TAG_STRING);
        for (int i = 0; i < orderList.size(); i++) {
            entityOrder.add(orderList.getString(i));
        }

        // Decode message counts
        Map<String, Integer> messageCounts = new HashMap<>();
        for (String key : packet.messageCountPerEntity.getAllKeys()) {
            messageCounts.put(key, packet.messageCountPerEntity.getInt(key));
        }

        // Decode team data
        Map<String, Object> teamData = new HashMap<>();
        for (String key : packet.teamData.getAllKeys()) {
            byte type = packet.teamData.getTagType(key);
            if (type == Tag.TAG_STRING) {
                teamData.put(key, packet.teamData.getString(key));
            } else if (type == Tag.TAG_BYTE) {
                teamData.put(key, packet.teamData.getBoolean(key));
            } else if (type == Tag.TAG_DOUBLE || type == Tag.TAG_FLOAT || type == Tag.TAG_INT
                    || type == Tag.TAG_LONG || type == Tag.TAG_SHORT) {
                teamData.put(key, packet.teamData.getDouble(key));
            }
        }

        ClientTeamCache.setTeamMetadata(packet.teamId, packet.title, packet.color,
                                        members, entityOrder, messageCounts, teamData);
    }
}
