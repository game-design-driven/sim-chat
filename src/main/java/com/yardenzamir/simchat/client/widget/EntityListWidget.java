package com.yardenzamir.simchat.client.widget;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import com.yardenzamir.simchat.client.AvatarManager;
import com.yardenzamir.simchat.client.ClientTeamCache;
import com.yardenzamir.simchat.client.RuntimeTemplateResolver;
import com.yardenzamir.simchat.client.SortMode;
import com.yardenzamir.simchat.config.ClientConfig;
import com.yardenzamir.simchat.data.ChatMessage;
import com.yardenzamir.simchat.data.PlayerChatData;
import com.yardenzamir.simchat.team.TeamData;

public class EntityListWidget extends ObjectSelectionList<EntityListWidget.EntityEntry> {

    private static final int AVATAR_SIZE = 36;
    private static final int PADDING = 4;
    private static final int UNREAD_DOT_SIZE = 8;

    private final Consumer<String> onSelect;
    private @Nullable String selectedEntityId;
    private long tickCount = 0;
    private boolean compactMode = false;

    public EntityListWidget(Minecraft minecraft, int width, int height, int x, int y, Consumer<String> onSelect) {
        super(minecraft, width, height, y, y + height, ClientConfig.ENTITY_LIST_ITEM_HEIGHT.get());
        this.x0 = x;
        this.x1 = x + width;
        this.onSelect = onSelect;
        this.setRenderBackground(false);
        this.setRenderTopAndBottom(false);
    }

    public void setEntities(TeamData team, PlayerChatData readData, List<String> entityIds) {
        clearEntries();
        for (String entityId : entityIds) {
            int totalMessages = ClientTeamCache.getTotalMessageCount(entityId);
            ChatMessage lastMessage = team.getLastMessage(entityId);
            if (lastMessage != null) {
                RuntimeTemplateResolver.preloadMessage(lastMessage, RuntimeTemplateResolver.ResolutionPriority.LOW);
            }
            addEntry(new EntityEntry(
                entityId,
                team.getEntityDisplayName(entityId),
                team.getEntitySubtitle(entityId),
                team.getEntityImageId(entityId),
                readData.hasUnread(entityId, totalMessages),
                readData.getUnreadCount(entityId, totalMessages),
                team.isTyping(entityId),
                lastMessage
            ));
        }
        if (selectedEntityId != null) {
            setSelected(selectedEntityId);
        }
    }

    public void tick() {
        tickCount++;
    }

    public void setCompactMode(boolean compact) {
        this.compactMode = compact;
    }

    public void setSelected(String entityId) {
        this.selectedEntityId = entityId;
        for (int i = 0; i < getItemCount(); i++) {
            EntityEntry entry = getEntry(i);
            if (entry.entityId.equals(entityId)) {
                setSelected(entry);
                break;
            }
        }
    }

    @Override
    public void setSelected(@Nullable EntityEntry entry) {
        super.setSelected(entry);
        if (entry != null) {
            if (!entry.entityId.equals(selectedEntityId)) {
                onSelect.accept(entry.entityId);
            }
            selectedEntityId = entry.entityId;
        }
    }

    @Override
    public int getRowWidth() {
        return width;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Custom render - no scrollbar, just entries
        this.enableScissor(graphics);

        int itemCount = this.getItemCount();
        for (int i = 0; i < itemCount; i++) {
            int rowTop = this.getRowTop(i);
            int rowBottom = rowTop + this.itemHeight;

            if (rowBottom >= this.y0 && rowTop <= this.y1) {
                EntityEntry entry = this.getEntry(i);
                int rowLeft = this.getRowLeft();
                int rowWidth = this.getRowWidth();
                boolean hovered = this.isMouseOver(mouseX, mouseY) && this.getEntryAtPosition(mouseX, mouseY) == entry;
                entry.render(graphics, i, rowTop, rowLeft, rowWidth, this.itemHeight - 4, mouseX, mouseY, hovered, partialTick);
            }
        }

        graphics.disableScissor();
    }

    @Override
    protected void renderSelection(GuiGraphics graphics, int top, int width, int height, int outerColor, int innerColor) {
        // Disabled
    }

    public class EntityEntry extends Entry<EntityEntry> {
        final String entityId;
        final String displayName;
        final @Nullable String subtitle;
        final @Nullable String imageId;
        final boolean hasUnread;
        final int unreadCount;
        final boolean isTyping;
        final @Nullable ChatMessage lastMessage;

