package com.xreatlabs.xdiscordultimate.utils;

import net.md_5.bungee.api.ChatColor;
import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtils {
    
    // Pattern for hex colors: &#RRGGBB or <#RRGGBB>
    private static final Pattern HEX_PATTERN = Pattern.compile("(?:&#|<#)([A-Fa-f0-9]{6})>?");
    
    // Pattern for gradients: <gradient:#RRGGBB:#RRGGBB>text</gradient>
    private static final Pattern GRADIENT_PATTERN = Pattern.compile("<gradient:?(#[A-Fa-f0-9]{6}):(#[A-Fa-f0-9]{6})>(.+?)</gradient>");
    
    // Pattern for rainbow: <rainbow>text</rainbow>
    private static final Pattern RAINBOW_PATTERN = Pattern.compile("<rainbow>(.+?)</rainbow>");
    
    // Pattern for bold/italic/etc MiniMessage style
    private static final Pattern BOLD_PATTERN = Pattern.compile("<bold>(.+?)</bold>|<b>(.+?)</b>");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("<italic>(.+?)</italic>|<i>(.+?)</i>|<em>(.+?)</em>");
    private static final Pattern UNDERLINE_PATTERN = Pattern.compile("<underlined>(.+?)</underlined>|<u>(.+?)</u>");
    private static final Pattern STRIKETHROUGH_PATTERN = Pattern.compile("<strikethrough>(.+?)</strikethrough>|<st>(.+?)</st>");
    
    /**
     * Parse all color formats: legacy (&), hex (&#RRGGBB), gradients, rainbow
     */
    public static String parse(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        
        // Process gradients first
        message = parseGradients(message);
        
        // Process rainbow
        message = parseRainbow(message);
        
        // Process MiniMessage formatting tags
        message = parseFormattingTags(message);
        
        // Process hex colors
        message = parseHexColors(message);
        
        // Process legacy color codes (&) using Bukkit's ChatColor
        message = org.bukkit.ChatColor.translateAlternateColorCodes('&', message);
        
        return message;
    }
    
    /**
     * Parse hex color codes (&#RRGGBB or <#RRGGBB>)
     */
    public static String parseHexColors(String message) {
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();
        
        while (matcher.find()) {
            String hex = matcher.group(1);
            String replacement = hexToMinecraft(hex);
            matcher.appendReplacement(buffer, replacement);
        }
        matcher.appendTail(buffer);
        
        return buffer.toString();
    }
    
    /**
     * Convert hex to Minecraft color code format
     */
    private static String hexToMinecraft(String hex) {
        StringBuilder result = new StringBuilder(ChatColor.COLOR_CHAR + "x");
        for (char c : hex.toCharArray()) {
            result.append(ChatColor.COLOR_CHAR).append(c);
        }
        return result.toString();
    }
    
    /**
     * Parse gradient tags: <gradient:#FF0000:#0000FF>text</gradient>
     */
    public static String parseGradients(String message) {
        Matcher matcher = GRADIENT_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();
        
        while (matcher.find()) {
            String startHex = matcher.group(1);
            String endHex = matcher.group(2);
            String text = matcher.group(3);
            
            String gradientText = applyGradient(text, 
                Color.decode(startHex), 
                Color.decode(endHex));
            
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(gradientText));
        }
        matcher.appendTail(buffer);
        
        return buffer.toString();
    }
    
    /**
     * Apply gradient colors to text
     */
    public static String applyGradient(String text, Color startColor, Color endColor) {
        StringBuilder result = new StringBuilder();
        int length = text.length();
        
        // Remove existing color codes from text for gradient calculation
        String plainText = org.bukkit.ChatColor.stripColor(org.bukkit.ChatColor.translateAlternateColorCodes('&', text));
        int plainLength = plainText.length();
        
        if (plainLength == 0) return text;
        
        int plainIndex = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            
            // Skip color code characters
            if (c == '&' || c == ChatColor.COLOR_CHAR) {
                if (i + 1 < text.length()) {
                    result.append(c).append(text.charAt(i + 1));
                    i++;
                    continue;
                }
            }
            
            // Calculate interpolated color
            float ratio = plainLength > 1 ? (float) plainIndex / (plainLength - 1) : 0;
            int red = (int) (startColor.getRed() + ratio * (endColor.getRed() - startColor.getRed()));
            int green = (int) (startColor.getGreen() + ratio * (endColor.getGreen() - startColor.getGreen()));
            int blue = (int) (startColor.getBlue() + ratio * (endColor.getBlue() - startColor.getBlue()));
            
            String hex = String.format("%02x%02x%02x", red, green, blue);
            result.append(hexToMinecraft(hex)).append(c);
            plainIndex++;
        }
        
        return result.toString();
    }
    
    /**
     * Parse rainbow tags: <rainbow>text</rainbow>
     */
    public static String parseRainbow(String message) {
        Matcher matcher = RAINBOW_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();
        
        while (matcher.find()) {
            String text = matcher.group(1);
            String rainbowText = applyRainbow(text);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(rainbowText));
        }
        matcher.appendTail(buffer);
        
        return buffer.toString();
    }
    
    /**
     * Apply rainbow colors to text
     */
    public static String applyRainbow(String text) {
        StringBuilder result = new StringBuilder();
        String plainText = org.bukkit.ChatColor.stripColor(org.bukkit.ChatColor.translateAlternateColorCodes('&', text));
        int plainLength = plainText.length();
        
        if (plainLength == 0) return text;
        
        // Rainbow colors
        Color[] rainbowColors = {
            new Color(255, 0, 0),    // Red
            new Color(255, 127, 0),  // Orange
            new Color(255, 255, 0),  // Yellow
            new Color(0, 255, 0),    // Green
            new Color(0, 255, 255),  // Cyan
            new Color(0, 0, 255),    // Blue
            new Color(127, 0, 255)   // Purple
        };
        
        int plainIndex = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            
            // Skip color code characters
            if (c == '&' || c == ChatColor.COLOR_CHAR) {
                if (i + 1 < text.length()) {
                    i++;
                    continue;
                }
            }
            
            // Calculate rainbow position
            float position = (float) plainIndex / plainLength * (rainbowColors.length - 1);
            int colorIndex = (int) position;
            float colorRatio = position - colorIndex;
            
            if (colorIndex >= rainbowColors.length - 1) {
                colorIndex = rainbowColors.length - 2;
                colorRatio = 1.0f;
            }
            
            Color startColor = rainbowColors[colorIndex];
            Color endColor = rainbowColors[colorIndex + 1];
            
            int red = (int) (startColor.getRed() + colorRatio * (endColor.getRed() - startColor.getRed()));
            int green = (int) (startColor.getGreen() + colorRatio * (endColor.getGreen() - startColor.getGreen()));
            int blue = (int) (startColor.getBlue() + colorRatio * (endColor.getBlue() - startColor.getBlue()));
            
            String hex = String.format("%02x%02x%02x", red, green, blue);
            result.append(hexToMinecraft(hex)).append(c);
            plainIndex++;
        }
        
        return result.toString();
    }
    
    /**
     * Parse MiniMessage-style formatting tags
     */
    public static String parseFormattingTags(String message) {
        // Bold
        message = BOLD_PATTERN.matcher(message).replaceAll(result -> {
            String content = result.group(1) != null ? result.group(1) : result.group(2);
            return "&l" + content + "&r";
        });
        
        // Italic
        message = ITALIC_PATTERN.matcher(message).replaceAll(result -> {
            String content = result.group(1) != null ? result.group(1) : 
                           (result.group(2) != null ? result.group(2) : result.group(3));
            return "&o" + content + "&r";
        });
        
        // Underline
        message = UNDERLINE_PATTERN.matcher(message).replaceAll(result -> {
            String content = result.group(1) != null ? result.group(1) : result.group(2);
            return "&n" + content + "&r";
        });
        
        // Strikethrough
        message = STRIKETHROUGH_PATTERN.matcher(message).replaceAll(result -> {
            String content = result.group(1) != null ? result.group(1) : result.group(2);
            return "&m" + content + "&r";
        });
        
        // Named colors MiniMessage style
        message = message.replace("<red>", "&c").replace("</red>", "&r");
        message = message.replace("<green>", "&a").replace("</green>", "&r");
        message = message.replace("<blue>", "&9").replace("</blue>", "&r");
        message = message.replace("<yellow>", "&e").replace("</yellow>", "&r");
        message = message.replace("<gold>", "&6").replace("</gold>", "&r");
        message = message.replace("<aqua>", "&b").replace("</aqua>", "&r");
        message = message.replace("<white>", "&f").replace("</white>", "&r");
        message = message.replace("<gray>", "&7").replace("</gray>", "&r");
        message = message.replace("<dark_gray>", "&8").replace("</dark_gray>", "&r");
        message = message.replace("<dark_red>", "&4").replace("</dark_red>", "&r");
        message = message.replace("<dark_green>", "&2").replace("</dark_green>", "&r");
        message = message.replace("<dark_blue>", "&1").replace("</dark_blue>", "&r");
        message = message.replace("<dark_aqua>", "&3").replace("</dark_aqua>", "&r");
        message = message.replace("<dark_purple>", "&5").replace("</dark_purple>", "&r");
        message = message.replace("<light_purple>", "&d").replace("</light_purple>", "&r");
        message = message.replace("<black>", "&0").replace("</black>", "&r");
        
        // Reset
        message = message.replace("<reset>", "&r").replace("<r>", "&r");
        
        return message;
    }
    
    /**
     * Create a simple gradient string
     */
    public static String gradient(String text, String startHex, String endHex) {
        return applyGradient(text, Color.decode(startHex), Color.decode(endHex));
    }
    
    /**
     * Strip all colors including hex
     */
    public static String stripAll(String message) {
        // First strip hex colors
        message = HEX_PATTERN.matcher(message).replaceAll("");
        // Then strip regular colors
        return org.bukkit.ChatColor.stripColor(message);
    }
    
    /**
     * Pre-defined gradient presets
     */
    public static String xdiscordGradient(String text) {
        return applyGradient(text, new Color(85, 255, 255), new Color(85, 85, 255)); // Aqua to Blue
    }
    
    public static String fireGradient(String text) {
        return applyGradient(text, new Color(255, 85, 85), new Color(255, 170, 0)); // Red to Orange
    }
    
    public static String oceanGradient(String text) {
        return applyGradient(text, new Color(0, 170, 170), new Color(85, 85, 255)); // Dark Aqua to Blue
    }
    
    public static String sunsetGradient(String text) {
        return applyGradient(text, new Color(255, 85, 255), new Color(255, 170, 0)); // Pink to Orange
    }
    
    public static String forestGradient(String text) {
        return applyGradient(text, new Color(85, 255, 85), new Color(0, 170, 0)); // Light Green to Dark Green
    }
    
    public static String goldGradient(String text) {
        return applyGradient(text, new Color(255, 255, 85), new Color(255, 170, 0)); // Yellow to Gold
    }
}
