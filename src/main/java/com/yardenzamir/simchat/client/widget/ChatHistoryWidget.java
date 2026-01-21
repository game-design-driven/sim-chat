package com.yardenzamir.simchat.client.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import com.yardenzamir.simchat.capability.ChatCapability;
import com.yardenzamir.simchat.client.ClientTeamCache;
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

    // Chunked loading state
    private int loadingIndex = 0;
    private boolean isLoadingChunked = false;
    private static final int CHUNK_SIZE = 50; // Process 50 messages per frame

    // Lazy loading state
    private boolean requestingOlderMessages = false;

    // Scrollbar dragging state
    private boolean draggingScrollbar = false;
    private int dragStartY = 0;
    private int dragStartScrollOffset = 0;

    // Focused message state
    private @Nullable UUID focusedMessageId;
    private int focusedMessageIndex = -1;
    private boolean pendingFocusScroll = false;

    // Context menu state
    private @Nullable ContextMenu contextMenu;

    /**
     * Tracks the state of an active text input field.
     */
    private record ActiveInputState(
            int messageIndex,
            UUID messageId,
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

        static ActiveInputState create(int messageIndex, UUID messageId, int actionIndex, ChatAction.PlayerInputConfig config) {
            Pattern pattern = null;
            if (config.pattern() != null) {
                try {
                    pattern = Pattern.compile(config.pattern());
                } catch (PatternSyntaxException ignored) {
                    // Invalid regex in config, treat as no validation
                }
            }
            return new ActiveInputState(messageIndex, messageId, actionIndex, new StringBuilder(), config, pattern);
        }
    }

    private static final String CONTEXT_MENU_SHARE = "Share to team chat";
    private static final String CONTEXT_MENU_COPY = "Copy message";

    private record ContextMenu(UUID messageId, int messageIndex, int x, int y) {}

    private record ContextMenuLayout(int x, int y, int width, int height, int itemHeight) {}

    private record MessageHit(int index, ChatMessage message, int y, int height) {}


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
        this.requestingOlderMessages = false;
        this.contextMenu = null;

        // Preload all messages for template resolution
        RuntimeTemplateResolver.preloadMessages(this.messages, RuntimeTemplateResolver.ResolutionPriority.HIGH);

        recalculateContentHeight();
        scrollToBottom();
    }

    /**
     * Updates messages for the current conversation, preserving scroll position appropriately.
     * Used when messages are added or older messages are loaded.
     */
    public void updateMessages(List<ChatMessage> messages, @Nullable String entityId) {
        int oldCount = this.messages.size();
        int oldScrollOffset = this.scrollOffset;
        int oldContentHeight = this.contentHeight;

        // Detect if this is older messages loading (messages prepended) vs new messages (appended)
        boolean loadingOlderMessages = false;
        if (!this.messages.isEmpty() && !messages.isEmpty() && messages.size() > oldCount) {
            // Check if the first old message is now at a different index
            UUID firstOldId = this.messages.get(0).messageId();
            int newIndex = -1;
            for (int i = 0; i < messages.size(); i++) {
                if (messages.get(i).messageId().equals(firstOldId)) {
                    newIndex = i;
                    break;
                }
            }
            loadingOlderMessages = newIndex > 0;
        }

        this.messages.clear();
        this.messages.addAll(messages);
        this.entityId = entityId;
        this.contextMenu = null;

        // Preload new messages for template resolution
        if (messages.size() > oldCount) {
            if (loadingOlderMessages) {
                int newMessageCount = messages.size() - oldCount;
                RuntimeTemplateResolver.preloadMessages(this.messages.subList(0, newMessageCount), RuntimeTemplateResolver.ResolutionPriority.HIGH);
            } else {
                RuntimeTemplateResolver.preloadMessages(this.messages.subList(oldCount, this.messages.size()), RuntimeTemplateResolver.ResolutionPriority.HIGH);
            }
        }

        recalculateContentHeight();

        if (loadingOlderMessages) {
            // Older messages loaded - adjust scroll to maintain view of same content
            int heightDifference = contentHeight - oldContentHeight;
            this.scrollOffset = clampScrollOffset(oldScrollOffset + heightDifference);
        } else if (messages.size() > oldCount) {
            // New messages arrived - scroll to bottom
            scrollToBottom();
        } else {
            // Same or fewer messages - maintain scroll position
            this.scrollOffset = clampScrollOffset(oldScrollOffset);
        }

        if (pendingFocusScroll && focusedMessageId != null) {
            pendingFocusScroll = !scrollToMessage(focusedMessageId);
        }
    }

    public void setFocusedMessage(@Nullable UUID messageId, int messageIndex, boolean scrollToMessage) {
        this.focusedMessageId = messageId;
        this.focusedMessageIndex = messageId != null ? messageIndex : -1;
        this.pendingFocusScroll = false;
        if (scrollToMessage && messageId != null) {
            this.pendingFocusScroll = !scrollToMessage(messageId);
        }
    }

    public void clearFocusedMessage() {
        this.focusedMessageId = null;
        this.focusedMessageIndex = -1;
        this.pendingFocusScroll = false;
    }

    public @Nullable UUID getFocusedMessageId() {
        return focusedMessageId;
    }

    public int getFocusedMessageIndex() {
        return focusedMessageIndex;
    }

    private boolean scrollToMessage(UUID messageId) {
        int y = getY() + MESSAGE_PADDING;
        for (ChatMessage message : messages) {
            int msgHeight = MessageRenderer.calculateHeight(minecraft, message, width);
            if (message.messageId().equals(messageId)) {
                int desiredOffset = y - getY() - MESSAGE_PADDING;
                scrollOffset = clampScrollOffset(desiredOffset);
                return true;
            }
            y += msgHeight + MESSAGE_PADDING;
        }
        return false;
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
        this.requestingOlderMessages = false;
        this.focusedMessageId = null;
        this.focusedMessageIndex = -1;
        this.contextMenu = null;
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

    /**
     * Process a chunk of messages for preloading and height calculation.
     * Called each frame until all messages are processed.
     * Processes from newest to oldest for better UX.
     */
    private void processLoadingChunk() {
        if (loadingIndex <= 0) {
            isLoadingChunked = false;
            // Final accurate height calculation
            recalculateContentHeight();
            return;
        }

        int startIndex = Math.max(0, loadingIndex - CHUNK_SIZE);
        List<ChatMessage> chunk = messages.subList(startIndex, loadingIndex);

        // Preload templates for this chunk
        RuntimeTemplateResolver.preloadMessages(chunk, RuntimeTemplateResolver.ResolutionPriority.HIGH);

        loadingIndex = startIndex;
    }

    /**
     * Check if user scrolled near top and request older messages if available.
     */
    private void checkLazyLoadOlderMessages() {
        if (entityId == null || requestingOlderMessages) {
            return;
        }

        int threshold = com.yardenzamir.simchat.config.ClientConfig.LAZY_LOAD_THRESHOLD.get();
        int batchSize = com.yardenzamir.simchat.config.ClientConfig.LAZY_LOAD_BATCH_SIZE.get();

        // Check if scrolled near top
        if (scrollOffset < threshold) {
            // Check if there are older messages to load
            if (ClientTeamCache.hasOlderMessages(entityId)) {
                requestingOlderMessages = true;
                int beforeIndex = ClientTeamCache.getOldestLoadedIndex(entityId);
                if (beforeIndex <= 0) {
                    beforeIndex = ClientTeamCache.getTotalMessageCount(entityId);
                }
                com.yardenzamir.simchat.network.NetworkHandler.requestOlderMessages(
                        entityId, beforeIndex, batchSize);
            }
        }
    }

    /**
     * Called when older messages finish loading.
     */
    public void onOlderMessagesLoaded() {
        requestingOlderMessages = false;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Process chunked loading if active
        if (isLoadingChunked) {
            processLoadingChunk();
        }

        // Check if we should load older messages
        checkLazyLoadOlderMessages();

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
                int displayHeight = Math.max(1, msgHeight - MESSAGE_PADDING);
                int focusPadding = FOCUS_BORDER_PADDING;
                int focusTop = y - focusPadding;
                int focusBottom = y + displayHeight + focusPadding;
                int clampedTop = Math.max(getY(), focusTop);
                int clampedBottom = Math.min(getY() + height, focusBottom);
                int focusHeight = Math.max(1, clampedBottom - clampedTop);

                if (contextMenu != null && contextMenu.messageId().equals(message.messageId())) {
                    graphics.fill(getX(), clampedTop, getX() + width, clampedTop + focusHeight, CONTEXT_MENU_HIGHLIGHT_COLOR);
                }

                boolean isFocused = focusedMessageId != null && focusedMessageId.equals(message.messageId());
                if (isFocused) {
                    graphics.renderOutline(getX(), clampedTop, width, focusHeight, FOCUS_BORDER_COLOR);
                }

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

    private boolean isMouseOverScrollbar(double mouseX, double mouseY) {
        if (contentHeight <= height) return false;

        int scrollbarX = getX() + width - SCROLLBAR_WIDTH - 1;
        int scrollbarHeight = Math.max(SCROLLBAR_MIN_HEIGHT, (int) ((float) height / contentHeight * height));
        int scrollbarY = getY() + (int) ((float) scrollOffset / (contentHeight - height) * (height - scrollbarHeight));

        return mouseX >= scrollbarX && mouseX <= getX() + width - 1
                && mouseY >= scrollbarY && mouseY <= scrollbarY + scrollbarHeight;
    }

    private void handleScrollbarDrag(double mouseY) {
        if (contentHeight <= height) return;

        int scrollbarHeight = Math.max(SCROLLBAR_MIN_HEIGHT, (int) ((float) height / contentHeight * height));
        int availableHeight = height - scrollbarHeight;

        int relativeY = (int) (mouseY - getY());
        int scrollbarY = Math.max(0, Math.min(availableHeight, relativeY - scrollbarHeight / 2));

        scrollOffset = (int) ((float) scrollbarY / availableHeight * (contentHeight - height));
        scrollOffset = clampScrollOffset(scrollOffset);
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

    public void renderContextMenuOverlay(GuiGraphics graphics) {
        ContextMenuLayout layout = getContextMenuLayout();
        if (layout == null) {
            return;
        }

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);

        int x = layout.x();
        int y = layout.y();
        int width = layout.width();
        int height = layout.height();
        int itemHeight = layout.itemHeight();

        graphics.fill(x, y, x + width, y + height, CONTEXT_MENU_BG_COLOR);
        graphics.renderOutline(x, y, width, height, CONTEXT_MENU_BORDER_COLOR);

        int textX = x + CONTEXT_MENU_PADDING;
        int textY = y + CONTEXT_MENU_PADDING + (itemHeight - minecraft.font.lineHeight) / 2;
        graphics.drawString(minecraft.font, CONTEXT_MENU_SHARE, textX, textY, CONTEXT_MENU_TEXT_COLOR);
        graphics.drawString(minecraft.font, CONTEXT_MENU_COPY, textX, textY + itemHeight, CONTEXT_MENU_TEXT_COLOR);

        graphics.pose().popPose();
    }

    private @Nullable ContextMenuLayout getContextMenuLayout() {
        if (contextMenu == null) {
            return null;
        }
        int textWidth = Math.max(
                minecraft.font.width(CONTEXT_MENU_SHARE),
                minecraft.font.width(CONTEXT_MENU_COPY)
        );
        int menuWidth = CONTEXT_MENU_PADDING * 2 + textWidth;
        int menuHeight = CONTEXT_MENU_PADDING * 2 + CONTEXT_MENU_ITEM_HEIGHT * 2;

        int maxX = getX() + width - menuWidth;
        int maxY = getY() + height - menuHeight;
        int menuX = Math.max(getX(), Math.min(contextMenu.x(), maxX));
        int menuY = Math.max(getY(), Math.min(contextMenu.y(), maxY));

        return new ContextMenuLayout(menuX, menuY, menuWidth, menuHeight, CONTEXT_MENU_ITEM_HEIGHT);
    }

    private @Nullable ChatMessage getMessageById(UUID messageId) {
        for (ChatMessage message : messages) {
            if (message.messageId().equals(messageId)) {
                return message;
            }
        }
        return null;
    }

    private @Nullable MessageHit getMessageHit(double mouseX, double mouseY) {
        int y = getY() + MESSAGE_PADDING - scrollOffset;
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            int msgHeight = MessageRenderer.calculateHeight(minecraft, message, width);
            if (mouseX >= getX() && mouseX < getX() + width
                    && mouseY >= y && mouseY < y + msgHeight) {
                return new MessageHit(i, message, y, msgHeight);
            }
            y += msgHeight + MESSAGE_PADDING;
        }
        return null;
    }

    private void handleContextMenuClick(ContextMenuLayout layout, double mouseX, double mouseY) {
        if (contextMenu == null) {
            return;
        }
        int relativeY = (int) mouseY - layout.y() - CONTEXT_MENU_PADDING;
        if (relativeY < 0) {
            contextMenu = null;
            return;
        }
        int itemIndex = relativeY / layout.itemHeight();
        if (itemIndex == 0) {
            NetworkHandler.requestShareMessage(contextMenu.messageId());
        } else if (itemIndex == 1) {
            ChatMessage message = getMessageById(contextMenu.messageId());
            if (message != null) {
                String resolved = RuntimeTemplateResolver.resolveContent(message, RuntimeTemplateResolver.ResolutionPriority.HIGH);
                minecraft.keyboardHandler.setClipboard(resolved);
            }
        }
        contextMenu = null;
    }

    private void handleMessageFocus(ChatMessage message) {
        if (entityId == null) {
            return;
        }
        UUID messageId = message.messageId();
        if (focusedMessageId != null && focusedMessageId.equals(messageId)) {
            clearFocusOnClient();
            return;
        }

        int messageIndex = ClientTeamCache.getMessageIndex(entityId, messageId);
        if (messageIndex < 0) {
            return;
        }

        focusedMessageId = messageId;
        focusedMessageIndex = messageIndex;
        ChatCapability.get(minecraft.player).ifPresent(data -> data.setFocusedMessage(entityId, messageId, messageIndex));
        NetworkHandler.sendFocusUpdate(entityId, messageId, messageIndex);
    }

    private void clearFocusOnClient() {
        if (entityId == null) {
            focusedMessageId = null;
            focusedMessageIndex = -1;
            return;
        }
        focusedMessageId = null;
        focusedMessageIndex = -1;
        ChatCapability.get(minecraft.player).ifPresent(data -> data.clearFocusedMessage(entityId));
        NetworkHandler.clearFocus(entityId);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isMouseOver(mouseX, mouseY)) {
            scrollOffset = clampScrollOffset(scrollOffset - (int) (delta * SCROLL_SPEED));
            contextMenu = null;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar && button == 0) {
            handleScrollbarDrag(mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) {
            ContextMenuLayout layout = getContextMenuLayout();
            if (layout != null
                    && mouseX >= layout.x() && mouseX < layout.x() + layout.width()
                    && mouseY >= layout.y() && mouseY < layout.y() + layout.height()) {
                contextMenu = null;
                return true;
            }

            MessageHit hit = getMessageHit(mouseX, mouseY);
            if (hit != null) {
                contextMenu = new ContextMenu(hit.message().messageId(), hit.index(), (int) mouseX, (int) mouseY);
                return true;
            }

            contextMenu = null;
            return false;
        }

        if (button != 0) {
            return false;
        }

        ContextMenuLayout menuLayout = getContextMenuLayout();
        if (menuLayout != null) {
            if (mouseX >= menuLayout.x() && mouseX < menuLayout.x() + menuLayout.width()
                    && mouseY >= menuLayout.y() && mouseY < menuLayout.y() + menuLayout.height()) {
                handleContextMenuClick(menuLayout, mouseX, mouseY);
                return true;
            }
            contextMenu = null;
        }

        // Check if clicked on scrollbar
        if (isMouseOverScrollbar(mouseX, mouseY)) {
            draggingScrollbar = true;
            dragStartY = (int) mouseY;
            dragStartScrollOffset = scrollOffset;
            handleScrollbarDrag(mouseY);
            return true;
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
                String resolvedContent = RuntimeTemplateResolver.resolveContent(message, RuntimeTemplateResolver.ResolutionPriority.HIGH);
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
                        String label = RuntimeTemplateResolver.resolveActionLabel(message, actionIndex, action, RuntimeTemplateResolver.ResolutionPriority.HIGH);

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
                                                new ActionClickPacket(entityId, activeInput.messageId(), actionIndex, activeInput.text().toString())
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
                                    activeInput = ActiveInputState.create(i, message.messageId(), actionIndex, action.playerInput());
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
                                            new ActionClickPacket(entityId, message.messageId(), actionIndex)
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

        MessageHit hit = getMessageHit(mouseX, mouseY);
        if (hit != null) {
            handleMessageFocus(hit.message());
            return true;
        }

        if (focusedMessageId != null) {
            clearFocusOnClient();
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
                        new ActionClickPacket(entityId, activeInput.messageId(), activeInput.actionIndex(), activeInput.text().toString())
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
