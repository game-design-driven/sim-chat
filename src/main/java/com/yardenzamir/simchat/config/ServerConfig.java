package com.yardenzamir.simchat.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ServerConfig {

    public static final ForgeConfigSpec SPEC;

    // Typing Delay
    public static final ForgeConfigSpec.DoubleValue TYPING_DELAY_MIN;
    public static final ForgeConfigSpec.DoubleValue TYPING_DELAY_MAX;
    public static final ForgeConfigSpec.DoubleValue TYPING_CHARS_PER_SECOND;

    // Permissions
    public static final ForgeConfigSpec.IntValue COMMAND_PERMISSION_LEVEL;

    // History sync
    public static final ForgeConfigSpec.IntValue INITIAL_SYNC_MESSAGE_COUNT;
    public static final ForgeConfigSpec.IntValue MAX_LAZY_LOAD_BATCH_SIZE;

    // Debug
    public static final ForgeConfigSpec.BooleanValue DEBUG;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Typing Delay Settings",
                "Controls how long the 'typing' indicator shows before messages appear.",
                "Delay is calculated as: text.length / charsPerSecond, clamped to min/max.")
                .push("typingDelay");
        TYPING_DELAY_MIN = builder
                .comment("Minimum typing delay in seconds")
                .defineInRange("minDelay", 0.3, 0.0, 30.0);
        TYPING_DELAY_MAX = builder
                .comment("Maximum typing delay in seconds")
                .defineInRange("maxDelay", 3.0, 1.0, 60.0);
        TYPING_CHARS_PER_SECOND = builder
                .comment("Simulated typing speed (characters per second)")
                .defineInRange("charsPerSecond", 100.0, 1.0, 100.0);
        builder.pop();

        builder.comment("Permission Settings").push("permissions");
        COMMAND_PERMISSION_LEVEL = builder
                .comment("Required permission level to use /simchat commands (0-4)",
                        "0 = all players, 1 = moderators, 2 = gamemaster, 3 = admin, 4 = owner")
                .defineInRange("commandPermissionLevel", 4, 0, 4);
        builder.pop();

        builder.comment("History Sync Settings").push("historySync");
        INITIAL_SYNC_MESSAGE_COUNT = builder
                .comment("How many recent messages per conversation to send on sync")
                .defineInRange("initialMessageCount", 30, 0, 5000);
        MAX_LAZY_LOAD_BATCH_SIZE = builder
                .comment("Server-side cap for lazy load batch size")
                .defineInRange("maxLazyLoadBatchSize", 100, 10, 2000);
        builder.pop();

        builder.comment("Debug Settings").push("debug");
        DEBUG = builder
                .comment("Enable verbose debug logging")
                .define("enabled", false);
        builder.pop();

        SPEC = builder.build();
    }
}
