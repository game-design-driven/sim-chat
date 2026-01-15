package com.yardenzamir.simchat.client.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import com.yardenzamir.simchat.client.ClientTemplateEngine;
import com.yardenzamir.simchat.client.RuntimeTemplateResolver;
import com.yardenzamir.simchat.data.ChatAction;
import com.yardenzamir.simchat.data.ChatMessage;
import com.yardenzamir.simchat.network.ActionClickPacket;
import com.yardenzamir.simchat.network.NetworkHandler;

import static com.yardenzamir.simchat.client.widget.ChatHistoryConstants.*;

/**
 * Scrollable chat history displaying messages with avatars and action buttons.
 */
public class ChatHistoryWidget extends AbstractWidget {

    private final Minecraft minecraft;
    private final List<ChatMessage> messages = new ArrayList<>();
    private final HoverState hoverState = new HoverState();

    private @Nullable String entityId;
    private @Nullable String typingEntityNameResolved;
    private @Nullable String typingEntityImageId;
    private boolean isTyping = false;

    private int scrollOffset = 0;
    private int contentHeight = 0;

    // Player input state
    private @Nullable ActiveInputState activeInput;
    private long cursorBlinkStart = 0;

    /**
     * Tracks the state of an active text input field.
     */
    private record ActiveInputState(
            int messageIndex,
            int actionIndex,
            StringBuilder text,
            ChatAction.PlayerInputConfig config,
            @Nullable Pattern compiledPattern
    ) {
        boolean isValid() {
            if (text.isEmpty()) return false;
            if (compiledPattern == null) return true;
            return compiledPattern.matcher(text).matches();
        }

        static ActiveInputState create(int messageIndex, int actionIndex, ChatAction.PlayerInputConfig config) {
            Pattern pattern = null;
            if (config.pattern() != null) {
                try {
                    pattern = Pattern.compile(config.pattern());
                } catch (PatternSyntaxException ignored) {
                    // Invalid pattern - will always fail validation
                }
            }
            return new ActiveInputState(messageIndex, actionIndex, new StringBuilder(), config, pattern);
        }
    }

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
        RuntimeTemplateResolver.preloadMessages(this.messages);
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
        if (RuntimeTemplateResolver.needsPreload()) {
            RuntimeTemplateResolver.preloadMessages(this.messages);
        } else if (this.messages.size() > oldCount) {
            RuntimeTemplateResolver.preloadMessages(this.messages.subList(oldCount, this.messages.size()));
        }
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
        this.typingEntityNameResolved = null;
        this.typingEntityImageId = null;
        this.scrollOffset = 0;
        this.contentHeight = 0;
        this.activeInput = null;
    }

    public void setTyping(boolean typing, @Nullable String entityName, @Nullable String nameTemplate, @Nullable String imageId) {
        boolean wasTyping = this.isTyping;
        this.isTyping = typing;
        this.typingEntityNameResolved = resolveTypingName(entityName, nameTemplate);
        this.typingEntityImageId = imageId;
        recalculateContentHeight();

        if (typing && !wasTyping) {
            scrollToBottom();
        }
    }

    private @Nullable String resolveTypingName(@Nullable String entityName, @Nullable String nameTemplate) {
        if (nameTemplate == null || nameTemplate.isEmpty()) {
            return entityName;
        }
        String resolved = ClientTemplateEngine.process(nameTemplate);
        if (ClientTemplateEngine.hasPlaceholders(resolved)) {
            return entityName;
        }
        return resolved;
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
        scrollOffset = clampScrollOffset(contentHeight);
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
        // Clamp scroll in case dimensions changed since scroll was set
        scrollOffset = clampScrollOffset(scrollOffset);

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
                // Build active input info if this message has the active input
                MessageRenderer.ActiveInputInfo inputInfo = null;
                if (activeInput != null && activeInput.messageIndex() == i) {
                    boolean cursorVisible = ((System.currentTimeMillis() - cursorBlinkStart) / 500) % 2 == 0;
                    inputInfo = new MessageRenderer.ActiveInputInfo(
                            i, activeInput.actionIndex(), activeInput.text().toString(),
                            activeInput.isValid(), cursorVisible);
                }

                MessageRenderer.render(graphics, minecraft, message, i,
                        getX() + MESSAGE_PADDING, y, width,
                        mouseX, mouseY, isHovered, hoverState, inputInfo);
            }

            y += msgHeight + MESSAGE_PADDING;
        }

        if (isTyping && typingEntityImageId != null) {
            if (y + TYPING_INDICATOR_HEIGHT > getY() && y < getY() + height) {
                MessageRenderer.renderTypingIndicator(graphics, minecraft,
                        typingEntityNameResolved, typingEntityImageId,
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
        if (button != 0) {
            return false;
        }

        // Copy to avoid ConcurrentModificationException from network thread updates
        List<ChatMessage> snapshot = List.copyOf(messages);

        // Track if we clicked inside the active input field
        boolean clickedInsideActiveInput = false;

        int y = getY() + MESSAGE_PADDING - scrollOffset;

        for (int i = 0; i < snapshot.size(); i++) {
            ChatMessage message = snapshot.get(i);
            int msgHeight = MessageRenderer.calculateHeight(minecraft, message, width);

            if (!message.actions().isEmpty()) {
                int textX = getX() + MESSAGE_PADDING + AVATAR_SIZE + MESSAGE_PADDING;
                int textWidth = width - AVATAR_SIZE - MESSAGE_PADDING * 3;
                String resolvedContent = RuntimeTemplateResolver.resolveContent(message);
                List<String> wrappedLines = MessageRenderer.wrapText(minecraft, resolvedContent, textWidth);
                int textY = y + minecraft.font.lineHeight + 2 + (wrappedLines.size() * (minecraft.font.lineHeight + 2));
                int buttonStartY = textY + BUTTON_PADDING;

                // Calculate total button area height
                int buttonRows = MessageRenderer.calculateButtonRows(minecraft, message, textWidth);
                int buttonAreaHeight = buttonRows * (BUTTON_HEIGHT + BUTTON_PADDING);

                if (mouseY >= buttonStartY && mouseY < buttonStartY + buttonAreaHeight) {
                    int buttonX = textX;
                    int buttonY = buttonStartY;
                    int maxButtonX = textX + textWidth;

                    for (int actionIndex = 0; actionIndex < message.actions().size(); actionIndex++) {
                        ChatAction action = message.actions().get(actionIndex);
                        String label = RuntimeTemplateResolver.resolveActionLabel(message, actionIndex, action);

                        // Check if this action is in input mode
                        boolean isActiveInput = activeInput != null
                                && activeInput.messageIndex() == i
                                && activeInput.actionIndex() == actionIndex;

                        int buttonWidth;
                        if (isActiveInput && action.playerInput() != null) {
                            buttonWidth = ActionButtonRenderer.calculateInputModeWidth(minecraft, action.playerInput().maxLength());
                        } else {
                            buttonWidth = ActionButtonRenderer.calculateWidth(minecraft, action, label);
                        }

                        // Wrap to next row if button doesn't fit
                        if (buttonX + buttonWidth > maxButtonX && buttonX > textX) {
                            buttonX = textX;
                            buttonY += BUTTON_HEIGHT + BUTTON_PADDING;
                        }

                        if (mouseX >= buttonX && mouseX < buttonX + buttonWidth
                                && mouseY >= buttonY && mouseY < buttonY + BUTTON_HEIGHT) {

                            if (isActiveInput && action.playerInput() != null) {
                                // Clicked inside active input - check if Send button
                                int fieldWidth = ActionButtonRenderer.calculateInputFieldWidth(minecraft,
                                        activeInput.text().toString(), action.playerInput().maxLength());
                                int sendX = buttonX + fieldWidth + BUTTON_PADDING;

                                if (mouseX >= sendX) {
                                    // Clicked Send button
                                    if (activeInput.isValid() && entityId != null) {
                                        NetworkHandler.CHANNEL.sendToServer(
                                                new ActionClickPacket(entityId, i, actionIndex, activeInput.text().toString())
                                        );
                                        activeInput = null;
                                    }
                                    return true;
                                } else {
                                    // Clicked inside field - keep input active
                                    clickedInsideActiveInput = true;
                                    return true;
                                }
                            } else if (action.playerInput() != null) {
                                // Clicked a playerInput action - activate input mode
                                if (!InventoryHelper.isActionDisabled(minecraft, action)) {
                                    activeInput = ActiveInputState.create(i, actionIndex, action.playerInput());
                                    cursorBlinkStart = System.currentTimeMillis();
                                }
                                return true;
                            } else {
                                // Regular action click
                                if (InventoryHelper.isActionDisabled(minecraft, action)) {
                                    return true;
                                }

                                if (entityId != null) {
                                    NetworkHandler.CHANNEL.sendToServer(
                                            new ActionClickPacket(entityId, i, actionIndex)
                                    );
                                }
                                return true;
                            }
                        }

                        buttonX += buttonWidth + BUTTON_PADDING;
                    }
                }
            }

            y += msgHeight + MESSAGE_PADDING;
        }

        // Clicked outside all buttons - cancel active input if any
        if (activeInput != null && !clickedInsideActiveInput) {
            activeInput = null;
            return true;
        }

        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (activeInput == null) {
            return false;
        }

        // ESC - cancel input
        if (keyCode == 256) { // GLFW_KEY_ESCAPE
            activeInput = null;
            return true;
        }

        // ENTER - submit if valid
        if (keyCode == 257 || keyCode == 335) { // GLFW_KEY_ENTER or GLFW_KEY_KP_ENTER
            if (activeInput.isValid() && entityId != null) {
                NetworkHandler.CHANNEL.sendToServer(
                        new ActionClickPacket(entityId, activeInput.messageIndex(), activeInput.actionIndex(), activeInput.text().toString())
                );
                activeInput = null;
            }
            return true;
        }

        // BACKSPACE - delete last character
        if (keyCode == 259) { // GLFW_KEY_BACKSPACE
            if (!activeInput.text().isEmpty()) {
                activeInput.text().deleteCharAt(activeInput.text().length() - 1);
                cursorBlinkStart = System.currentTimeMillis();
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (activeInput == null) {
            return false;
        }

        // Only accept printable characters
        if (!SharedConstants.isAllowedChatCharacter(codePoint)) {
            return false;
        }

        // Enforce max length
        if (activeInput.text().length() >= activeInput.config().maxLength()) {
            return true;
        }

        activeInput.text().append(codePoint);
        cursorBlinkStart = System.currentTimeMillis();
        return true;
    }

    /**
     * Returns true if an input field is currently active (for screen focus handling).
     */
    public boolean hasActiveInput() {
        return activeInput != null;
    }

    /**
     * Cancels the active input if any.
     */
    public void cancelInput() {
        activeInput = null;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
