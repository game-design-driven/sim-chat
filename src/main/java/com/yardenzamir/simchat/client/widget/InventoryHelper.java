package com.yardenzamir.simchat.client.widget;

import com.yardenzamir.simchat.data.ChatAction;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

import static com.yardenzamir.simchat.client.widget.ChatHistoryConstants.MAIN_INVENTORY_SIZE;

/**
 * Utility class for inventory checks in chat UI.
 */
public final class InventoryHelper {
    private InventoryHelper() {}

    /**
     * Checks if the player has all required input items.
     */
    public static boolean hasAllItems(Minecraft mc, List<ChatAction.ActionItem> items) {
        if (items.isEmpty() || mc.player == null) {
            return true;
        }

        Inventory inventory = mc.player.getInventory();
        for (ChatAction.ActionItem item : items) {
            ItemStack required = item.toItemStack();
            if (required == null) continue;

            int found = 0;
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack slot = inventory.getItem(i);
                if (ItemStack.isSameItemSameTags(slot, required)) {
                    found += slot.getCount();
                }
            }
            if (found < required.getCount()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if the player has inventory space for all output items.
     */
    public static boolean hasSpaceFor(Minecraft mc, List<ChatAction.ActionItem> items) {
        if (items.isEmpty() || mc.player == null) {
            return true;
        }

        Inventory inventory = mc.player.getInventory();
        int emptySlots = 0;
        for (int i = 0; i < MAIN_INVENTORY_SIZE; i++) {
            if (inventory.getItem(i).isEmpty()) {
                emptySlots++;
            }
        }

        int slotsNeeded = 0;
        for (ChatAction.ActionItem item : items) {
            ItemStack toGive = item.toItemStack();
            if (toGive == null) continue;

            int remaining = toGive.getCount();
            for (int i = 0; i < MAIN_INVENTORY_SIZE; i++) {
                ItemStack slot = inventory.getItem(i);
                if (ItemStack.isSameItemSameTags(slot, toGive) && slot.getCount() < slot.getMaxStackSize()) {
                    remaining -= (slot.getMaxStackSize() - slot.getCount());
                    if (remaining <= 0) break;
                }
            }
            if (remaining > 0) {
                slotsNeeded += (remaining + toGive.getMaxStackSize() - 1) / toGive.getMaxStackSize();
            }
        }

        return emptySlots >= slotsNeeded;
    }

    /**
     * Checks if an action button should be disabled.
     */
    public static boolean isActionDisabled(Minecraft mc, ChatAction action) {
        return !hasAllItems(mc, action.itemsInput()) || !hasSpaceFor(mc, action.itemsOutput());
    }
}
