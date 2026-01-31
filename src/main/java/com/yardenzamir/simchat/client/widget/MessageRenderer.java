package com.yardenzamir.simchat.client.widget;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.systems.RenderSystem;

import org.jetbrains.annotations.Nullable;

import com.yardenzamir.simchat.client.AvatarManager;
import com.yardenzamir.simchat.client.ClientTeamCache;
import com.yardenzamir.simchat.client.PlayerSkinCache;
import com.yardenzamir.simchat.client.RuntimeTemplateResolver;
import com.yardenzamir.simchat.config.ClientConfig;
import com.yardenzamir.simchat.data.ChatAction;
import com.yardenzamir.simchat.data.ChatMessage;
import com.yardenzamir.simchat.team.TeamData;

import static com.yardenzamir.simchat.client.widget.ChatHistoryConstants.*;

/**
 * Renders chat messages including avatars, text, and action buttons.
 */
public final class MessageRenderer {
    private MessageRenderer() {}

    /**
     * Info about an action in input mode.
     */
    public record ActiveInputInfo(
            int messageIndex,
            int actionIndex,
            String currentText,
            boolean isValid,
            boolean cursorVisible
    ) {}

    /**
     * Result of rendering, including input-related click targets.
     */
    public record RenderResult(
            boolean sendButtonClicked,
            Integer clickedActionIndex
    ) {
        public static final RenderResult NONE = new RenderResult(false, null);
    }

    /**
     * Renders a chat message.
     */
    public static RenderResult render(GuiGraphics graphics, Minecraft mc, ChatMessage message,
                              int index, int x, int y, int width,
                              int mouseX, int mouseY, boolean isHovered,
                              HoverState hoverState, ActiveInputInfo activeInput) {
        if (message.isSystemMessage()) {
            renderSystemMessage(graphics, mc, message, x, y, width, mouseX, mouseY, hoverState);
            return RenderResult.NONE;
        }

        // Avatar
        renderAvatar(graphics, mc, message, x, y);

        int textX = x + AVATAR_SIZE + MESSAGE_PADDING;
        int textY = y;
        int textWidth = width - AVATAR_SIZE - MESSAGE_PADDING * 3;

        // Sender name with subtitle
        String resolvedName = RuntimeTemplateResolver.resolveSenderName(message, RuntimeTemplateResolver.ResolutionPriority.HIGH);
        int nameColor = message.isPlayerMessage()
                ? getPlayerNameColor()
                : ClientConfig.getColor(ClientConfig.ENTITY_NAME_COLOR, DEFAULT_ENTITY_NAME_COLOR);
        graphics.drawString(mc.font, resolvedName, textX, textY, nameColor);
        int nextX = textX + mc.font.width(resolvedName);

        // Show subtitle (entity subtitle or team title for player messages)
        String resolvedSubtitle = RuntimeTemplateResolver.resolveSenderSubtitle(message, RuntimeTemplateResolver.ResolutionPriority.HIGH);
        if (resolvedSubtitle != null && !resolvedSubtitle.isEmpty()) {
            String subtitle = " - " + resolvedSubtitle;
            graphics.drawString(mc.font, subtitle, nextX, textY,
                    ClientConfig.getColor(ClientConfig.SUBTITLE_COLOR, DEFAULT_SUBTITLE_COLOR));
            nextX += mc.font.width(subtitle);
        }

        // Show day on hover
        if (isHovered) {
            String dayText = "  " + Component.translatable("simchat.chat.day", message.worldDay()).getString();
            graphics.drawString(mc.font, dayText, nextX, textY,
                    ClientConfig.getColor(ClientConfig.DAY_TEXT_COLOR, DEFAULT_DAY_TEXT_COLOR));
        }
        textY += mc.font.lineHeight + 2;

        // Message content
        String resolvedContent = RuntimeTemplateResolver.resolveContent(message, RuntimeTemplateResolver.ResolutionPriority.HIGH);
        List<String> wrappedLines = wrapText(mc, resolvedContent, textWidth);
        int messageTextColor = ClientConfig.getColor(ClientConfig.MESSAGE_TEXT_COLOR, DEFAULT_MESSAGE_TEXT_COLOR);
        for (String line : wrappedLines) {
            graphics.drawString(mc.font, line, textX, textY, messageTextColor);
            textY += mc.font.lineHeight + 2;
        }

        // Action buttons (with wrapping)
        RenderResult result = RenderResult.NONE;
        if (!message.actions().isEmpty()) {
            int buttonX = textX;
            int buttonY = textY + BUTTON_PADDING;
            int maxButtonX = textX + textWidth;

            for (int actionIndex = 0; actionIndex < message.actions().size(); actionIndex++) {
                ChatAction action = message.actions().get(actionIndex);
                String label = RuntimeTemplateResolver.resolveActionLabel(message, actionIndex, action, RuntimeTemplateResolver.ResolutionPriority.HIGH);

                // Check if this action is in input mode
                boolean isActiveInput = activeInput != null
                        && activeInput.messageIndex() == index
                        && activeInput.actionIndex() == actionIndex;

                int buttonWidth;
                if (isActiveInput && action.playerInput() != null) {
                    buttonWidth = ActionButtonRenderer.calculateInputModeWidth(mc, action.playerInput().maxLength());
                } else {
                    buttonWidth = ActionButtonRenderer.calculateWidth(mc, action, label);
                }

                // Wrap to next row if button doesn't fit
                if (buttonX + buttonWidth > maxButtonX && buttonX > textX) {
                    buttonX = textX;
                    buttonY += BUTTON_HEIGHT + BUTTON_PADDING;
                }

                if (isActiveInput && action.playerInput() != null) {
                    // Render input field instead of button
                    var inputResult = ActionButtonRenderer.renderInputMode(graphics, mc, action,
                            buttonX, buttonY, maxButtonX - buttonX,
                            activeInput.currentText(), activeInput.isValid(), activeInput.cursorVisible(),
                            mouseX, mouseY, hoverState);
                    if (inputResult.sendButtonHovered()) {
                        result = new RenderResult(true, actionIndex);
                    }
                } else {
                    ActionButtonRenderer.render(graphics, mc, action, label, buttonX, buttonY,
                            mouseX, mouseY, hoverState);
                }
                buttonX += buttonWidth + BUTTON_PADDING;
            }
        }
        return result;
    }

