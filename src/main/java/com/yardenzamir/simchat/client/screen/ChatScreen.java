package com.yardenzamir.simchat.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import com.yardenzamir.simchat.capability.ChatCapability;
import com.yardenzamir.simchat.client.ClientTeamCache;
import com.yardenzamir.simchat.client.RuntimeTemplateResolver;
import com.yardenzamir.simchat.client.SortMode;
import com.yardenzamir.simchat.client.widget.ChatHistoryWidget;
import com.yardenzamir.simchat.client.widget.EntityListWidget;
import com.yardenzamir.simchat.config.ClientConfig;
import com.yardenzamir.simchat.data.ChatMessage;
import com.yardenzamir.simchat.data.PlayerChatData;
import com.yardenzamir.simchat.network.MarkAsReadPacket;
import com.yardenzamir.simchat.network.NetworkHandler;
import com.yardenzamir.simchat.team.TeamData;

/**
 * Main chat screen with entity list sidebar and chat history panel.
 * Uses TeamData from ClientTeamCache for conversations, PlayerChatData for read counts.
 */
public class ChatScreen extends Screen {

    private static final int PADDING = 4;
    private static final int HEADER_HEIGHT = 20;
    private static final int DIVIDER_WIDTH = 5;
    private static final int COMPACT_WIDTH = 60;
    private static final int SNAP_THRESHOLD = 20;
    private static final int HEADER_BUTTON_PADDING = 6;

    private @Nullable String selectedEntityId;
    private EntityListWidget entityList;
    private ChatHistoryWidget chatHistory;
    private @Nullable String lastTeamId;
    private int lastTeamRevision = -1;
    private int lastReadRevision = -1;
    private boolean lastTypingState = false;

    private int sidebarWidth;
    private boolean draggingDivider = false;
    private boolean hoveringDivider = false;

    private SortMode sortMode;

    public ChatScreen(@Nullable String initialEntityId) {
        super(Component.literal("SimChat"));
        this.selectedEntityId = initialEntityId;
    }

    @Override
    protected void init() {
        super.init();

        sidebarWidth = ClientConfig.SIDEBAR_WIDTH.get();
        sortMode = SortMode.fromId(ClientConfig.SIDEBAR_SORT_MODE.get());
        clampSidebarWidth();

        rebuildLayout();
        RuntimeTemplateResolver.clear();
        refreshAll();

        if (selectedEntityId != null && !selectedEntityId.isEmpty()) {
            selectEntity(selectedEntityId);
        } else {
            selectFirstEntity();
        }
    }

    private void clampSidebarWidth() {
        int maxWidth = width / 2;
        sidebarWidth = Math.max(COMPACT_WIDTH, Math.min(sidebarWidth, maxWidth));
    }

    private boolean isCompactMode() {
        return sidebarWidth <= COMPACT_WIDTH + SNAP_THRESHOLD;
    }

    private void rebuildLayout() {
        clearWidgets();

        entityList = new EntityListWidget(
                minecraft,
                sidebarWidth - PADDING * 2,
                height - PADDING * 2,
                PADDING,
                PADDING,
                this::onEntitySelected
        );
        entityList.setCompactMode(isCompactMode());
        addRenderableWidget(entityList);

        int chatX = sidebarWidth + PADDING;
        int chatWidth = width - sidebarWidth - PADDING * 2;
        chatHistory = new ChatHistoryWidget(
                minecraft,
                chatWidth,
                height - HEADER_HEIGHT - PADDING * 2,
                chatX,
                HEADER_HEIGHT + PADDING
        );
        addRenderableWidget(chatHistory);

        // Restore state after rebuild
        TeamData team = ClientTeamCache.getTeam();
        if (team != null && minecraft != null && minecraft.player != null) {
            PlayerChatData readData = ChatCapability.getOrThrow(minecraft.player);
            entityList.setEntities(team, readData, team.getEntityIds());
            if (selectedEntityId != null) {
                entityList.setSelected(selectedEntityId);
                chatHistory.updateMessages(team.getMessages(selectedEntityId), selectedEntityId);
                chatHistory.setTyping(
                        team.isTyping(selectedEntityId),
                        team.getEntityDisplayName(selectedEntityId),
                        team.getEntityDisplayNameTemplate(selectedEntityId),
                        team.getEntityImageId(selectedEntityId)
                );
            }
        }
    }

