package com.yardenzamir.simchat.network;

import com.yardenzamir.simchat.client.ChatToast;
import com.yardenzamir.simchat.client.ClientSetup;
import com.yardenzamir.simchat.client.ClientTeamCache;
import com.yardenzamir.simchat.client.screen.ChatScreen;
import com.yardenzamir.simchat.config.ClientConfig;
import com.yardenzamir.simchat.data.ChatMessage;
import com.yardenzamir.simchat.team.TeamData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sends a new message notification from server to client.
 * Message data is synced via SyncTeamDataPacket; this is for sound/toast.
 */
public class NewMessagePacket {

    private final CompoundTag messageData;
    private final boolean showToast;

    public NewMessagePacket(ChatMessage message, boolean showToast) {
        this.messageData = message.toNbt();
        this.showToast = showToast;
    }

    private NewMessagePacket(CompoundTag data, boolean showToast) {
        this.messageData = data;
        this.showToast = showToast;
    }

    public static void encode(NewMessagePacket packet, FriendlyByteBuf buf) {
        buf.writeNbt(packet.messageData);
        buf.writeBoolean(packet.showToast);
    }

    public static NewMessagePacket decode(FriendlyByteBuf buf) {
        return new NewMessagePacket(buf.readNbt(), buf.readBoolean());
    }

    public static void handle(NewMessagePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(packet))
        );
        ctx.get().setPacketHandled(true);
    }

    private static void handleClient(NewMessagePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ChatMessage message = ChatMessage.fromNbt(packet.messageData);

        // Clear typing state on team data
        TeamData team = ClientTeamCache.getTeam();
        if (team != null) {
            team.setTyping(message.entityId(), false);
        }

        // Play notification sound and show toast for entity messages (not player replies)
        if (!message.isPlayerMessage()) {
            String soundPath = ClientConfig.NOTIFICATION_SOUND.get();
            if (!soundPath.isEmpty()) {
                ResourceLocation soundId = new ResourceLocation(soundPath);
                SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(soundId);
                if (sound != null) {
                    float volume = ClientConfig.NOTIFICATION_VOLUME.get().floatValue();
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0f, volume));
                }
            }

            if (packet.showToast && ClientConfig.SHOW_TOASTS.get()) {
                String keybindName = ClientSetup.getOpenChatKeyName();
                boolean showKeybindHint = !(mc.screen instanceof ChatScreen);
                mc.getToasts().addToast(new ChatToast(message, keybindName, showKeybindHint));
            }
        }
    }
}