    /**
     * Renders a system message (transactions, etc).
     */
    public static void renderSystemMessage(GuiGraphics graphics, Minecraft mc,
                                           ChatMessage message, int x, int y, int width,
                                           int mouseX, int mouseY, HoverState hoverState) {
        int contentWidth = width - MESSAGE_PADDING * 2;
        int centerY = y + (SYSTEM_MESSAGE_HEIGHT - ITEM_ICON_SIZE) / 2;

        boolean hasTransaction = !message.transactionInput().isEmpty() || !message.transactionOutput().isEmpty();

        if (hasTransaction) {
            String arrowText = Component.translatable("simchat.transaction.arrow").getString();
            String soldText = Component.translatable("simchat.transaction.sold").getString() + " ";
            String receivedText = Component.translatable("simchat.transaction.received").getString() + " ";

            int inputWidth = calculateTransactionItemsWidth(message.transactionInput());
            int outputWidth = calculateTransactionItemsWidth(message.transactionOutput());
            int arrowWidth = mc.font.width(arrowText);

            int totalWidth = 0;
            if (inputWidth > 0 && outputWidth > 0) {
                totalWidth = inputWidth + arrowWidth + outputWidth;
            } else if (inputWidth > 0) {
                totalWidth = mc.font.width(soldText) + inputWidth;
            } else if (outputWidth > 0) {
                totalWidth = mc.font.width(receivedText) + outputWidth;
            }

            int startX = x + (contentWidth - totalWidth) / 2;
            int currentX = startX;
            int itemY = centerY;
            int textY = y + (SYSTEM_MESSAGE_HEIGHT - mc.font.lineHeight) / 2;
            int systemTextColor = ClientConfig.getColor(ClientConfig.SYSTEM_TEXT_COLOR, DEFAULT_SYSTEM_TEXT_COLOR);

            if (inputWidth > 0 && outputWidth > 0) {
                currentX = renderTransactionItems(graphics, mc, message.transactionInput(),
                        currentX, itemY, ClientConfig.getColor(ClientConfig.INPUT_BG_COLOR, DEFAULT_INPUT_BG_COLOR),
                        mouseX, mouseY, hoverState);
                graphics.drawString(mc.font, arrowText, currentX, textY, systemTextColor);
                currentX += arrowWidth;
                renderTransactionItems(graphics, mc, message.transactionOutput(),
                        currentX, itemY, ClientConfig.getColor(ClientConfig.OUTPUT_BG_COLOR, DEFAULT_OUTPUT_BG_COLOR),
                        mouseX, mouseY, hoverState);
            } else if (inputWidth > 0) {
                graphics.drawString(mc.font, soldText, currentX, textY, systemTextColor);
                currentX += mc.font.width(soldText);
                renderTransactionItems(graphics, mc, message.transactionInput(),
                        currentX, itemY, ClientConfig.getColor(ClientConfig.INPUT_BG_COLOR, DEFAULT_INPUT_BG_COLOR),
                        mouseX, mouseY, hoverState);
            } else {
                graphics.drawString(mc.font, receivedText, currentX, textY, systemTextColor);
                currentX += mc.font.width(receivedText);
                renderTransactionItems(graphics, mc, message.transactionOutput(),
                        currentX, itemY, ClientConfig.getColor(ClientConfig.OUTPUT_BG_COLOR, DEFAULT_OUTPUT_BG_COLOR),
                        mouseX, mouseY, hoverState);
            }
        } else {
            String resolvedContent = RuntimeTemplateResolver.resolveContent(message, RuntimeTemplateResolver.ResolutionPriority.HIGH);
            if (!resolvedContent.isEmpty()) {

                int textWidth = mc.font.width(resolvedContent);
                int textX = x + (contentWidth - textWidth) / 2;
                int textY = y + (SYSTEM_MESSAGE_HEIGHT - mc.font.lineHeight) / 2;
                graphics.drawString(mc.font, resolvedContent, textX, textY,
                        ClientConfig.getColor(ClientConfig.SYSTEM_TEXT_COLOR, DEFAULT_SYSTEM_TEXT_COLOR));
            }
        }
    }

