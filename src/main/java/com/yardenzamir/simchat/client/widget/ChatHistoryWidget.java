package com.yardenzamir.simchat.client.widget;

import com.yardenzamir.simchat.data.ChatAction;
import com.yardenzamir.simchat.data.ChatMessage;
import com.yardenzamir.simchat.network.ActionClickPacket;
import com.yardenzamir.simchat.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.yardenzamir.simchat.client.widget.ChatHistoryConstants.*;

/**
 * Scrollable chat history displaying messages with avatars and action buttons.
 */
public class ChatHistoryWidget extends AbstractWidget {

    private final Minecraft minecraft;
    private final List<ChatMessage> messages = new ArrayList<>();
    private final HoverState hoverState = new HoverState();

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
        setMessages(messages, entityId, messages.size());
    }

    public void setMessages(List<ChatMessage> messages, String entityId, int readCount) {
        this.messages.clear();
        this.messages.addAll(messages);
        this.entityId = entityId;
        recalculateContentHeight();

        if (readCount < messages.size()) {
            scrollToMessage(readCount);
        } else {
            scrollToBottom();
        }
    }

    public void updateMessages(List<ChatMessage> messages, String entityId) {
        int oldCount = this.messages.size();
        int oldScrollOffset = this.scrollOffset;

        this.messages.clear();
        this.messages.addAll(messages);
        this.entityId = entityId;
        recalculateContentHeight();

        if (messages.size() > oldCount) {
            scrollToBottom();
        } else {
            this.scrollOffset = clampScrollOffset(oldScrollOffset);
        }
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
        boolean wasTyping = this.isTyping;
        this.isTyping = typing;
        this.typingEntityName = entityName;
        this.typingEntityImageId = imageId;
        recalculateContentHeight();

        if (typing && !wasTyping) {
            scrollToBottom();
        }
    }

    private void scrollToMessage(int messageIndex) {
        if (messageIndex < 0 || messageIndex >= messages.size()) {
            scrollToBottom();
            return;
        }

        int y = MESSAGE_PADDING;
        for (int i = 0; i < messageIndex; i++) {
            y += MessageRenderer.calculateHeight(minecraft, messages.get(i), width) + MESSAGE_PADDING;
        }

        scrollOffset = clampScrollOffset(y - height / 4);
    }

    private void scrollToBottom() {
        scrollOffset = Math.max(0, contentHeight - height);
    }

    private int clampScrollOffset(int offset) {
        return Math.max(0, Math.min(contentHeight - height, offset));
    }

    private void recalculateContentHeight() {
        int totalHeight = MESSAGE_PADDING;
        for (ChatMessage message : messages) {
            totalHeight += MessageRenderer.calculateHeight(minecraft, message, width) + MESSAGE_PADDING;
        }
        if (isTyping) {
            totalHeight += TYPING_INDICATOR_HEIGHT + MESSAGE_PADDING;
        }
        // Remove trailing padding so bottom content aligns with widget edge
        if (!messages.isEmpty() || isTyping) {
            totalHeight -= MESSAGE_PADDING;
        }
        this.contentHeight = totalHeight;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hoverState.reset();

        graphics.enableScissor(getX(), getY(), getX() + width, getY() + height);

        // Copy to avoid ConcurrentModificationException from network thread updates
        List<ChatMessage> snapshot = List.copyOf(messages);

        int y = getY() + MESSAGE_PADDING - scrollOffset;

        for (int i = 0; i < snapshot.size(); i++) {
            ChatMessage message = snapshot.get(i);
            int msgHeight = MessageRenderer.calculateHeight(minecraft, message, width);

            boolean isHovered = mouseX >= getX() && mouseX < getX() + width
                    && mouseY >= y && mouseY < y + msgHeight;
            if (isHovered) {
                hoverState.setHoveredMessageIndex(i);
            }

            if (y + msgHeight > getY() && y < getY() + height) {
                MessageRenderer.render(graphics, minecraft, message, i,
                        getX() + MESSAGE_PADDING, y, width,
                        mouseX, mouseY, isHovered, hoverState);
            }

            y += msgHeight + MESSAGE_PADDING;
        }

        if (isTyping && typingEntityImageId != null) {
            if (y + TYPING_INDICATOR_HEIGHT > getY() && y < getY() + height) {
                MessageRenderer.renderTypingIndicator(graphics, minecraft,
                        typingEntityName, typingEntityImageId,
                        getX() + MESSAGE_PADDING, y);
            }
        }

        graphics.disableScissor();

        renderScrollbar(graphics);
        renderTooltips(graphics, mouseX, mouseY);
    }

    private void renderScrollbar(GuiGraphics graphics) {
        if (contentHeight > height) {
            int scrollbarHeight = Math.max(SCROLLBAR_MIN_HEIGHT, (int) ((float) height / contentHeight * height));
            int scrollbarY = (int) ((float) scrollOffset / (contentHeight - height) * (height - scrollbarHeight));
            graphics.fill(
                    getX() + width - SCROLLBAR_WIDTH - 1,
                    getY() + scrollbarY,
                    getX() + width - 1,
                    getY() + scrollbarY + scrollbarHeight,
                    SCROLLBAR_COLOR
            );
        }
    }

    private void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoverState.hasItemTooltip()) {
            graphics.renderTooltip(minecraft.font, hoverState.getHoveredItem(),
                    hoverState.getHoveredItemX(), hoverState.getHoveredItemY());
        } else if (hoverState.hasDisabledTooltip()) {
            graphics.renderTooltip(minecraft.font, hoverState.getDisabledTooltip(),
                    hoverState.getTooltipX(), hoverState.getTooltipY());
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isMouseOver(mouseX, mouseY)) {
            scrollOffset = clampScrollOffset(scrollOffset - (int) (delta * SCROLL_SPEED));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY) || button != 0) {
            return false;
        }

        // Copy to avoid ConcurrentModificationException from network thread updates
        List<ChatMessage> snapshot = List.copyOf(messages);

        int y = getY() + MESSAGE_PADDING - scrollOffset;

        for (int i = 0; i < snapshot.size(); i++) {
            ChatMessage message = snapshot.get(i);
            int msgHeight = MessageRenderer.calculateHeight(minecraft, message, width);

            if (!message.actions().isEmpty()) {
                int textX = getX() + MESSAGE_PADDING + AVATAR_SIZE + MESSAGE_PADDING;
                int textWidth = width - AVATAR_SIZE - MESSAGE_PADDING * 3;
                List<String> wrappedLines = MessageRenderer.wrapText(minecraft, message.content(), textWidth);
                int textY = y + minecraft.font.lineHeight + 2 + (wrappedLines.size() * (minecraft.font.lineHeight + 2));
                int buttonStartY = textY + BUTTON_PADDING;

                // Calculate total button area height
                int buttonRows = MessageRenderer.calculateButtonRows(minecraft, message.actions(), textWidth);
                int buttonAreaHeight = buttonRows * (BUTTON_HEIGHT + BUTTON_PADDING);

                if (mouseY >= buttonStartY && mouseY < buttonStartY + buttonAreaHeight) {
                    int buttonX = textX;
                    int buttonY = buttonStartY;
                    int maxButtonX = textX + textWidth;

                    for (ChatAction action : message.actions()) {
                        int buttonWidth = ActionButtonRenderer.calculateWidth(minecraft, action);

                        // Wrap to next row if button doesn't fit
                        if (buttonX + buttonWidth > maxButtonX && buttonX > textX) {
                            buttonX = textX;
                            buttonY += BUTTON_HEIGHT + BUTTON_PADDING;
                        }

                        if (mouseX >= buttonX && mouseX < buttonX + buttonWidth
                                && mouseY >= buttonY && mouseY < buttonY + BUTTON_HEIGHT) {
                            if (InventoryHelper.isActionDisabled(minecraft, action)) {
                                return true;
                            }

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
    }
}
