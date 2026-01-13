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
    private @Nullable Component disabledTooltip = null;
    private int tooltipX = 0;
    private int tooltipY = 0;
    private int hoveredMessageIndex = -1;

    public void reset() {
        hoveredItem = null;
        disabledTooltip = null;
        hoveredMessageIndex = -1;
    }

    public void setHoveredItem(ItemStack item, int x, int y) {
        this.hoveredItem = item;
        this.hoveredItemX = x;
        this.hoveredItemY = y;
    }

    /**
     * Sets a disabled tooltip using a translation key.
     */
    public void setDisabledTooltip(String translationKey, int x, int y) {
        this.disabledTooltip = Component.translatable(translationKey);
        this.tooltipX = x;
        this.tooltipY = y;
    }

    /**
     * Sets a disabled tooltip using a literal string (not translated).
     */
    public void setDisabledTooltipLiteral(String text, int x, int y) {
        this.disabledTooltip = Component.literal(text);
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

    public @Nullable Component getDisabledTooltip() {
        return disabledTooltip;
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
        return disabledTooltip != null;
    }
}
