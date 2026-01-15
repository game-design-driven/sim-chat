package com.yardenzamir.simchat.client;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

import com.yardenzamir.simchat.SimChatMod;
import com.yardenzamir.simchat.config.ClientConfig;
import com.yardenzamir.simchat.team.TeamData;

/**
 * Client-side template processor for dynamic text in UI elements.
 * Handles templates like {team:title} using data available on the client.
 * 
 * <p>Unlike the server-side TemplateEngine, this only resolves templates
 * using client-accessible data (ClientTeamCache, Minecraft instance, etc.)
 */
@OnlyIn(Dist.CLIENT)
public final class ClientTemplateEngine {
    
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z_][a-zA-Z0-9_]*):([^}]+)}");
    
    private ClientTemplateEngine() {}

    /** Check if debug logging is enabled via config */
    public static boolean isDebugEnabled() {
        return ClientConfig.DEBUG.get();
    }
    
    /**
     * Processes a template string, replacing placeholders with client-side values.
     * Unknown placeholders are left as-is.
     *
     * @param template The template string (may be null)
     * @return Processed string or null if input was null
     */
    @Nullable
    public static String process(@Nullable String template) {
        if (template == null || template.isEmpty() || !template.contains("{")) {
            return template;
        }
        
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        
        while (matcher.find()) {
            String prefix = matcher.group(1);
            String name = matcher.group(2);
            
            String replacement = resolve(prefix, name);
            if (replacement == null) {
                replacement = matcher.group(0); // Keep original if unresolved
            }
            
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        
        if (isDebugEnabled()) {
            SimChatMod.LOGGER.info("[ClientTemplate] '{}' -> '{}'", template, result);
        }
        
        return result.toString();
    }
    
    @Nullable
    private static String resolve(String prefix, String name) {
        return switch (prefix) {
            case "team" -> resolveTeam(name);
            case "data" -> resolveData(name);
            case "world" -> resolveWorld(name);
            default -> null;
        };
    }
    
    @Nullable
    private static String resolveTeam(String name) {
        TeamData team = ClientTeamCache.getTeam();
        if (team == null) {
            return null;
        }
        
        return switch (name) {
            case "title" -> team.getTitle();
            case "id" -> team.getId();
            case "memberCount" -> String.valueOf(team.getMemberCount());
            case "color" -> TeamData.getColorName(team.getColor());
            default -> null;
        };
    }

    @Nullable
    private static String resolveData(String name) {
        TeamData team = ClientTeamCache.getTeam();
        if (team == null) {
            return null;
        }
        Object value = team.getData(name);
        return value != null ? value.toString() : null;
    }

    @Nullable
    private static String resolveWorld(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        long dayTime = mc.level.getDayTime();
        return switch (name) {
            case "day" -> String.valueOf(dayTime / 24000);
            case "time" -> String.valueOf(dayTime % 24000);
            case "dimension" -> mc.level.dimension().location().toString();
            case "weather" -> mc.level.isRaining() ? (mc.level.isThundering() ? "thunder" : "rain") : "clear";
            default -> null;
        };
    }

    public static boolean hasPlaceholders(String text) {
        return text != null && PLACEHOLDER_PATTERN.matcher(text).find();
    }
}
