package com.yardenzamir.simchat.condition;

import com.yardenzamir.simchat.SimChatMod;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Processes template strings with placeholders like {prefix:name}.
 * Extensible via registered resolvers for different prefixes.
 *
 * <p>Example: "Hello {kjs:playerName}, you have {kjs:repLevel} reputation"
 */
public final class TemplateEngine {
    // Matches {prefix:name} patterns
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z_][a-zA-Z0-9_]*):([^}]+)}");

    private static final Map<String, TemplateResolver> resolvers = new HashMap<>();

    private TemplateEngine() {}

    /**
     * Registers a resolver for a given prefix.
     *
     * @param prefix The prefix (e.g., "kjs" for {kjs:name})
     * @param resolver The resolver implementation
     */
    public static void registerResolver(String prefix, TemplateResolver resolver) {
        resolvers.put(prefix, resolver);
        SimChatMod.LOGGER.debug("Registered template resolver for prefix: {}", prefix);
    }

    /**
     * Removes a resolver by prefix.
     */
    public static void unregisterResolver(String prefix) {
        resolvers.remove(prefix);
    }

    /**
     * Processes a template string, replacing all {prefix:name} placeholders.
     * Unknown prefixes or names are left as-is.
     *
     * @param template The template string
     * @param ctx The callback context for resolution
     * @return The processed string with placeholders replaced
     */
    public static String process(String template, CallbackContext ctx) {
        if (template == null || template.isEmpty() || !template.contains("{")) {
            return template;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String prefix = matcher.group(1);
            String name = matcher.group(2);

            String replacement = resolve(prefix, name, ctx);
            // If resolution failed, keep original placeholder
            if (replacement == null) {
                replacement = matcher.group(0);
            }

            // Escape replacement string for appendReplacement
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Resolves a single placeholder value.
     *
     * @param prefix The prefix (resolver type)
     * @param name The name within that prefix
     * @param ctx The callback context
     * @return Resolved value or null if not found
     */
    private static String resolve(String prefix, String name, CallbackContext ctx) {
        TemplateResolver resolver = resolvers.get(prefix);
        if (resolver == null) {
            SimChatMod.LOGGER.warn("Unknown template prefix: {}", prefix);
            return null;
        }

        try {
            return resolver.resolve(name, ctx);
        } catch (Exception e) {
            SimChatMod.LOGGER.error("Error resolving template {{{}: {}}}: {}", prefix, name, e.getMessage());
            return null;
        }
    }

    /**
     * Checks if a string contains any template placeholders.
     */
    public static boolean hasPlaceholders(String text) {
        return text != null && PLACEHOLDER_PATTERN.matcher(text).find();
    }

    // Static initializer to register built-in resolvers
    static {
        // KubeJS callback resolver
        registerResolver("kjs", (name, ctx) -> CallbackRegistry.evaluateString(name, ctx));

        // Player data resolver - for common player properties
        registerResolver("player", (name, ctx) -> {
            if (ctx.player() == null) return null;
            return switch (name) {
                case "name" -> ctx.player().getName().getString();
                case "uuid" -> ctx.player().getStringUUID();
                case "health" -> String.valueOf((int) ctx.player().getHealth());
                case "maxHealth" -> String.valueOf((int) ctx.player().getMaxHealth());
                case "level" -> String.valueOf(ctx.player().experienceLevel);
                case "x" -> String.valueOf((int) ctx.player().getX());
                case "y" -> String.valueOf((int) ctx.player().getY());
                case "z" -> String.valueOf((int) ctx.player().getZ());
                default -> null;
            };
        });

        // Team data resolver
        registerResolver("team", (name, ctx) -> {
            if (ctx.team() == null) return null;
            return switch (name) {
                case "id" -> ctx.team().getId();
                case "title" -> ctx.team().getTitle();
                case "memberCount" -> String.valueOf(ctx.team().getMemberCount());
                case "color" -> String.valueOf(ctx.team().getColor());
                default -> null;
            };
        });

        // Data resolver - {data:keyname}
        registerResolver("data", (name, ctx) -> {
            if (ctx.team() == null) return null;
            Object val = ctx.team().getData(name);
            return val != null ? val.toString() : null;
        });

        // World/time resolver
        registerResolver("world", (name, ctx) -> {
            if (ctx.player() == null) return null;
            var level = ctx.player().level();
            return switch (name) {
                case "day" -> String.valueOf(level.getDayTime() / 24000);
                case "time" -> String.valueOf(level.getDayTime() % 24000);
                case "dimension" -> level.dimension().location().toString();
                case "weather" -> level.isRaining() ? (level.isThundering() ? "thunder" : "rain") : "clear";
                default -> null;
            };
        });
    }
}