    private void refreshAll() {
        TeamData team = ClientTeamCache.getTeam();
        if (team == null || minecraft == null || minecraft.player == null) return;

        PlayerChatData readData = ChatCapability.getOrThrow(minecraft.player);
        lastTeamId = team.getId();
        lastTeamRevision = team.getRevision();
        lastReadRevision = readData.getRevision();

        List<String> entityIds = sortEntityIds(team, team.getEntityIds());
        entityList.setEntities(team, readData, entityIds);
    }

    private List<String> sortEntityIds(TeamData team, List<String> entityIds) {
        if (sortMode == SortMode.ALPHABETICAL) {
            List<String> sorted = new ArrayList<>(entityIds);
            sorted.sort(Comparator.comparing(id -> {
                ChatMessage lastMessage = getLastNonPlayerMessage(team, id);
                String name = lastMessage != null
                        ? RuntimeTemplateResolver.resolveSenderName(lastMessage, RuntimeTemplateResolver.ResolutionPriority.LOW)
                        : team.getEntityDisplayName(id);
                return name != null ? name.toLowerCase() : id.toLowerCase();
            }));
            return sorted;
        }
        return entityIds;
    }

    private void toggleSortMode() {
        sortMode = sortMode.next();
        ClientConfig.SIDEBAR_SORT_MODE.set(sortMode.getId());
        refreshAll();
    }

    private void handleRefresh() {
        RuntimeTemplateResolver.clear();
        refreshAll();
        if (selectedEntityId != null) {
            refreshChatHistory();
        }
    }

    private Component getRefreshText() {
        return Component.translatable("simchat.screen.refresh");
    }

    private int getRefreshButtonHeight() {
        return font.lineHeight + 4;
    }

    private int getRefreshButtonWidth() {
        return font.width(getRefreshText()) + HEADER_BUTTON_PADDING * 2;
    }

    private int getRefreshButtonX() {
        int buttonWidth = getRefreshButtonWidth();
        int x = width - PADDING - buttonWidth;
        int minX = sidebarWidth + PADDING;
        return Math.max(minX, x);
    }

    private int getRefreshButtonY() {
        return PADDING + 2;
    }

    private boolean isOverRefreshButton(double mouseX, double mouseY) {
        int x = getRefreshButtonX();
        int y = getRefreshButtonY();
        int width = getRefreshButtonWidth();
        int height = getRefreshButtonHeight();
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void selectFirstEntity() {
        TeamData team = ClientTeamCache.getTeam();
        if (team == null) return;

        List<String> entityIds = team.getEntityIds();
        if (!entityIds.isEmpty()) {
            selectEntity(entityIds.get(0));
        }
    }

    private void onEntitySelected(String entityId) {
        selectEntity(entityId);
    }

    private void selectEntity(String entityId) {
        this.selectedEntityId = entityId;
        entityList.setSelected(entityId);

        TeamData team = ClientTeamCache.getTeam();
        if (team == null || minecraft == null || minecraft.player == null) return;

        PlayerChatData readData = ChatCapability.getOrThrow(minecraft.player);

        // Get read count before marking as read
        int readCount = readData.getReadCount(entityId);
        int totalMessages = ClientTeamCache.getTotalMessageCount(entityId);

        // Set messages with scroll to first unread
        chatHistory.setMessages(team.getMessages(entityId), entityId, readCount);
        chatHistory.setTyping(
                team.isTyping(entityId),
                team.getEntityDisplayName(entityId),
                team.getEntityDisplayNameTemplate(entityId),
                team.getEntityImageId(entityId)
        );

        // Mark as read
        readData.markAsRead(entityId, totalMessages);
        NetworkHandler.CHANNEL.sendToServer(new MarkAsReadPacket(entityId));

        // Track typing state for change detection
        lastTypingState = team.isTyping(entityId);
    }

    private @Nullable ChatMessage getLastNonPlayerMessage(TeamData team, String entityId) {
        List<ChatMessage> messages = team.getMessages(entityId);
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (!msg.isPlayerMessage()) {
                return msg;
            }
        }
        return null;
    }

