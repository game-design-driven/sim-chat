package com.yardenzamir.simchat.client;

import com.yardenzamir.simchat.team.TeamData;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        
        return result.toString();
    }
    
    @Nullable
    private static String resolve(String prefix, String name) {
        return switch (prefix) {
            case "team" -> resolveTeam(name);
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
            case "color" -> String.valueOf(team.getColor());
            default -> null;
        };
    }
}
