package com.yardenzamir.simchat.client.widget;

import com.yardenzamir.simchat.data.ChatAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.util.List;

import static com.yardenzamir.simchat.client.widget.ChatHistoryConstants.*;

/**
 * Renders action buttons with items in the chat history.
 */
public final class ActionButtonRenderer {
    private ActionButtonRenderer() {}

    /**
     * Renders an action button.
     * @return true if hovered
     */
    public static boolean render(GuiGraphics graphics, Minecraft mc, ChatAction action,
                                 int buttonX, int buttonY, int mouseX, int mouseY,
                                 HoverState hoverState) {
        int buttonWidth = calculateWidth(mc, action);
        boolean hovered = mouseX >= buttonX && mouseX < buttonX + buttonWidth
                && mouseY >= buttonY && mouseY < buttonY + BUTTON_HEIGHT;

        boolean hasRequiredItems = InventoryHelper.hasAllItems(mc, action.itemsInput());
        boolean hasInventorySpace = InventoryHelper.hasSpaceFor(mc, action.itemsOutput());
        boolean isDisabled = !hasRequiredItems || !hasInventorySpace;

        int bgColor = determineBackgroundColor(action, hovered, isDisabled);
        graphics.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + BUTTON_HEIGHT, bgColor);

        int borderColor = isDisabled ? BORDER_DISABLED_COLOR
                : (hovered ? BORDER_HOVER_COLOR : BORDER_DEFAULT_COLOR);

        int currentX = buttonX + BUTTON_INTERNAL_PADDING;
        int itemY = buttonY + (BUTTON_HEIGHT - ITEM_ICON_SIZE) / 2;

        // Render INPUT items (blue background)
        currentX = renderItems(graphics, mc, action.itemsInput(), currentX, itemY, buttonY,
                INPUT_BG_COLOR, mouseX, mouseY, isDisabled, hoverState);

        // Render VISUAL items (no background)
        currentX = renderItems(graphics, mc, action.itemsVisual(), currentX, itemY, buttonY,
                0, mouseX, mouseY, isDisabled, hoverState);

        // Button label
        int labelX = currentX + BUTTON_INTERNAL_PADDING;
        int textYOffset = (BUTTON_HEIGHT - mc.font.lineHeight) / 2;
        int textColor = isDisabled ? DISABLED_TEXT_COLOR : WHITE_TEXT_COLOR;
        graphics.drawString(mc.font, action.label(), labelX, buttonY + textYOffset, textColor);
        currentX = labelX + mc.font.width(action.label()) + BUTTON_INTERNAL_PADDING;

        // Render OUTPUT items (orange background)
        renderItems(graphics, mc, action.itemsOutput(), currentX, itemY, buttonY,
                OUTPUT_BG_COLOR, mouseX, mouseY, isDisabled, hoverState);

        graphics.renderOutline(buttonX, buttonY, buttonWidth, BUTTON_HEIGHT, borderColor);

        // Set disabled tooltip
        if (isDisabled && hovered) {
            if (!hasRequiredItems) {
                hoverState.setDisabledTooltip("simchat.tooltip.missing_items", mouseX, mouseY);
            } else {
                hoverState.setDisabledTooltip("simchat.tooltip.no_inventory_space", mouseX, mouseY);
            }
        }

        return hovered;
    }

    private static int determineBackgroundColor(ChatAction action, boolean hovered, boolean isDisabled) {
        boolean hasOnlyInput = !action.itemsInput().isEmpty()
                && action.itemsOutput().isEmpty()
                && action.itemsVisual().isEmpty();
        boolean hasOnlyOutput = action.itemsInput().isEmpty()
                && !action.itemsOutput().isEmpty()
                && action.itemsVisual().isEmpty();

        if (isDisabled) {
            return DISABLED_BG_COLOR;
        } else if (hasOnlyInput) {
            return hovered ? brighten(INPUT_BG_COLOR) : INPUT_BG_COLOR;
        } else if (hasOnlyOutput) {
            return hovered ? brighten(OUTPUT_BG_COLOR) : OUTPUT_BG_COLOR;
        } else {
            return hovered ? BUTTON_HOVER_COLOR : BUTTON_DEFAULT_COLOR;
        }
    }

    private static int renderItems(GuiGraphics graphics, Minecraft mc,
                                   List<ChatAction.ActionItem> items,
                                   int startX, int itemY, int buttonY, int bgColor,
                                   int mouseX, int mouseY, boolean isDisabled,
                                   HoverState hoverState) {
        if (items.isEmpty()) {
            return startX;
        }

        int currentX = startX;
        for (ChatAction.ActionItem item : items) {
            ItemStack stack = item.toItemStack();
            if (stack != null) {
                if (bgColor != 0 && !isDisabled) {
                    int bgX = currentX - ITEM_BG_PADDING;
                    int bgY = itemY - ITEM_BG_PADDING;
                    int bgSize = ITEM_ICON_SIZE + ITEM_BG_PADDING * 2;
                    graphics.fill(bgX, bgY, bgX + bgSize, bgY + bgSize, bgColor);
                }

                graphics.renderItem(stack, currentX, itemY);
                if (item.count() > 1) {
                    graphics.renderItemDecorations(mc.font, stack, currentX, itemY);
                }

                if (mouseX >= currentX && mouseX < currentX + ITEM_ICON_SIZE
                        && mouseY >= itemY && mouseY < itemY + ITEM_ICON_SIZE) {
                    hoverState.setHoveredItem(stack, mouseX, mouseY);
                }

                currentX += ITEM_ICON_SIZE + ITEM_SPACING;
            }
        }
        return currentX;
    }

    /**
     * Calculates the total width of an action button.
     */
    public static int calculateWidth(Minecraft mc, ChatAction action) {
        int width = mc.font.width(action.label()) + BUTTON_BASE_WIDTH_PADDING;
        width += calculateItemsWidth(action.itemsInput());
        width += calculateItemsWidth(action.itemsVisual());
        width += calculateItemsWidth(action.itemsOutput());
        return width;
    }

    private static int calculateItemsWidth(List<ChatAction.ActionItem> items) {
        int width = 0;
        for (ChatAction.ActionItem item : items) {
            ItemStack stack = item.toItemStack();
            if (stack != null) {
                width += ITEM_ICON_SIZE + ITEM_SPACING;
            }
        }
        return width;
    }

    public static int brighten(int color) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, ((color >> 16) & 0xFF) + BRIGHTEN_AMOUNT);
        int g = Math.min(255, ((color >> 8) & 0xFF) + BRIGHTEN_AMOUNT);
        int b = Math.min(255, (color & 0xFF) + BRIGHTEN_AMOUNT);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