    /**
     * Renders the typing indicator.
     */
    public static void renderTypingIndicator(GuiGraphics graphics, Minecraft mc,
                                             @Nullable String entityName, String imageId,
                                             int x, int y) {
        ResourceLocation avatarTexture = AvatarManager.getTexture(imageId);
        graphics.blit(avatarTexture, x, y, AVATAR_SIZE, AVATAR_SIZE, 0, 0, 256, 256, 256, 256);

        int textX = x + AVATAR_SIZE + MESSAGE_PADDING;
        int textY = y;

        graphics.drawString(mc.font, entityName != null ? entityName : "...", textX, textY,
                ClientConfig.getColor(ClientConfig.ENTITY_NAME_COLOR, DEFAULT_ENTITY_NAME_COLOR));
        textY += mc.font.lineHeight + 2;

        long time = System.currentTimeMillis();
        int animSpeed = ClientConfig.TYPING_ANIMATION_SPEED.get();
        int dotCount = (int) ((time / animSpeed) % 4);
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < dotCount; i++) {
            dots.append(".");
        }
        String typingText = Component.translatable("simchat.chat.typing").getString() + dots;
        graphics.drawString(mc.font, typingText, textX, textY,
                ClientConfig.getColor(ClientConfig.SYSTEM_TEXT_COLOR, DEFAULT_SYSTEM_TEXT_COLOR));
    }

    /**
     * Calculates the height of a message.
     */
    public static int calculateHeight(Minecraft mc, ChatMessage message, int width) {
        if (message.isSystemMessage()) {
            return SYSTEM_MESSAGE_HEIGHT;
        }

        int textWidth = width - AVATAR_SIZE - MESSAGE_PADDING * 3;
        String resolvedContent = RuntimeTemplateResolver.resolveContent(message, RuntimeTemplateResolver.ResolutionPriority.HIGH);
        List<String> wrappedLines = wrapText(mc, resolvedContent, textWidth);
        int textHeight = wrappedLines.size() * (mc.font.lineHeight + 2);

        int buttonRowHeight = 0;
        if (!message.actions().isEmpty()) {
            int buttonRows = calculateButtonRows(mc, message, textWidth);
            buttonRowHeight = buttonRows * (BUTTON_HEIGHT + BUTTON_PADDING);
        }

        return Math.max(AVATAR_SIZE, mc.font.lineHeight + 2 + textHeight + buttonRowHeight) + MESSAGE_PADDING;
    }

    /**
     * Calculates number of rows needed for action buttons given available width.
     */
    public static int calculateButtonRows(Minecraft mc, ChatMessage message, int maxWidth) {
        if (message.actions().isEmpty()) return 0;

        int rows = 1;
        int currentX = 0;

        for (int actionIndex = 0; actionIndex < message.actions().size(); actionIndex++) {
            ChatAction action = message.actions().get(actionIndex);
            String label = RuntimeTemplateResolver.resolveActionLabel(message, actionIndex, action, RuntimeTemplateResolver.ResolutionPriority.HIGH);
            int buttonWidth = ActionButtonRenderer.calculateWidth(mc, action, label);

            if (currentX + buttonWidth > maxWidth && currentX > 0) {
                rows++;
                currentX = 0;
            }

            currentX += buttonWidth + BUTTON_PADDING;
        }

        return rows;
    }

    /**
     * Wraps text to fit within maxWidth.
     */
    public static List<String> wrapText(Minecraft mc, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            if (mc.font.width(testLine) > maxWidth) {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                } else {
                    lines.add(word);
                }
            } else {
                currentLine = new StringBuilder(testLine);
            }
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines;
    }

    private static int getPlayerNameColor() {
        TeamData team = ClientTeamCache.getTeam();
        if (team == null) {
            return TeamData.getVanillaColorValue(15);
        }

        return team.getVanillaColorValue();
    }

    private static void renderAvatar(GuiGraphics graphics, Minecraft mc, ChatMessage message, int x, int y) {
        if (message.isPlayerMessage()) {
            ResourceLocation skinTexture = PlayerSkinCache.getSkin(message.playerUuid());
            // Render player head from skin texture
            // Face layer: 8x8 pixels at UV (8,8) in 64x64 texture
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            graphics.blit(skinTexture, x, y, AVATAR_SIZE, AVATAR_SIZE, 8, 8, 8, 8, 64, 64);
            // Hat layer: 8x8 pixels at UV (40,8) - overlay with transparency
            graphics.blit(skinTexture, x, y, AVATAR_SIZE, AVATAR_SIZE, 40, 8, 8, 8, 64, 64);
            RenderSystem.disableBlend();
        } else {
            ResourceLocation avatarTexture = AvatarManager.getTexture(message.senderImageId());
            graphics.blit(avatarTexture, x, y, AVATAR_SIZE, AVATAR_SIZE, 0, 0, 256, 256, 256, 256);
        }
    }

    private static int calculateTransactionItemsWidth(List<ChatAction.ActionItem> items) {
        int width = 0;
        for (ChatAction.ActionItem item : items) {
            if (item.toItemStack() != null) {
                width += ITEM_ICON_SIZE + ITEM_SPACING;
            }
        }
        return width > 0 ? width - ITEM_SPACING : 0;
    }

    private static int renderTransactionItems(GuiGraphics graphics, Minecraft mc,
                                              List<ChatAction.ActionItem> items,
                                              int startX, int itemY, int bgColor,
                                              int mouseX, int mouseY, HoverState hoverState) {
        int currentX = startX;
        for (ChatAction.ActionItem item : items) {
            ItemStack stack = item.toItemStack();
            if (stack != null) {
                int bgX = currentX - ITEM_BG_PADDING;
                int bgY = itemY - ITEM_BG_PADDING;
                int bgSize = ITEM_ICON_SIZE + ITEM_BG_PADDING * 2;
                graphics.fill(bgX, bgY, bgX + bgSize, bgY + bgSize, bgColor);

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
}
