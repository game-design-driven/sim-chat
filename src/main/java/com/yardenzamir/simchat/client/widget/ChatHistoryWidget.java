package com.yardenzamir.simchat.client.widget;

import com.yardenzamir.simchat.client.AvatarManager;
import com.yardenzamir.simchat.config.ClientConfig;
import com.yardenzamir.simchat.data.ChatAction;
import com.yardenzamir.simchat.data.ChatMessage;
import com.yardenzamir.simchat.network.ActionClickPacket;
import com.yardenzamir.simchat.network.NetworkHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Scrollable chat history displaying messages with avatars and action buttons.
 */
public class ChatHistoryWidget extends AbstractWidget {

    private static final int AVATAR_SIZE = 32;
    private static final int MESSAGE_PADDING = 8;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_PADDING = 4;
    private static final int ITEM_ICON_SIZE = 16;

    private static final int TYPING_INDICATOR_HEIGHT = 32;

    private final Minecraft minecraft;
    private final List<ChatMessage> messages = new ArrayList<>();
    private @Nullable String entityId;
    private @Nullable String typingEntityName;
    private @Nullable String typingEntityImageId;
    private boolean isTyping = false;

    private int scrollOffset = 0;
    private int contentHeight = 0;

    public ChatHistoryWidget(Minecraft minecraft, int width, int height, int x, int y) {
        super(x, y, width, height, Component.empty());
        this.minecraft = minecraft;
    }

    public void setMessages(List<ChatMessage> messages, String entityId) {
        this.messages.clear();
        this.messages.addAll(messages);
        this.entityId = entityId;
        recalculateContentHeight();
        // Scroll to bottom for new messages
        scrollToBottom();
    }

    public void clearMessages() {
        this.messages.clear();
        this.entityId = null;
        this.isTyping = false;
        this.typingEntityName = null;
        this.typingEntityImageId = null;
        this.scrollOffset = 0;
        this.contentHeight = 0;
    }

    public void setTyping(boolean typing, @Nullable String entityName, @Nullable String imageId) {
        this.isTyping = typing;
        this.typingEntityName = entityName;
        this.typingEntityImageId = imageId;
        recalculateContentHeight();
    }

    private void scrollToBottom() {
        scrollOffset = Math.max(0, contentHeight - height);
    }

    private void recalculateContentHeight() {
        int totalHeight = MESSAGE_PADDING;
        for (ChatMessage message : messages) {
            totalHeight += calculateMessageHeight(message) + MESSAGE_PADDING;
        }
        if (isTyping) {
            totalHeight += TYPING_INDICATOR_HEIGHT + MESSAGE_PADDING;
        }
        this.contentHeight = totalHeight;
    }

    private int calculateMessageHeight(ChatMessage message) {
        int textWidth = width - AVATAR_SIZE - MESSAGE_PADDING * 3;
        List<String> wrappedLines = wrapText(message.content(), textWidth);
        int textHeight = wrappedLines.size() * (minecraft.font.lineHeight + 2);

        int buttonRowHeight = 0;
        if (!message.actions().isEmpty()) {
            buttonRowHeight = BUTTON_HEIGHT + BUTTON_PADDING;
        }

        return Math.max(AVATAR_SIZE, minecraft.font.lineHeight + 2 + textHeight + buttonRowHeight) + MESSAGE_PADDING;
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            if (minecraft.font.width(testLine) > maxWidth) {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                } else {
                    // Word too long, force break
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

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Enable scissor for clipping
        graphics.enableScissor(getX(), getY(), getX() + width, getY() + height);

        int y = getY() + MESSAGE_PADDING - scrollOffset;

        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            int msgHeight = calculateMessageHeight(message);

            // Only render if visible
            if (y + msgHeight > getY() && y < getY() + height) {
                renderMessage(graphics, message, i, getX() + MESSAGE_PADDING, y, mouseX, mouseY);
            }

            y += msgHeight + MESSAGE_PADDING;
        }

        // Render typing indicator if active
        if (isTyping && typingEntityImageId != null) {
            if (y + TYPING_INDICATOR_HEIGHT > getY() && y < getY() + height) {
                renderTypingIndicator(graphics, getX() + MESSAGE_PADDING, y, partialTick);
            }
        }

        graphics.disableScissor();

        // Scrollbar
        if (contentHeight > height) {
            int scrollbarHeight = Math.max(20, (int) ((float) height / contentHeight * height));
            int scrollbarY = (int) ((float) scrollOffset / (contentHeight - height) * (height - scrollbarHeight));
            graphics.fill(getX() + width - 4, getY() + scrollbarY,
                    getX() + width - 1, getY() + scrollbarY + scrollbarHeight, 0x80FFFFFF);
        }
    }

