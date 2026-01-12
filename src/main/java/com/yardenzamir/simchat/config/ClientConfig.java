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

    // Sort mode (0 = recent, 1 = alphabetical)
    public static final ForgeConfigSpec.IntValue SIDEBAR_SORT_MODE;

    // Team settings
    public static final ForgeConfigSpec.BooleanValue NOTIFY_TEAMMATE_ACTIONS;
    public static final ForgeConfigSpec.BooleanValue TEAMMATE_ACTION_SOUND;

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
        SIDEBAR_SORT_MODE = builder
                .comment("Sort mode for conversation list (0 = Recent, 1 = Alphabetical)")
                .defineInRange("sidebarSortMode", 0, 0, 1);
        builder.pop();

        builder.comment("Team Settings").push("team");
        NOTIFY_TEAMMATE_ACTIONS = builder
                .comment("Show toast notifications when a teammate clicks an action")
                .define("notifyTeammateActions", true);
        TEAMMATE_ACTION_SOUND = builder
                .comment("Play notification sound when a teammate clicks an action")
                .define("teammateActionSound", true);
        builder.pop();

        SPEC = builder.build();
    }
}