    private void refreshChatHistory() {
        TeamData team = ClientTeamCache.getTeam();
        if (team == null || selectedEntityId == null) {
            chatHistory.clearMessages();
            return;
        }

        chatHistory.updateMessages(team.getMessages(selectedEntityId), selectedEntityId);
        chatHistory.setTyping(
                team.isTyping(selectedEntityId),
                team.getEntityDisplayName(selectedEntityId),
                team.getEntityDisplayNameTemplate(selectedEntityId),
                team.getEntityImageId(selectedEntityId)
        );
    }

    /**
     * Called when new messages are loaded (for lazy loading).
     */
    public void refreshMessages() {
        refreshChatHistory();
        chatHistory.onOlderMessagesLoaded();
    }

    private boolean isOverDivider(double mouseX) {
        int dividerX = sidebarWidth;
        return mouseX >= dividerX - DIVIDER_WIDTH / 2 && mouseX <= dividerX + DIVIDER_WIDTH / 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hoveringDivider = isOverDivider(mouseX);

        renderBackground(graphics);

        graphics.fill(0, 0, sidebarWidth, height, 0xDD1a1a2e);

        int dividerColor = (hoveringDivider || draggingDivider) ? 0xFF6060a0 : 0xFF3d3d5c;
        graphics.fill(sidebarWidth - 1, 0, sidebarWidth + 1, height, dividerColor);

        graphics.fill(sidebarWidth, 0, width, height, 0xDD12121f);

        // Header with selected entity name
        TeamData team = ClientTeamCache.getTeam();
        if (selectedEntityId != null && team != null) {
            ChatMessage lastMessage = getLastNonPlayerMessage(team, selectedEntityId);
            String displayName = lastMessage != null
                    ? RuntimeTemplateResolver.resolveSenderName(lastMessage, RuntimeTemplateResolver.ResolutionPriority.HIGH)
                    : team.getEntityDisplayName(selectedEntityId);
            if (displayName == null) displayName = selectedEntityId;
            int nameX = sidebarWidth + PADDING;
            graphics.drawString(font, displayName, nameX, PADDING + 4, 0xFFFFFFFF);

            String subtitle = lastMessage != null
                    ? RuntimeTemplateResolver.resolveSenderSubtitle(lastMessage, RuntimeTemplateResolver.ResolutionPriority.HIGH)
                    : team.getEntitySubtitle(selectedEntityId);
            if (subtitle != null) {
                int subtitleX = nameX + font.width(displayName);
                graphics.drawString(font, " - " + subtitle, subtitleX, PADDING + 4, 0xFF888888);
            }
        } else {
            Component emptyText = Component.translatable("simchat.screen.select_conversation");
            int textWidth = font.width(emptyText);
            int centerX = sidebarWidth + (width - sidebarWidth) / 2;
            int centerY = height / 2;
            graphics.drawString(font, emptyText, centerX - textWidth / 2, centerY, 0xFF888888);
        }

        Component refreshText = getRefreshText();
        int refreshButtonWidth = getRefreshButtonWidth();
        int refreshButtonHeight = getRefreshButtonHeight();
        int refreshButtonX = getRefreshButtonX();
        int refreshButtonY = getRefreshButtonY();
        boolean refreshHovered = isOverRefreshButton(mouseX, mouseY);
        int refreshBg = refreshHovered ? 0xFF404060 : 0xFF303050;
        graphics.fill(refreshButtonX, refreshButtonY, refreshButtonX + refreshButtonWidth,
                refreshButtonY + refreshButtonHeight, refreshBg);
        graphics.drawString(font, refreshText,
                refreshButtonX + (refreshButtonWidth - font.width(refreshText)) / 2,
                refreshButtonY + 2, 0xFFCCCCCC);

        graphics.fill(sidebarWidth, HEADER_HEIGHT, width, HEADER_HEIGHT + 1, 0xFF3d3d5c);

        // Sort button
        if (!isCompactMode()) {
            Component sortText = sortMode.getDisplayName();
            int sortTextWidth = font.width(sortText);
            int sortButtonX = PADDING;
            int sortButtonY = height - PADDING - font.lineHeight - 4;
            int sortButtonWidth = sidebarWidth - PADDING * 2 - DIVIDER_WIDTH;

            boolean sortHovered = mouseX >= sortButtonX && mouseX < sortButtonX + sortButtonWidth
                    && mouseY >= sortButtonY && mouseY < sortButtonY + font.lineHeight + 4;
            int buttonBg = sortHovered ? 0xFF404060 : 0xFF303050;
            graphics.fill(sortButtonX, sortButtonY, sortButtonX + sortButtonWidth, sortButtonY + font.lineHeight + 4, buttonBg);
            graphics.drawString(font, sortText, sortButtonX + (sortButtonWidth - sortTextWidth) / 2, sortButtonY + 2, 0xFFCCCCCC);
        }

        // Empty state
        if (team == null || !team.hasConversations()) {
            Component emptyText = Component.translatable("simchat.screen.no_messages");
            int emptyTextWidth = font.width(emptyText);
            graphics.drawString(font, emptyText,
                    (sidebarWidth - emptyTextWidth) / 2,
                    height / 2,
                    0xFF888888);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isOverDivider(mouseX)) {
            draggingDivider = true;
            return true;
        }

        if (button == 0 && isOverRefreshButton(mouseX, mouseY)) {
            handleRefresh();
            return true;
        }

        if (button == 0 && !isCompactMode()) {
            int sortButtonX = PADDING;
            int sortButtonY = height - PADDING - font.lineHeight - 4;
            int sortButtonWidth = sidebarWidth - PADDING * 2 - DIVIDER_WIDTH;
            int sortButtonHeight = font.lineHeight + 4;

            if (mouseX >= sortButtonX && mouseX < sortButtonX + sortButtonWidth
                    && mouseY >= sortButtonY && mouseY < sortButtonY + sortButtonHeight) {
                toggleSortMode();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingDivider) {
            draggingDivider = false;
            ClientConfig.SIDEBAR_WIDTH.set(sidebarWidth);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingDivider) {
            int newWidth = (int) mouseX;
            int maxWidth = width / 2;

            if (newWidth < COMPACT_WIDTH + SNAP_THRESHOLD) {
                newWidth = COMPACT_WIDTH;
            }

            newWidth = Math.max(COMPACT_WIDTH, Math.min(newWidth, maxWidth));

            if (newWidth != sidebarWidth) {
                sidebarWidth = newWidth;
                rebuildLayout();
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        int keepCount = ClientConfig.CLOSED_CACHE_SIZE.get();
        java.util.Set<java.util.UUID> retained = ClientTeamCache.trimToLatest(keepCount);
        RuntimeTemplateResolver.retainMessages(retained);
    }

    @Override
    public void tick() {
        super.tick();

        entityList.tick();

        TeamData team = ClientTeamCache.getTeam();
        if (team == null || minecraft == null || minecraft.player == null) return;

        PlayerChatData readData = ChatCapability.getOrThrow(minecraft.player);

        // Check if team changed (different team ID) or data changed (revision) or typing state changed
        boolean teamChanged = !team.getId().equals(lastTeamId);
        boolean dataChanged = team.getRevision() != lastTeamRevision || readData.getRevision() != lastReadRevision;
        boolean typingChanged = selectedEntityId != null && team.isTyping(selectedEntityId) != lastTypingState;

        if (teamChanged || dataChanged || typingChanged) {
            if (teamChanged) {
                // Switched teams - clear selection and select first entity
                selectedEntityId = null;
                lastTeamId = team.getId();
            }

            if (selectedEntityId != null) {
                int totalMessages = ClientTeamCache.getTotalMessageCount(selectedEntityId);
                readData.markAsRead(selectedEntityId, totalMessages);
                NetworkHandler.CHANNEL.sendToServer(new MarkAsReadPacket(selectedEntityId));
            }

            lastTeamRevision = team.getRevision();
            lastReadRevision = readData.getRevision();
            lastTypingState = selectedEntityId != null && team.isTyping(selectedEntityId);

            List<String> entityIds = sortEntityIds(team, team.getEntityIds());
            entityList.setEntities(team, readData, entityIds);

            if (teamChanged && !entityIds.isEmpty()) {
                selectEntity(entityIds.get(0));
            } else {
                refreshChatHistory();
            }
        }
    }
}
