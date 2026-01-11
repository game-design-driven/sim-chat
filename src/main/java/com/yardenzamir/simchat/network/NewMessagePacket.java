package com.yardenzamir.simchat.network;

import com.yardenzamir.simchat.capability.ChatCapability;
import com.yardenzamir.simchat.client.ChatToast;
import com.yardenzamir.simchat.client.ClientSetup;
import com.yardenzamir.simchat.config.ClientConfig;
import com.yardenzamir.simchat.data.ChatMessage;
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
 * Sends a single new message from server to client.
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
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player != null) {
            ChatMessage message = ChatMessage.fromNbt(packet.messageData);

            ChatCapability.get(player).ifPresent(data -> {
                // Clear typing state when message arrives
                data.setTyping(message.entityId(), false);
                data.addMessage(message);
            });

            // Play notification sound and show toast for entity messages (not player replies)
            if (!message.isPlayerMessage()) {
                // Play sound if configured
                String soundPath = ClientConfig.NOTIFICATION_SOUND.get();
                if (!soundPath.isEmpty()) {
                    ResourceLocation soundId = new ResourceLocation(soundPath);
                    SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(soundId);
                    if (sound != null) {
                        float volume = ClientConfig.NOTIFICATION_VOLUME.get().floatValue();
                        mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0f, volume));
                    }
                }

                // Show toast if enabled
                if (packet.showToast && ClientConfig.SHOW_TOASTS.get()) {
                    String keybindName = ClientSetup.getOpenChatKeyName();
                    mc.getToasts().addToast(new ChatToast(message, keybindName));
                }
            }
        }
    }
}