        public EntityEntry(String entityId, String displayName, @Nullable String subtitle,
                          @Nullable String imageId, boolean hasUnread, int unreadCount,
                          boolean isTyping, @Nullable ChatMessage lastMessage) {
            this.entityId = entityId;
            this.displayName = displayName;
            this.subtitle = subtitle;
            this.imageId = imageId;
            this.hasUnread = hasUnread;
            this.unreadCount = unreadCount;
            this.isTyping = isTyping;
            this.lastMessage = lastMessage;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                          int mouseX, int mouseY, boolean hovered, float partialTick) {
            // Same margins in both modes
            int contentLeft = left + PADDING;
            int contentRight = left + width - PADDING;
            int contentWidth = contentRight - contentLeft;

            // Selection highlight
            boolean selected = isSelectedItem(index);
            if (selected) {
                graphics.fill(contentLeft, top, contentRight, top + height, 0x40404080);
                graphics.renderOutline(contentLeft, top, contentWidth, height, 0x80606090);
            } else if (hovered) {
                graphics.fill(contentLeft, top, contentRight, top + height, 0x20404060);
            }

            int avatarY = top + (height - AVATAR_SIZE) / 2;
            int avatarX = contentLeft + PADDING;

            // Avatar
            ResourceLocation texture = AvatarManager.getTexture(imageId);
            graphics.blit(texture, avatarX, avatarY, AVATAR_SIZE, AVATAR_SIZE, 0, 0, 256, 256, 256, 256);

            // Typing dots on avatar
            if (isTyping) {
                int dotCount = (int) ((tickCount / 8) % 4);
                graphics.drawString(minecraft.font, ".".repeat(Math.max(1, dotCount)), avatarX + AVATAR_SIZE - 12, avatarY + AVATAR_SIZE - 10, 0xFFFFFFFF);
            }

            // Unread dot
            if (hasUnread) {
                int dotX = compactMode ? avatarX + AVATAR_SIZE - UNREAD_DOT_SIZE + 2 : contentRight - UNREAD_DOT_SIZE - PADDING;
                int dotY = compactMode ? avatarY - 2 : top + (height - UNREAD_DOT_SIZE) / 2;
                graphics.fill(dotX, dotY, dotX + UNREAD_DOT_SIZE, dotY + UNREAD_DOT_SIZE, 0xFF4488FF);
                if (unreadCount > 1 && unreadCount < 10) {
                    String c = String.valueOf(unreadCount);
                    graphics.drawString(minecraft.font, c, dotX + (UNREAD_DOT_SIZE - minecraft.font.width(c)) / 2, dotY, 0xFFFFFFFF);
                }
            }

            // Text only in non-compact mode
            if (!compactMode) {
                int textX = avatarX + AVATAR_SIZE + PADDING;
                int textMaxWidth = contentRight - textX - PADDING - (hasUnread ? UNREAD_DOT_SIZE + PADDING : 0);

                String resolvedName = displayName;
                @Nullable String resolvedSubtitle = subtitle;
                if (lastMessage != null) {
                    resolvedName = RuntimeTemplateResolver.resolveSenderName(lastMessage, RuntimeTemplateResolver.ResolutionPriority.LOW);
                    resolvedSubtitle = RuntimeTemplateResolver.resolveSenderSubtitle(lastMessage, RuntimeTemplateResolver.ResolutionPriority.LOW);
                }
                if (resolvedName == null || resolvedName.isEmpty()) {
                    resolvedName = entityId;
                }

                // Name and subtitle on first line
                String truncatedName = truncate(resolvedName, textMaxWidth);
                graphics.drawString(minecraft.font, truncatedName, textX, top + PADDING, hasUnread ? 0xFFFFFFFF : 0xFFAAAAAA);

                // Subtitle after name if there's space
                if (resolvedSubtitle != null && !resolvedSubtitle.isEmpty()) {
                    int nameWidth = minecraft.font.width(truncatedName);
                    int subtitleX = textX + nameWidth + 4;
                    int subtitleMaxWidth = textMaxWidth - nameWidth - 4;
                    if (subtitleMaxWidth > 20) {
                        graphics.drawString(minecraft.font, truncate(resolvedSubtitle, subtitleMaxWidth), subtitleX, top + PADDING, 0xFF666666);
                    }
                }

                String preview = isTyping ? "typing..." : (lastMessage != null ? (lastMessage.isPlayerMessage() ? "You: " : "") + RuntimeTemplateResolver.resolveContent(lastMessage, RuntimeTemplateResolver.ResolutionPriority.LOW) : "");
                if (!preview.isEmpty() && textMaxWidth > 20) {
                    graphics.drawString(minecraft.font, truncate(preview, textMaxWidth), textX, top + PADDING + minecraft.font.lineHeight + 2, isTyping ? 0xFF88AAFF : 0xFF888888);
                }
            }
        }

        private String truncate(String text, int maxWidth) {
            if (maxWidth <= 0) return "";
            if (minecraft.font.width(text) <= maxWidth) return text;
            int w = maxWidth - minecraft.font.width("...");
            if (w <= 0) return "...";
            StringBuilder sb = new StringBuilder();
            for (char c : text.toCharArray()) {
                if (minecraft.font.width(sb.toString() + c) > w) break;
                sb.append(c);
            }
            return sb + "...";
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                EntityListWidget.this.setSelected(this);
                return true;
            }
            return false;
        }

        @Override
        public Component getNarration() {
            return Component.literal(displayName != null ? displayName : entityId);
        }
    }
}
