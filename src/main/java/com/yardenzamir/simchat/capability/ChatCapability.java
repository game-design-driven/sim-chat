package com.yardenzamir.simchat.capability;

import com.yardenzamir.simchat.SimChatMod;
import com.yardenzamir.simchat.data.PlayerChatData;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Capability for storing chat data on players.
 */
public class ChatCapability {

    public static final Capability<PlayerChatData> CHAT_DATA =
            CapabilityManager.get(new CapabilityToken<>() {});

    private static final ResourceLocation ID = SimChatMod.id("chat_data");

    /**
     * Gets chat data for a player.
     */
    public static Optional<PlayerChatData> get(Player player) {
        return player.getCapability(CHAT_DATA).resolve();
    }

    /**
     * Gets chat data or throws if not present.
     */
    public static PlayerChatData getOrThrow(Player player) {
        return get(player).orElseThrow(() ->
                new IllegalStateException("Player missing chat capability: " + player.getName().getString()));
    }

    @Mod.EventBusSubscriber(modid = SimChatMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class Registration {
        @SubscribeEvent
        public static void registerCapabilities(RegisterCapabilitiesEvent event) {
            event.register(PlayerChatData.class);
        }
    }

    @Mod.EventBusSubscriber(modid = SimChatMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class Events {

        @SubscribeEvent
        public static void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof Player) {
                event.addCapability(ID, new Provider());
            }
        }

        @SubscribeEvent
        public static void onPlayerClone(PlayerEvent.Clone event) {
            if (!event.isWasDeath() || !event.getOriginal().level().getGameRules()
                    .getBoolean(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY)) {
                // Always copy chat data on clone (respawn or dimension change)
            }
            // Copy chat data regardless of death/keepInventory
            event.getOriginal().reviveCaps();
            get(event.getOriginal()).ifPresent(oldData ->
                    get(event.getEntity()).ifPresent(newData -> newData.copyFrom(oldData)));
            event.getOriginal().invalidateCaps();
        }
    }

    private static class Provider implements ICapabilityProvider, INBTSerializable<CompoundTag> {

        private final PlayerChatData chatData = new PlayerChatData();
        private final LazyOptional<PlayerChatData> optional = LazyOptional.of(() -> chatData);

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return CHAT_DATA.orEmpty(cap, optional);
        }

        @Override
        public CompoundTag serializeNBT() {
            return chatData.toNbt();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            PlayerChatData loaded = PlayerChatData.fromNbt(nbt);
            chatData.copyFrom(loaded);
        }
    }
}
