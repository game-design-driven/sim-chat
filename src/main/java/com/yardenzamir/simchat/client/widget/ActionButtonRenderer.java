package com.yardenzamir.simchat.client.widget;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import com.yardenzamir.simchat.data.ChatAction;

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
    public static boolean render(GuiGraphics graphics, Minecraft mc, ChatAction action, String label,
                                 int buttonX, int buttonY, int mouseX, int mouseY,
                                 HoverState hoverState) {
        int buttonWidth = calculateWidth(mc, action, label);
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
        graphics.drawString(mc.font, label, labelX, buttonY + textYOffset, textColor);
        currentX = labelX + mc.font.width(label) + BUTTON_INTERNAL_PADDING;

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
    public static int calculateWidth(Minecraft mc, ChatAction action, String label) {
        int width = mc.font.width(label) + BUTTON_BASE_WIDTH_PADDING;
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

    /**
     * Result of rendering an input field.
     */
    public record InputRenderResult(
            boolean sendButtonHovered,
            boolean fieldHovered,
            int totalWidth
    ) {}

    /**
     * Renders an action in input mode (text field + send button).
     *
     * @param currentText Current text in the input field
     * @param isValid Whether the current text passes validation
     * @param cursorVisible Whether to show the cursor (for blinking)
     * @return Render result with hover states and dimensions
     */
    public static InputRenderResult renderInputMode(GuiGraphics graphics, Minecraft mc, ChatAction action,
                                                     int x, int y, int maxWidth,
                                                     String currentText, boolean isValid, boolean cursorVisible,
                                                     int mouseX, int mouseY, HoverState hoverState) {
        ChatAction.PlayerInputConfig config = action.playerInput();
        if (config == null) {
            return new InputRenderResult(false, false, 0);
        }

        // Calculate dimensions
        int sendButtonWidth = INPUT_SEND_BUTTON_WIDTH;
        int availableWidth = maxWidth - sendButtonWidth - BUTTON_PADDING;
        int fieldWidth = Math.max(INPUT_FIELD_MIN_WIDTH, Math.min(availableWidth, calculateInputFieldWidth(mc, currentText, config.maxLength())));
        int totalWidth = fieldWidth + BUTTON_PADDING + sendButtonWidth;

        // Field bounds
        int fieldX = x;
        int fieldY = y;
        int fieldHeight = INPUT_FIELD_HEIGHT;

        // Send button bounds
        int sendX = fieldX + fieldWidth + BUTTON_PADDING;
        int sendY = y;

        // Check hover states
        boolean fieldHovered = mouseX >= fieldX && mouseX < fieldX + fieldWidth
                && mouseY >= fieldY && mouseY < fieldY + fieldHeight;
        boolean sendHovered = mouseX >= sendX && mouseX < sendX + sendButtonWidth
                && mouseY >= sendY && mouseY < sendY + BUTTON_HEIGHT;

        // Determine if send is enabled (non-empty and valid)
        boolean canSend = !currentText.isEmpty() && isValid;

        // Render text field background
        graphics.fill(fieldX, fieldY, fieldX + fieldWidth, fieldY + fieldHeight, INPUT_FIELD_BG_COLOR);
        int borderColor = fieldHovered ? INPUT_FIELD_BORDER_FOCUSED_COLOR : INPUT_FIELD_BORDER_COLOR;
        graphics.renderOutline(fieldX, fieldY, fieldWidth, fieldHeight, borderColor);

        // Render text with validation color
        int textColor = currentText.isEmpty() ? SUBTITLE_COLOR : (isValid ? INPUT_VALID_COLOR : INPUT_INVALID_COLOR);
        int textX = fieldX + INPUT_FIELD_PADDING;
        int textY = fieldY + (fieldHeight - mc.font.lineHeight) / 2;

        // Truncate display text if too wide
        String displayText = currentText;
        int maxTextWidth = fieldWidth - INPUT_FIELD_PADDING * 2 - (cursorVisible ? mc.font.width("_") : 0);
        while (!displayText.isEmpty() && mc.font.width(displayText) > maxTextWidth) {
            displayText = displayText.substring(1);
        }
        graphics.drawString(mc.font, displayText, textX, textY, textColor);

        // Render cursor
        if (cursorVisible) {
            int cursorX = textX + mc.font.width(displayText);
            graphics.drawString(mc.font, "_", cursorX, textY, INPUT_CURSOR_COLOR);
        }

        // Render send button
        int sendBgColor = canSend ? (sendHovered ? brighten(INPUT_SEND_ENABLED_COLOR) : INPUT_SEND_ENABLED_COLOR)
                                  : INPUT_SEND_DISABLED_COLOR;
        graphics.fill(sendX, sendY, sendX + sendButtonWidth, sendY + BUTTON_HEIGHT, sendBgColor);
        int sendBorderColor = canSend ? (sendHovered ? BORDER_HOVER_COLOR : BORDER_DEFAULT_COLOR) : BORDER_DISABLED_COLOR;
        graphics.renderOutline(sendX, sendY, sendButtonWidth, BUTTON_HEIGHT, sendBorderColor);

        // Send button text
        String sendLabel = Component.translatable("simchat.input.send").getString();
        int sendTextColor = canSend ? WHITE_TEXT_COLOR : DISABLED_TEXT_COLOR;
        int sendTextX = sendX + (sendButtonWidth - mc.font.width(sendLabel)) / 2;
        int sendTextY = sendY + (BUTTON_HEIGHT - mc.font.lineHeight) / 2;
        graphics.drawString(mc.font, sendLabel, sendTextX, sendTextY, sendTextColor);

        // Show error tooltip if invalid and has error message
        if (!isValid && !currentText.isEmpty() && config.error() != null && fieldHovered) {
            hoverState.setDisabledTooltipLiteral(config.error(), mouseX, mouseY);
        }

        return new InputRenderResult(sendHovered && canSend, fieldHovered, totalWidth);
    }

    /**
     * Calculates the width needed for an input field.
     */
    public static int calculateInputFieldWidth(Minecraft mc, String currentText, int maxLength) {
        // Base width on current text or placeholder width, with some padding for typing
        int textWidth = mc.font.width(currentText.isEmpty() ? "A".repeat(Math.min(20, maxLength)) : currentText);
        return Math.max(INPUT_FIELD_MIN_WIDTH, textWidth + INPUT_FIELD_PADDING * 2 + mc.font.width("__"));
    }

    /**
     * Calculates total width of input mode (field + send button).
     */
    public static int calculateInputModeWidth(Minecraft mc, int maxLength) {
        return calculateInputFieldWidth(mc, "", maxLength) + BUTTON_PADDING + INPUT_SEND_BUTTON_WIDTH;
    }
}
