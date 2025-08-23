package com.shulkerking.managers;

import com.shulkerking.ShulkerKingPlugin;
import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorManager {
    
    private final ShulkerKingPlugin plugin;
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    
    public ColorManager(ShulkerKingPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Process color codes in text (both HEX and legacy)
     */
    public String colorize(String text) {
        if (text == null) return null;
        
        String result = text;
        
        // Process HEX colors if enabled
        if (plugin.getConfig().getBoolean("colors.hex-colors", true)) {
            result = processHexColors(result);
        }
        
        // Process legacy colors if enabled
        if (plugin.getConfig().getBoolean("colors.legacy-colors", true)) {
            result = ChatColor.translateAlternateColorCodes('&', result);
        }
        
        return result;
    }
    
    /**
     * Process HEX color codes (&#FFFFFF format)
     */
    private String processHexColors(String text) {
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        
        while (matcher.find()) {
            String hexCode = matcher.group(1);
            String replacement = convertHexToMinecraft(hexCode);
            matcher.appendReplacement(buffer, replacement);
        }
        matcher.appendTail(buffer);
        
        return buffer.toString();
    }
    
    /**
     * Convert HEX code to Minecraft color format
     */
    private String convertHexToMinecraft(String hexCode) {
        StringBuilder result = new StringBuilder("§x");
        for (char c : hexCode.toCharArray()) {
            result.append("§").append(c);
        }
        return result.toString();
    }
    
    /**
     * Get themed color for message type
     */
    public String getThemedColor(String messageType) {
        String colorPath = "colors.themes." + messageType;
        String color = plugin.getConfig().getString(colorPath, "&#FFFFFF");
        return colorize(color);
    }
    
    /**
     * Apply success theme color
     */
    public String success(String message) {
        return getThemedColor("success") + colorize(message);
    }
    
    /**
     * Apply error theme color
     */
    public String error(String message) {
        return getThemedColor("error") + colorize(message);
    }
    
    /**
     * Apply warning theme color
     */
    public String warning(String message) {
        return getThemedColor("warning") + colorize(message);
    }
    
    /**
     * Apply info theme color
     */
    public String info(String message) {
        return getThemedColor("info") + colorize(message);
    }
    
    /**
     * Apply cooldown theme color
     */
    public String cooldown(String message) {
        return getThemedColor("cooldown") + colorize(message);
    }
    
    /**
     * Strip all color codes from text
     */
    public String stripColors(String text) {
        if (text == null) return null;
        
        // Strip HEX colors
        text = HEX_PATTERN.matcher(text).replaceAll("");
        
        // Strip legacy colors
        text = ChatColor.stripColor(text);
        
        return text;
    }
}
