package com.yardenzamir.simchat.client;

import com.yardenzamir.simchat.config.ClientConfig;
import com.yardenzamir.simchat.data.ChatMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * Toast notification for new chat messages.
 * Shows sender image, name, message preview, and keybind hint.
 */
public class ChatToast implements Toast {

    private static final int TOAST_WIDTH = 160;
    private static final int TOAST_HEIGHT = 42;
    private static final int ICON_SIZE = 34;
    private static final int PADDING = 4;
    private static final int TEXT_SPACING = 1;

    private final String senderName;
    private final String messagePreview;
    private final ResourceLocation iconTexture;
    private final String keybindHint;
    private final boolean showKeybindHint;
    private long firstRender = -1;

    public ChatToast(ChatMessage message, String keybindName, boolean showKeybindHint) {
        this.senderName = message.senderName();
        this.messagePreview = truncateMessage(message.content(), 100);
        this.iconTexture = AvatarManager.getTexture(message.senderImageId());
        this.keybindHint = "[" + keybindName + "]";
        this.showKeybindHint = showKeybindHint;
    }

    private static String truncateMessage(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    @Override
    public Visibility render(GuiGraphics graphics, ToastComponent toastComponent, long timeSinceLastVisible) {
        if (firstRender == -1) {
            firstRender = timeSinceLastVisible;
        }

        Minecraft mc = toastComponent.getMinecraft();
        Font font = mc.font;

        // Background
        graphics.fill(0, 0, TOAST_WIDTH, TOAST_HEIGHT, 0xF0202030);
        graphics.renderOutline(0, 0, TOAST_WIDTH, TOAST_HEIGHT, 0xFF404060);

        // Icon - sample full texture, scale to ICON_SIZE (assumes square-ish avatars)
        graphics.blit(iconTexture, PADDING, PADDING, ICON_SIZE, ICON_SIZE, 0, 0, 256, 256, 256, 256);

        int textX = PADDING + ICON_SIZE + PADDING;
        int textMaxWidth = TOAST_WIDTH - textX - PADDING;

        // Sender name
        graphics.drawString(font, senderName, textX, PADDING, 0xFF88AAFF, false);

        // Message preview (truncated to fit)
        String preview = messagePreview;
        while (font.width(preview) > textMaxWidth && preview.length() > 3) {
            preview = preview.substring(0, preview.length() - 4) + "...";
        }
        graphics.drawString(font, preview, textX, PADDING + font.lineHeight + TEXT_SPACING, 0xFFCCCCCC, false);

        // Keybind hint at bottom (only when not already in chat)
        if (showKeybindHint) {
            int hintWidth = font.width(keybindHint);
            graphics.drawString(font, keybindHint,
                    TOAST_WIDTH - hintWidth - PADDING,
                    TOAST_HEIGHT - font.lineHeight - 2,
                    0xFF666688, false);
        }

        long elapsed = timeSinceLastVisible - firstRender;
        long displayTime = (long) (ClientConfig.TOAST_DURATION.get() * 1000);
        return elapsed >= displayTime ? Visibility.HIDE : Visibility.SHOW;
    }

    @Override
    public int width() {
        return TOAST_WIDTH;
    }

    @Override
    public int height() {
        return TOAST_HEIGHT;
    }
}