    private void renderMessage(GuiGraphics graphics, ChatMessage message, int index, int x, int y,
                               int mouseX, int mouseY) {
        // Avatar
        if (message.isPlayerMessage()) {
            // Use player skin face
            ResourceLocation skinTexture = getPlayerSkinTexture();
            RenderSystem.enableBlend();
            // Render face (8x8 at UV 8,8 on 64x64 skin)
            graphics.blit(skinTexture, x, y, AVATAR_SIZE, AVATAR_SIZE,
                    8.0f, 8.0f, 8, 8, 64, 64);
            // Render hat overlay (8x8 at UV 40,8 on 64x64 skin)
            graphics.blit(skinTexture, x, y, AVATAR_SIZE, AVATAR_SIZE,
                    40.0f, 8.0f, 8, 8, 64, 64);
        } else {
            ResourceLocation avatarTexture = AvatarManager.getTexture(message.senderImageId());
            graphics.blit(avatarTexture, x, y, AVATAR_SIZE, AVATAR_SIZE, 0, 0, 256, 256, 256, 256);
        }

        int textX = x + AVATAR_SIZE + MESSAGE_PADDING;
        int textY = y;
        int textWidth = width - AVATAR_SIZE - MESSAGE_PADDING * 3;

        // Sender name with subtitle and timestamp
        int nameColor = message.isPlayerMessage() ? 0xFF88FF88 : 0xFF88AAFF;
        graphics.drawString(minecraft.font, message.senderName(), textX, textY, nameColor);
        int nextX = textX + minecraft.font.width(message.senderName());

        // Subtitle (for non-player messages)
        if (!message.isPlayerMessage() && message.senderSubtitle() != null) {
            String subtitle = " - " + message.senderSubtitle();
            graphics.drawString(minecraft.font, subtitle, nextX, textY, 0xFF888888);
            nextX += minecraft.font.width(subtitle);
        }

        // Day timestamp (grayed out)
        String dayText = "  Day " + message.worldDay();
        graphics.drawString(minecraft.font, dayText, nextX, textY, 0xFF666666);
        textY += minecraft.font.lineHeight + 2;

        // Message content with wrapping
        List<String> wrappedLines = wrapText(message.content(), textWidth);
        for (String line : wrappedLines) {
            graphics.drawString(minecraft.font, line, textX, textY, 0xFFE0E0E0);
            textY += minecraft.font.lineHeight + 2;
        }

        // Action buttons
        if (!message.actions().isEmpty()) {
            int buttonX = textX;
            int buttonY = textY + BUTTON_PADDING;

            for (ChatAction action : message.actions()) {
                int buttonWidth = calculateButtonWidth(action);

                // Check if mouse is hovering
                boolean hovered = mouseX >= buttonX && mouseX < buttonX + buttonWidth
                        && mouseY >= buttonY && mouseY < buttonY + BUTTON_HEIGHT;

                // Button background
                int bgColor = hovered ? 0xFF4060A0 : 0xFF304080;
                graphics.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + BUTTON_HEIGHT, bgColor);

                // Button border
                int borderColor = hovered ? 0xFF6080C0 : 0xFF405090;
                graphics.renderOutline(buttonX, buttonY, buttonWidth, BUTTON_HEIGHT, borderColor);

                // Render items first
                int itemsWidth = 0;
                if (!action.items().isEmpty()) {
                    int itemX = buttonX + 4;
                    for (ChatAction.ActionItem item : action.items()) {
                        ItemStack stack = item.toItemStack();
                        if (stack != null) {
                            graphics.renderItem(stack, itemX, buttonY + (BUTTON_HEIGHT - ITEM_ICON_SIZE) / 2);
                            // Render count if > 1
                            if (item.count() > 1) {
                                graphics.renderItemDecorations(minecraft.font, stack, itemX, buttonY + (BUTTON_HEIGHT - ITEM_ICON_SIZE) / 2);
                            }
                            itemX += ITEM_ICON_SIZE + 2;
                            itemsWidth += ITEM_ICON_SIZE + 2;
                        }
                    }
                }

                // Button text (after items)
                int labelX = buttonX + 8 + itemsWidth;
                int textYOffset = (BUTTON_HEIGHT - minecraft.font.lineHeight) / 2;
                graphics.drawString(minecraft.font, action.label(), labelX, buttonY + textYOffset, 0xFFFFFFFF);

                buttonX += buttonWidth + BUTTON_PADDING;
            }
        }
    }

    private int calculateButtonWidth(ChatAction action) {
        int width = minecraft.font.width(action.label()) + 16;

        // Add space for items
        if (!action.items().isEmpty()) {
            for (ChatAction.ActionItem item : action.items()) {
                ItemStack stack = item.toItemStack();
                if (stack != null) {
                    width += ITEM_ICON_SIZE + 2;
                }
            }
        }

        return width;
    }

    private void renderTypingIndicator(GuiGraphics graphics, int x, int y, float partialTick) {
        // Avatar
        ResourceLocation avatarTexture = AvatarManager.getTexture(typingEntityImageId);
        graphics.blit(avatarTexture, x, y, AVATAR_SIZE, AVATAR_SIZE, 0, 0, 256, 256, 256, 256);

        int textX = x + AVATAR_SIZE + MESSAGE_PADDING;
        int textY = y;

        // Entity name
        graphics.drawString(minecraft.font, typingEntityName != null ? typingEntityName : "...", textX, textY, 0xFF88AAFF);
        textY += minecraft.font.lineHeight + 2;

        // Animated typing dots
        long time = System.currentTimeMillis();
        int animSpeed = ClientConfig.TYPING_ANIMATION_SPEED.get();
        int dotCount = (int) ((time / animSpeed) % 4); // 0-3 dots, cycling
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < dotCount; i++) {
            dots.append(".");
        }
        graphics.drawString(minecraft.font, "typing" + dots, textX, textY, 0xFF888888);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isMouseOver(mouseX, mouseY)) {
            scrollOffset = Math.max(0, Math.min(contentHeight - height,
                    scrollOffset - (int) (delta * 20)));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY) || button != 0) {
            return false;
        }

        // Check if clicking on an action button
        int y = getY() + MESSAGE_PADDING - scrollOffset;

        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            int msgHeight = calculateMessageHeight(message);

            if (!message.actions().isEmpty()) {
                int textX = getX() + MESSAGE_PADDING + AVATAR_SIZE + MESSAGE_PADDING;
                int textWidth = width - AVATAR_SIZE - MESSAGE_PADDING * 3;
                List<String> wrappedLines = wrapText(message.content(), textWidth);
                int textY = y + minecraft.font.lineHeight + 2 + (wrappedLines.size() * (minecraft.font.lineHeight + 2));
                int buttonY = textY + BUTTON_PADDING;

                if (mouseY >= buttonY && mouseY < buttonY + BUTTON_HEIGHT) {
                    int buttonX = textX;
                    for (ChatAction action : message.actions()) {
                        int buttonWidth = calculateButtonWidth(action);

                        if (mouseX >= buttonX && mouseX < buttonX + buttonWidth) {
                            // Button clicked - send packet to server
                            if (entityId != null) {
                                NetworkHandler.CHANNEL.sendToServer(
                                        new ActionClickPacket(entityId, i, action.label())
                                );
                            }
                            return true;
                        }

                        buttonX += buttonWidth + BUTTON_PADDING;
                    }
                }
            }

            y += msgHeight + MESSAGE_PADDING;
        }

        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // Not implemented for now
    }

    private ResourceLocation getPlayerSkinTexture() {
        if (minecraft.player == null) {
            return DefaultPlayerSkin.getDefaultSkin();
        }

        PlayerInfo playerInfo = minecraft.getConnection() != null
                ? minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID())
                : null;

        if (playerInfo != null) {
            return playerInfo.getSkinLocation();
        }

        return DefaultPlayerSkin.getDefaultSkin(minecraft.player.getUUID());
    }
}
