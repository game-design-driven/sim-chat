package com.yardenzamir.simchat.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import com.yardenzamir.simchat.client.ClientTeamCache;
import com.yardenzamir.simchat.data.ChatMessage;

/**
 * Syncs a batch of messages for a specific conversation.
 * Used for initial team join and lazy loading of older messages.
 */
public class SyncMessagesPacket {

    private final String entityId;
    private final ListTag messagesNbt;
    private final int totalCount;
    private final int startIndex; // Index of first message in this batch
    private final boolean hasOlder; // Are there older messages before this batch?

    public SyncMessagesPacket(String entityId, List<ChatMessage> messages, int totalCount, int startIndex) {
        this.entityId = entityId;
        this.totalCount = totalCount;
        this.startIndex = startIndex;
        this.hasOlder = startIndex > 0; // Has older messages if batch doesn't start at 0

        this.messagesNbt = new ListTag();
        for (ChatMessage message : messages) {
            this.messagesNbt.add(message.toNbt());
        }
    }

    private SyncMessagesPacket(String entityId, ListTag messagesNbt, int totalCount, int startIndex, boolean hasOlder) {
        this.entityId = entityId;
        this.messagesNbt = messagesNbt;
        this.totalCount = totalCount;
        this.startIndex = startIndex;
        this.hasOlder = hasOlder;
    }

    public static void encode(SyncMessagesPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.entityId);
        buf.writeNbt(new CompoundTag() {{
            put("messages", packet.messagesNbt);
        }});
        buf.writeInt(packet.totalCount);
        buf.writeInt(packet.startIndex);
        buf.writeBoolean(packet.hasOlder);
    }

    public static SyncMessagesPacket decode(FriendlyByteBuf buf) {
        String entityId = buf.readUtf();
        CompoundTag tag = buf.readNbt();
        ListTag messagesNbt = tag != null ? tag.getList("messages", 10) : new ListTag();
        int totalCount = buf.readInt();
        int startIndex = buf.readInt();
        boolean hasOlder = buf.readBoolean();
        return new SyncMessagesPacket(entityId, messagesNbt, totalCount, startIndex, hasOlder);
    }

    public static void handle(SyncMessagesPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(packet));
        });
        ctx.get().setPacketHandled(true);
    }

    private static void handleClient(SyncMessagesPacket packet) {
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < packet.messagesNbt.size(); i++) {
            CompoundTag messageTag = packet.messagesNbt.getCompound(i);
            messages.add(ChatMessage.fromNbt(messageTag));
        }

        ClientTeamCache.addMessages(packet.entityId, messages, packet.totalCount,
                                     packet.startIndex, packet.hasOlder);

        // Notify chat screen if it's open
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen instanceof com.yardenzamir.simchat.client.screen.ChatScreen chatScreen) {
            chatScreen.refreshMessages();
        }
    }
}
