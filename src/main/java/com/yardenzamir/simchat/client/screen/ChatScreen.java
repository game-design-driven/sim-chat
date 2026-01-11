package com.yardenzamir.simchat.client.screen;

import com.yardenzamir.simchat.capability.ChatCapability;
import com.yardenzamir.simchat.client.widget.ChatHistoryWidget;
import com.yardenzamir.simchat.client.widget.EntityListWidget;
import com.yardenzamir.simchat.config.ClientConfig;
import com.yardenzamir.simchat.data.PlayerChatData;
import com.yardenzamir.simchat.network.MarkAsReadPacket;
import com.yardenzamir.simchat.network.NetworkHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Main chat screen with entity list sidebar and chat history panel.
 * The divider between them is draggable to resize.
 */
public class ChatScreen extends Screen {

    private static final int PADDING = 4;
    private static final int HEADER_HEIGHT = 20;
    private static final int DIVIDER_WIDTH = 5;
    private static final int COMPACT_WIDTH = 60; // PADDING*2 (widget margin) + PADDING*2 (content margin) + PADDING (avatar left) + AVATAR(36) + PADDING (avatar right) = 60
    private static final int SNAP_THRESHOLD = 20; // Snap to compact when within this distance

    private @Nullable String selectedEntityId;
    private EntityListWidget entityList;
    private ChatHistoryWidget chatHistory;
    private int lastRevision = -1;

    private int sidebarWidth;
    private boolean draggingDivider = false;
    private boolean hoveringDivider = false;

    public ChatScreen(@Nullable String initialEntityId) {
        super(Component.literal("SimChat"));
        this.selectedEntityId = initialEntityId;
    }

    @Override
    protected void init() {
        super.init();

        sidebarWidth = ClientConfig.SIDEBAR_WIDTH.get();
        clampSidebarWidth();

        rebuildLayout();
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

        // Entity list on the left
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

        // Chat history on the right
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
        if (minecraft != null && minecraft.player != null) {
            ChatCapability.get(minecraft.player).ifPresent(data -> {
                entityList.setEntities(data, data.getEntityIds());
                if (selectedEntityId != null) {
                    entityList.setSelected(selectedEntityId);
                    chatHistory.setMessages(data.getMessages(selectedEntityId), selectedEntityId);
                    chatHistory.setTyping(
                            data.isTyping(selectedEntityId),
                            data.getEntityDisplayName(selectedEntityId),
                            data.getEntityImageId(selectedEntityId)
                    );
                }
            });
        }
    }

    private void refreshAll() {
        if (minecraft == null || minecraft.player == null) return;

        ChatCapability.get(minecraft.player).ifPresent(data -> {
            lastRevision = data.getRevision();
            List<String> entityIds = data.getEntityIds();
            entityList.setEntities(data, entityIds);
        });
    }

    private void selectFirstEntity() {
        if (minecraft == null || minecraft.player == null) return;

        ChatCapability.get(minecraft.player).ifPresent(data -> {
            List<String> entityIds = data.getEntityIds();
            if (!entityIds.isEmpty()) {
                selectEntity(entityIds.get(0));
            }
        });
    }

    private void onEntitySelected(String entityId) {
        selectEntity(entityId);
    }

    private void selectEntity(String entityId) {
        this.selectedEntityId = entityId;
        entityList.setSelected(entityId);

        if (minecraft != null && minecraft.player != null) {
            ChatCapability.get(minecraft.player).ifPresent(data -> {
                data.markAsRead(entityId);
            });
            NetworkHandler.CHANNEL.sendToServer(new MarkAsReadPacket(entityId));
        }

        refreshChatHistory();
    }

    private void refreshChatHistory() {
        if (minecraft == null || minecraft.player == null || selectedEntityId == null) {
            chatHistory.clearMessages();
            return;
        }

        ChatCapability.get(minecraft.player).ifPresent(data -> {
            chatHistory.setMessages(data.getMessages(selectedEntityId), selectedEntityId);
            chatHistory.setTyping(
                    data.isTyping(selectedEntityId),
                    data.getEntityDisplayName(selectedEntityId),
                    data.getEntityImageId(selectedEntityId)
            );
        });
    }

    private boolean isOverDivider(double mouseX) {
        int dividerX = sidebarWidth;
        return mouseX >= dividerX - DIVIDER_WIDTH / 2 && mouseX <= dividerX + DIVIDER_WIDTH / 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hoveringDivider = isOverDivider(mouseX);

        // Dark background
        renderBackground(graphics);

        // Sidebar background
        graphics.fill(0, 0, sidebarWidth, height, 0xDD1a1a2e);

        // Divider - highlight when hovering or dragging
        int dividerColor = (hoveringDivider || draggingDivider) ? 0xFF6060a0 : 0xFF3d3d5c;
        graphics.fill(sidebarWidth - 1, 0, sidebarWidth + 1, height, dividerColor);

        // Chat area background
        graphics.fill(sidebarWidth, 0, width, height, 0xDD12121f);

        // Header with selected entity name or empty state
        if (selectedEntityId != null && minecraft != null && minecraft.player != null) {
            ChatCapability.get(minecraft.player).ifPresent(data -> {
                String displayName = data.getEntityDisplayName(selectedEntityId);
                graphics.drawString(font, displayName, sidebarWidth + PADDING, PADDING + 4, 0xFFFFFFFF);
            });
        } else {
            String emptyText = "Select a conversation";
            int textWidth = font.width(emptyText);
            int centerX = sidebarWidth + (width - sidebarWidth) / 2;
            int centerY = height / 2;
            graphics.drawString(font, emptyText, centerX - textWidth / 2, centerY, 0xFF888888);
        }

        // Header separator
        graphics.fill(sidebarWidth, HEADER_HEIGHT, width, HEADER_HEIGHT + 1, 0xFF3d3d5c);

        // Empty state for sidebar
        if (minecraft != null && minecraft.player != null) {
            ChatCapability.get(minecraft.player).ifPresent(data -> {
                if (!data.hasConversations()) {
                    String emptyText = "No messages yet";
                    int emptyTextWidth = font.width(emptyText);
                    graphics.drawString(font, emptyText,
                            (sidebarWidth - emptyTextWidth) / 2,
                            height / 2,
                            0xFF888888);
                }
            });
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isOverDivider(mouseX)) {
            draggingDivider = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingDivider) {
            draggingDivider = false;
            // Save to config
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

            // Snap to compact mode when close
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
    public void tick() {
        super.tick();

        entityList.tick();

        if (minecraft != null && minecraft.player != null) {
            ChatCapability.get(minecraft.player).ifPresent(data -> {
                if (data.getRevision() != lastRevision) {
                    if (selectedEntityId != null) {
                        data.markAsRead(selectedEntityId);
                        NetworkHandler.CHANNEL.sendToServer(new MarkAsReadPacket(selectedEntityId));
                    }

                    lastRevision = data.getRevision();

                    List<String> entityIds = data.getEntityIds();
                    entityList.setEntities(data, entityIds);
                    refreshChatHistory();
                }
            });
        }
    }
}
