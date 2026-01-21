package com.yardenzamir.simchat.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ClientConfig {

    public static final ForgeConfigSpec SPEC;

    // Sound
    public static final ForgeConfigSpec.ConfigValue<String> NOTIFICATION_SOUND;
    public static final ForgeConfigSpec.DoubleValue NOTIFICATION_VOLUME;

    // Toast
    public static final ForgeConfigSpec.BooleanValue SHOW_TOASTS;
    public static final ForgeConfigSpec.DoubleValue TOAST_DURATION;

    // Animation
    public static final ForgeConfigSpec.IntValue TYPING_ANIMATION_SPEED;

    // UI Dimensions
    public static final ForgeConfigSpec.IntValue SIDEBAR_WIDTH;
    public static final ForgeConfigSpec.IntValue AVATAR_SIZE;
    public static final ForgeConfigSpec.IntValue MESSAGE_PADDING;
    public static final ForgeConfigSpec.IntValue ENTITY_LIST_ITEM_HEIGHT;
    public static final ForgeConfigSpec.ConfigValue<String> CHAT_BACKGROUND_COLOR;

    // Colors
    public static final ForgeConfigSpec.ConfigValue<String> SIDEBAR_BACKGROUND_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> DIVIDER_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> DIVIDER_HOVER_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> HEADER_TEXT_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> HEADER_SUBTITLE_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> EMPTY_STATE_TEXT_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> HEADER_SEPARATOR_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> REFRESH_BUTTON_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> REFRESH_BUTTON_HOVER_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> REFRESH_TEXT_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> SORT_BUTTON_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> SORT_BUTTON_HOVER_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> SORT_TEXT_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> COMPACT_HINT_COLOR;

    public static final ForgeConfigSpec.ConfigValue<String> SCROLLBAR_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> INPUT_BG_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> OUTPUT_BG_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> DISABLED_BG_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> PLAYER_NAME_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> ENTITY_NAME_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> SUBTITLE_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> DAY_TEXT_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> MESSAGE_TEXT_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> DISABLED_TEXT_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> WHITE_TEXT_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> SYSTEM_TEXT_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> BUTTON_DEFAULT_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> BUTTON_HOVER_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> BORDER_DISABLED_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> BORDER_HOVER_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> BORDER_DEFAULT_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> FOCUS_BORDER_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> CONTEXT_MENU_BG_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> CONTEXT_MENU_BORDER_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> CONTEXT_MENU_TEXT_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> CONTEXT_MENU_HIGHLIGHT_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> INPUT_VALID_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> INPUT_INVALID_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> INPUT_FIELD_BG_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> INPUT_FIELD_BORDER_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> INPUT_FIELD_BORDER_FOCUSED_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> INPUT_CURSOR_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> INPUT_SEND_ENABLED_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> INPUT_SEND_DISABLED_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> TOAST_SENDER_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> TOAST_PREVIEW_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> TOAST_BG_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> TOAST_BORDER_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> TOAST_HINT_COLOR;

    // History loading
    public static final ForgeConfigSpec.IntValue LAZY_LOAD_BATCH_SIZE;
    public static final ForgeConfigSpec.IntValue LAZY_LOAD_THRESHOLD;
    public static final ForgeConfigSpec.IntValue CLOSED_CACHE_SIZE;

    // Templates
    public static final ForgeConfigSpec.IntValue TEMPLATE_REQUESTS_PER_TICK;

    // Sort mode (0 = recent, 1 = alphabetical)
    public static final ForgeConfigSpec.IntValue SIDEBAR_SORT_MODE;

    // Team settings
    public static final ForgeConfigSpec.BooleanValue NOTIFY_TEAMMATE_ACTIONS;
    public static final ForgeConfigSpec.BooleanValue TEAMMATE_ACTION_SOUND;

    // Debug
    public static final ForgeConfigSpec.BooleanValue DEBUG;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Sound Settings").push("sound");
        NOTIFICATION_SOUND = builder
                .comment("Sound to play when receiving a message.",
                        "Use Minecraft resource location format (e.g., 'minecraft:block.note_block.bell').",
                        "Leave empty to disable notification sound.")
                .define("notificationSound", "minecraft:block.note_block.bell");
        NOTIFICATION_VOLUME = builder
                .comment("Volume for notification sound (0.0 - 1.0)")
                .defineInRange("notificationVolume", 0.5, 0.0, 1.0);
        builder.pop();

        builder.comment("Toast Notification Settings").push("toast");
        SHOW_TOASTS = builder
                .comment("Show toast notifications when receiving messages")
                .define("showToasts", true);
        TOAST_DURATION = builder
                .comment("How long toasts are displayed (seconds)")
                .defineInRange("toastDuration", 5.0, 1.0, 30.0);
        builder.pop();

        builder.comment("Animation Settings").push("animation");
        TYPING_ANIMATION_SPEED = builder
                .comment("Speed of typing indicator animation (milliseconds per dot)")
                .defineInRange("typingAnimationSpeed", 400, 100, 2000);
        builder.pop();

        builder.comment("UI Dimension Settings").push("ui");
        SIDEBAR_WIDTH = builder
                .comment("Width of the entity list sidebar (pixels). Drag the divider in-game to resize.")
                .defineInRange("sidebarWidth", 240, 60, 2000);
        AVATAR_SIZE = builder
                .comment("Size of avatar images in chat (pixels)")
                .defineInRange("avatarSize", 32, 16, 64);
        MESSAGE_PADDING = builder
                .comment("Padding between messages (pixels)")
                .defineInRange("messagePadding", 8, 2, 20);
        ENTITY_LIST_ITEM_HEIGHT = builder
                .comment("Height of each entity in the sidebar list (pixels)")
                .defineInRange("entityListItemHeight", 48, 32, 80);
        CHAT_BACKGROUND_COLOR = builder
                .comment("Background color for the chat panel (ARGB hex, e.g. 0xDD12121F)")
                .define("chatBackgroundColor", "0xDD12121F");
        SIDEBAR_SORT_MODE = builder
                .comment("Sort mode for conversation list (0 = Recent, 1 = Alphabetical)")
                .defineInRange("sidebarSortMode", 0, 0, 1);
        builder.pop();

        builder.comment("Color Settings").push("colors");
        SIDEBAR_BACKGROUND_COLOR = builder.define("sidebarBackgroundColor", "0xDD1A1A2E");
        DIVIDER_COLOR = builder.define("dividerColor", "0xFF3D3D5C");
        DIVIDER_HOVER_COLOR = builder.define("dividerHoverColor", "0xFF6060A0");
        HEADER_TEXT_COLOR = builder.define("headerTextColor", "0xFFFFFFFF");
        HEADER_SUBTITLE_COLOR = builder.define("headerSubtitleColor", "0xFF888888");
        EMPTY_STATE_TEXT_COLOR = builder.define("emptyStateTextColor", "0xFF888888");
        HEADER_SEPARATOR_COLOR = builder.define("headerSeparatorColor", "0xFF3D3D5C");
        REFRESH_BUTTON_COLOR = builder.define("refreshButtonColor", "0xFF303050");
        REFRESH_BUTTON_HOVER_COLOR = builder.define("refreshButtonHoverColor", "0xFF404060");
        REFRESH_TEXT_COLOR = builder.define("refreshTextColor", "0xFFCCCCCC");
        SORT_BUTTON_COLOR = builder.define("sortButtonColor", "0xFF303050");
        SORT_BUTTON_HOVER_COLOR = builder.define("sortButtonHoverColor", "0xFF404060");
        SORT_TEXT_COLOR = builder.define("sortTextColor", "0xFFCCCCCC");
        COMPACT_HINT_COLOR = builder.define("compactHintColor", "0xFF888888");

        SCROLLBAR_COLOR = builder.define("scrollbarColor", "0x80FFFFFF");
        INPUT_BG_COLOR = builder.define("inputBgColor", "0xFF0968B6");
        OUTPUT_BG_COLOR = builder.define("outputBgColor", "0xFFCE640B");
        DISABLED_BG_COLOR = builder.define("disabledBgColor", "0xFF404040");
        PLAYER_NAME_COLOR = builder.define("playerNameColor", "0xFF7BCB8B");
        ENTITY_NAME_COLOR = builder.define("entityNameColor", "0xFF88AAFF");
        SUBTITLE_COLOR = builder.define("subtitleColor", "0xFF888888");
        DAY_TEXT_COLOR = builder.define("dayTextColor", "0xFF666666");
        MESSAGE_TEXT_COLOR = builder.define("messageTextColor", "0xFFE0E0E0");
        DISABLED_TEXT_COLOR = builder.define("disabledTextColor", "0xFF808080");
        WHITE_TEXT_COLOR = builder.define("whiteTextColor", "0xFFFFFFFF");
        SYSTEM_TEXT_COLOR = builder.define("systemTextColor", "0xFF888888");
        BUTTON_DEFAULT_COLOR = builder.define("buttonDefaultColor", "0xFF304080");
        BUTTON_HOVER_COLOR = builder.define("buttonHoverColor", "0xFF4060A0");
        BORDER_DISABLED_COLOR = builder.define("borderDisabledColor", "0xFF606060");
        BORDER_HOVER_COLOR = builder.define("borderHoverColor", "0xFF6080C0");
        BORDER_DEFAULT_COLOR = builder.define("borderDefaultColor", "0xFF405090");
        FOCUS_BORDER_COLOR = builder.define("focusBorderColor", "0xFF6FA8FF");
        CONTEXT_MENU_BG_COLOR = builder.define("contextMenuBgColor", "0xFF202030");
        CONTEXT_MENU_BORDER_COLOR = builder.define("contextMenuBorderColor", "0xFF505070");
        CONTEXT_MENU_TEXT_COLOR = builder.define("contextMenuTextColor", "0xFFE0E0E0");
        CONTEXT_MENU_HIGHLIGHT_COLOR = builder.define("contextMenuHighlightColor", "0x2A405070");
        INPUT_VALID_COLOR = builder.define("inputValidColor", "0xFF55FF55");
        INPUT_INVALID_COLOR = builder.define("inputInvalidColor", "0xFFFF5555");
        INPUT_FIELD_BG_COLOR = builder.define("inputFieldBgColor", "0xFF202020");
        INPUT_FIELD_BORDER_COLOR = builder.define("inputFieldBorderColor", "0xFF404040");
        INPUT_FIELD_BORDER_FOCUSED_COLOR = builder.define("inputFieldBorderFocusedColor", "0xFF606060");
        INPUT_CURSOR_COLOR = builder.define("inputCursorColor", "0xFFFFFFFF");
        INPUT_SEND_ENABLED_COLOR = builder.define("inputSendEnabledColor", "0xFF308030");
        INPUT_SEND_DISABLED_COLOR = builder.define("inputSendDisabledColor", "0xFF404040");
        TOAST_SENDER_COLOR = builder.define("toastSenderColor", "0xFF88AAFF");
        TOAST_PREVIEW_COLOR = builder.define("toastPreviewColor", "0xFFCCCCCC");
        TOAST_BG_COLOR = builder.define("toastBackgroundColor", "0xF0202030");
        TOAST_BORDER_COLOR = builder.define("toastBorderColor", "0xFF404060");
        TOAST_HINT_COLOR = builder.define("toastHintColor", "0xFF666688");
        builder.pop();

        builder.comment("History Loading Settings").push("history");
        LAZY_LOAD_BATCH_SIZE = builder
                .comment("How many messages to request when scrolling up")
                .defineInRange("lazyLoadBatchSize", 30, 10, 2000);
        LAZY_LOAD_THRESHOLD = builder
                .comment("Distance from top (pixels) before requesting older messages")
                .defineInRange("lazyLoadThreshold", 100, 20, 500);
        CLOSED_CACHE_SIZE = builder
                .comment("Messages to keep per conversation after closing chat")
                .defineInRange("closedCacheSize", 400, 1, 5000);
        builder.pop();

        builder.comment("Template Settings").push("templates");
        TEMPLATE_REQUESTS_PER_TICK = builder
                .comment("How many runtime template requests to send per client tick")
                .defineInRange("requestsPerTick", 20, 1, 200);
        builder.pop();

        builder.comment("Team Settings").push("team");
        NOTIFY_TEAMMATE_ACTIONS = builder
                .comment("Show toast notifications when a teammate clicks an action")
                .define("notifyTeammateActions", true);
        TEAMMATE_ACTION_SOUND = builder
                .comment("Play notification sound when a teammate clicks an action")
                .define("teammateActionSound", true);
        builder.pop();

        builder.comment("Debug Settings").push("debug");
        DEBUG = builder
                .comment("Enable verbose debug logging")
                .define("enabled", false);
        builder.pop();

        SPEC = builder.build();
    }

    public static int getChatBackgroundColor() {
        return getColor(CHAT_BACKGROUND_COLOR, 0xDD12121F);
    }

    public static int getColor(ForgeConfigSpec.ConfigValue<String> value, int fallback) {
        String raw = value.get();
        if (raw == null || raw.isEmpty()) {
            return fallback;
        }
        try {
            long parsed = Long.decode(raw);
            return (int) (parsed & 0xFFFFFFFFL);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
