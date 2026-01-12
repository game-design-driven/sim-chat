package com.yardenzamir.simchat.client.widget;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Tracks hover state during chat history rendering for tooltips.
 */
public class HoverState {
    private @Nullable ItemStack hoveredItem = null;
    private int hoveredItemX = 0;
    private int hoveredItemY = 0;
    private @Nullable String disabledTooltipKey = null;
    private int tooltipX = 0;
    private int tooltipY = 0;
    private int hoveredMessageIndex = -1;

    public void reset() {
        hoveredItem = null;
        disabledTooltipKey = null;
        hoveredMessageIndex = -1;
    }

    public void setHoveredItem(ItemStack item, int x, int y) {
        this.hoveredItem = item;
        this.hoveredItemX = x;
        this.hoveredItemY = y;
    }

    public void setDisabledTooltip(String translationKey, int x, int y) {
        this.disabledTooltipKey = translationKey;
        this.tooltipX = x;
        this.tooltipY = y;
    }

    public void setHoveredMessageIndex(int index) {
        this.hoveredMessageIndex = index;
    }

    public @Nullable ItemStack getHoveredItem() {
        return hoveredItem;
    }

    public int getHoveredItemX() {
        return hoveredItemX;
    }

    public int getHoveredItemY() {
        return hoveredItemY;
    }

    public @Nullable String getDisabledTooltipKey() {
        return disabledTooltipKey;
    }

    public @Nullable Component getDisabledTooltip() {
        return disabledTooltipKey != null ? Component.translatable(disabledTooltipKey) : null;
    }

    public int getTooltipX() {
        return tooltipX;
    }

    public int getTooltipY() {
        return tooltipY;
    }

    public int getHoveredMessageIndex() {
        return hoveredMessageIndex;
    }

    public boolean hasItemTooltip() {
        return hoveredItem != null;
    }

    public boolean hasDisabledTooltip() {
        return disabledTooltipKey != null;
    }
}
