package com.yardenzamir.simchat.config;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import net.minecraftforge.common.ForgeConfigSpec;

public class ServerConfig {

    public static final ForgeConfigSpec SPEC;

    // Typing Delay
    public static final ForgeConfigSpec.DoubleValue TYPING_DELAY_MIN;
    public static final ForgeConfigSpec.DoubleValue TYPING_DELAY_MAX;
    public static final ForgeConfigSpec.DoubleValue TYPING_CHARS_PER_SECOND;

    // Command permissions
    public static final Map<String, ForgeConfigSpec.IntValue> COMMAND_PERMISSIONS = new LinkedHashMap<>();

    // Team join behavior
    public static final ForgeConfigSpec.ConfigValue<String> JOIN_BEHAVIOR;
    public static final ForgeConfigSpec.IntValue JOIN_SIZE_CAP;

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
        commandPermission(builder, "send", 4, "Permission to use /simchat send");
        commandPermission(builder, "system", 4, "Permission to use /simchat system");
        commandPermission(builder, "clear", 4, "Permission to use /simchat clear");
        commandPermission(builder, "open", 4, "Permission to use /simchat open");
        commandPermission(builder, "openmessage", 0, "Permission to use /simchat openmessage");
        commandPermission(builder, "team.create", 4, "Permission to use /simchat team create");
        commandPermission(builder, "team.invite", 4, "Permission to use /simchat team invite");
        commandPermission(builder, "team.join", 4, "Permission to use /simchat team join");
        commandPermission(builder, "team.list", 4, "Permission to use /simchat team list");
        commandPermission(builder, "team.info", 4, "Permission to use /simchat team info");
        commandPermission(builder, "team.title", 4, "Permission to use /simchat team title");
        commandPermission(builder, "team.color", 4, "Permission to use /simchat team color");
        commandPermission(builder, "callback.list", 4, "Permission to use /simchat callback list");
        commandPermission(builder, "callback.run", 4, "Permission to use /simchat callback run");
        commandPermission(builder, "callback.reload", 4, "Permission to use /simchat reload");
        commandPermission(builder, "data.get", 4, "Permission to use /simchat data get");
        commandPermission(builder, "data.set", 4, "Permission to use /simchat data set");
        commandPermission(builder, "data.add", 4, "Permission to use /simchat data add");
        commandPermission(builder, "data.remove", 4, "Permission to use /simchat data remove");
        commandPermission(builder, "data.list", 4, "Permission to use /simchat data list");
        builder.pop();

        builder.comment("Team Join Settings").push("teamJoin");
        JOIN_BEHAVIOR = builder
                .comment("Behavior for players joining an existing world.",
                        "join_largest = join the largest existing team",
                        "join_smallest = join the smallest existing team",
                        "join_random = join a random existing team",
                        "create_new = always create a new team")
                .define("joinBehavior", JoinBehavior.JOIN_LARGEST.getId());
        JOIN_SIZE_CAP = builder
                .comment("Maximum members for auto-join (-1 disables cap).",
                        "If no team is eligible, a new team is created.")
                .defineInRange("joinSizeCap", -1, -1, Integer.MAX_VALUE);
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

    public static JoinBehavior getJoinBehavior() {
        return JoinBehavior.fromConfig(JOIN_BEHAVIOR.get());
    }

    public enum JoinBehavior {
        JOIN_LARGEST("join_largest"),
        JOIN_SMALLEST("join_smallest"),
        JOIN_RANDOM("join_random"),
        CREATE_NEW("create_new");

        private final String id;

        JoinBehavior(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public static JoinBehavior fromConfig(String value) {
            if (value != null) {
                String normalized = value.trim().toLowerCase(Locale.ROOT);
                for (JoinBehavior behavior : values()) {
                    if (behavior.id.equals(normalized)) {
                        return behavior;
                    }
                }
            }
            return JOIN_LARGEST;
        }
    }

    private static void commandPermission(ForgeConfigSpec.Builder builder, String key, int defaultLevel, String comment) {
        COMMAND_PERMISSIONS.put(key, builder
                .comment(comment,
                        "0 = all players, 1 = moderators, 2 = gamemaster, 3 = admin, 4 = owner")
                .defineInRange(key, defaultLevel, 0, 4));
    }

    public static int getCommandPermission(String key) {
        ForgeConfigSpec.IntValue value = COMMAND_PERMISSIONS.get(key);
        if (value == null) {
            return 4;
        }
        return value.get();
    }
}
