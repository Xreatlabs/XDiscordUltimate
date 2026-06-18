package com.xreatlabs.xdiscordultimate.utils;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmbedUtils {

    private final XDiscordUltimate plugin;
    private final Map<String, Color> colorMap;
    private final Pattern hexPattern;

    // Modern color palette - muted and professional
    public static final Color BRAND_PRIMARY = new Color(0, 123, 255);     // Professional blue
    public static final Color BRAND_SECONDARY = new Color(108, 117, 125); // Neutral gray
    public static final Color SUCCESS = new Color(40, 167, 69);           // Muted green
    public static final Color WARNING = new Color(255, 193, 7);           // Muted yellow
    public static final Color ERROR = new Color(220, 53, 69);             // Muted red
    public static final Color INFO = new Color(23, 162, 184);            // Cyan
    public static final Color PREMIUM = new Color(255, 193, 7);           // Gold
    public static final Color DISCORD = new Color(88, 101, 242);          // Blurple
    
    public EmbedUtils(XDiscordUltimate plugin) {
        this.plugin = plugin;
        this.colorMap = initializeColorMap();
        this.hexPattern = Pattern.compile("#[a-fA-F0-9]{6}");
    }

    /**
     * Get player avatar URL from reliable avatar service
     */
    public static String getPlayerAvatarUrl(UUID uuid) {
        return getPlayerAvatarUrl(uuid.toString());
    }

    /**
     * Get player avatar URL from UUID string
     */
    public static String getPlayerAvatarUrl(String uuid) {
        return "https://cravatar.eu/helm/" + uuid + "/64.png";
    }

    /**
     * Get player avatar URL from username (uses name.mc for UUID lookup)
     * Falls back to minotar if lookup fails
     */
    public static String getPlayerAvatarUrlByUsername(String username) {
        return "https://minotar.net/helm/" + username + "/64.png";
    }

    /**
     * Create a branded embed with XDiscord styling
     */
    public static EmbedBuilder brandedEmbed() {
        return new EmbedBuilder()
            .setColor(BRAND_PRIMARY)
            .setFooter("XDiscordUltimate", null)
            .setTimestamp(Instant.now());
    }

    /**
     * Create a success embed
     */
    public static EmbedBuilder successEmbed(String title, String description) {
        return new EmbedBuilder()
            .setColor(SUCCESS)
            .setTitle(title)
            .setDescription(description)
            .setTimestamp(Instant.now());
    }

    /**
     * Create an error embed
     */
    public static EmbedBuilder errorEmbed(String title, String description) {
        return new EmbedBuilder()
            .setColor(ERROR)
            .setTitle(title)
            .setDescription(description)
            .setTimestamp(Instant.now());
    }

    /**
     * Create a warning embed
     */
    public static EmbedBuilder warningEmbed(String title, String description) {
        return new EmbedBuilder()
            .setColor(WARNING)
            .setTitle(title)
            .setDescription(description)
            .setTimestamp(Instant.now());
    }

    /**
     * Create an info embed
     */
    public static EmbedBuilder infoEmbed(String title, String description) {
        return new EmbedBuilder()
            .setColor(INFO)
            .setTitle(title)
            .setDescription(description)
            .setTimestamp(Instant.now());
    }

    /**
     * Create a player embed with avatar thumbnail
     */
    public static EmbedBuilder playerEmbed(Player player, String title, String description) {
        return new EmbedBuilder()
            .setColor(BRAND_PRIMARY)
            .setTitle(title)
            .setDescription(description)
            .setThumbnail(getPlayerAvatarUrl(player.getUniqueId()))
            .setFooter(player.getName(), getPlayerAvatarUrl(player.getUniqueId()))
            .setTimestamp(Instant.now());
    }

    /**
     * Create a player embed with UUID
     */
    public static EmbedBuilder playerEmbed(UUID uuid, String name, String title, String description) {
        return new EmbedBuilder()
            .setColor(BRAND_PRIMARY)
            .setTitle(title)
            .setDescription(description)
            .setThumbnail(getPlayerAvatarUrl(uuid))
            .setFooter(name, getPlayerAvatarUrl(uuid))
            .setTimestamp(Instant.now());
    }

    /**
     * Create a moderation embed with player info
     */
    public static EmbedBuilder moderationEmbed(String action, Player target, String moderator, String reason) {
        Color color = getModerationColor(action);
        return new EmbedBuilder()
            .setColor(color)
            .setTitle("Moderation: " + action)
            .addField("Target", target.getName(), true)
            .addField("Moderator", moderator, true)
            .addField("Reason", reason != null ? reason : "No reason provided", false)
            .setThumbnail(getPlayerAvatarUrl(target.getUniqueId()))
            .setTimestamp(Instant.now());
    }

    /**
     * Create a ticket embed
     */
    public static EmbedBuilder ticketEmbed(int ticketId, String subject, String minecraftName, String status) {
        return new EmbedBuilder()
            .setColor(BRAND_PRIMARY)
            .setTitle("Ticket #" + ticketId)
            .addField("Subject", subject, false)
            .addField("Minecraft", minecraftName, true)
            .addField("Status", status, true)
            .setTimestamp(Instant.now());
    }

    /**
     * Create a verification embed
     */
    public static EmbedBuilder verificationEmbed(String code, int expiryMinutes) {
        return new EmbedBuilder()
            .setColor(INFO)
            .setTitle("Account Verification")
            .setDescription("To link your Discord account with your Minecraft account, follow these steps:")
            .addField("Step 1", "Join the Minecraft server", false)
            .addField("Step 2", "Use the following command in Minecraft:\n`/verify " + code + "`", false)
            .addField("Your Code", "**`" + code + "`**", true)
            .addField("Expires In", expiryMinutes + " minutes", true)
            .setFooter("This code is unique to you and can only be used once", null)
            .setTimestamp(Instant.now());
    }
    
    /**
     * Get moderation color by action type
     */
    public static Color getModerationColor(String action) {
        if (action == null) return INFO;

        switch (action.toUpperCase()) {
            case "BAN":
            case "TEMPBAN":
            case "BAN-IP":
                return ERROR;
            case "KICK":
                return WARNING;
            case "MUTE":
            case "TEMPMUTE":
                return WARNING;
            case "WARN":
                return new Color(255, 152, 0);
            case "UNBAN":
            case "UNMUTE":
                return SUCCESS;
            default:
                return INFO;
        }
    }
    
    /**
     * Create an embed for player events
     */
    public MessageEmbed createPlayerEventEmbed(Player player, String eventType, String message) {
        EmbedBuilder embed = new EmbedBuilder();
        
        // Set color based on event type
        Color color = getEventColor(eventType);
        embed.setColor(color);
        
        // Set author as player
        String avatarUrl = "https://crafatar.com/avatars/" + player.getUniqueId() + "?overlay";
        embed.setAuthor(player.getName(), null, avatarUrl);
        
        // Set description
        embed.setDescription(message);
        
        // Add fields
        embed.addField("World", player.getWorld().getName(), true);
        embed.addField("Location", formatLocation(player), true);
        
        // Add custom emoji if configured
        String emoji = getEventEmoji(eventType);
        if (emoji != null && !emoji.isEmpty()) {
            embed.setTitle(emoji + " " + eventType);
        } else {
            embed.setTitle(eventType);
        }
        
        embed.setTimestamp(Instant.now());
        
        return embed.build();
    }
    
    /**
     * Create an embed for announcements
     */
    public MessageEmbed createAnnouncementEmbed(String title, String message, Player author) {
        EmbedBuilder embed = new EmbedBuilder();
        
        embed.setTitle("📢 " + title);
        embed.setDescription(message);
        embed.setColor(new Color(255, 215, 0)); // Gold color
        
        if (author != null) {
            String avatarUrl = "https://crafatar.com/avatars/" + author.getUniqueId() + "?overlay";
            embed.setAuthor(author.getName(), null, avatarUrl);
        }
        
        embed.setTimestamp(Instant.now());
        embed.setFooter("Announcement", null);
        
        return embed.build();
    }
    
    /**
     * Create an embed for server status
     */
    public MessageEmbed createServerStatusEmbed() {
        EmbedBuilder embed = new EmbedBuilder();
        
        embed.setTitle("Server Status");
        embed.setColor(Color.GREEN);
        
        // Server info
        embed.addField("Players", plugin.getServer().getOnlinePlayers().size() + "/" + 
                       plugin.getServer().getMaxPlayers(), true);
        embed.addField("TPS", String.format("%.2f", getTPS()), true);
        embed.addField("Memory", getMemoryUsage(), true);
        
        // World info
        embed.addField("Worlds", String.valueOf(plugin.getServer().getWorlds().size()), true);
        embed.addField("Chunks", String.valueOf(getLoadedChunks()), true);
        embed.addField("Entities", String.valueOf(getEntityCount()), true);
        
        embed.setTimestamp(Instant.now());
        embed.setFooter("Server: " + plugin.getServer().getName(), null);
        
        return embed.build();
    }
    
    /**
     * Create an embed for moderation logs
     */
    public MessageEmbed createModerationEmbed(String action, Player moderator, Player target, String reason) {
        EmbedBuilder embed = new EmbedBuilder();

        // Set color based on action
        Color color = getModerationColor(action);
        embed.setColor(color);

        embed.setTitle("Moderation Action: " + action);
        
        // Moderator info
        if (moderator != null) {
            String avatarUrl = "https://crafatar.com/avatars/" + moderator.getUniqueId() + "?overlay";
            embed.setAuthor(moderator.getName(), null, avatarUrl);
        }
        
        // Target info
        if (target != null) {
            embed.addField("Target", target.getName(), true);
            embed.addField("UUID", target.getUniqueId().toString(), true);
        }
        
        // Reason
        if (reason != null && !reason.isEmpty()) {
            embed.addField("Reason", reason, false);
        }
        
        embed.setTimestamp(Instant.now());
        
        return embed.build();
    }
    
    /**
     * Parse color from string (supports hex and color names)
     */
    public Color parseColor(String colorStr) {
        if (colorStr == null || colorStr.isEmpty()) {
            return getDefaultColor();
        }
        
        // Check for hex color
        Matcher matcher = hexPattern.matcher(colorStr);
        if (matcher.matches()) {
            return Color.decode(colorStr);
        }
        
        // Check for named color
        Color namedColor = colorMap.get(colorStr.toLowerCase());
        if (namedColor != null) {
            return namedColor;
        }
        
        // Try to parse as RGB
        String[] parts = colorStr.split(",");
        if (parts.length == 3) {
            try {
                int r = Integer.parseInt(parts[0].trim());
                int g = Integer.parseInt(parts[1].trim());
                int b = Integer.parseInt(parts[2].trim());
                return new Color(r, g, b);
            } catch (NumberFormatException ignored) {
            }
        }
        
        return getDefaultColor();
    }
    
    /**
     * Strip color codes from text
     */
    public String stripColors(String text) {
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', text));
    }
    
    /**
     * Get default embed color
     */
    private Color getDefaultColor() {
        String colorStr = plugin.getConfig().getString("messages.embed-color", "#5865F2");
        return parseColor(colorStr);
    }
    
    /**
     * Get color for event type
     */
    private Color getEventColor(String eventType) {
        switch (eventType.toLowerCase()) {
            case "join":
                return Color.GREEN;
            case "leave":
                return Color.RED;
            case "death":
                return Color.DARK_GRAY;
            case "advancement":
                return Color.YELLOW;
            case "milestone":
                return new Color(255, 215, 0); // Gold color
            default:
                return getDefaultColor();
        }
    }
    
    /**
     * Get emoji for event type
     */
    private String getEventEmoji(String eventType) {
        String path = "features.emoji-reactions.custom-emojis." + eventType.toLowerCase();
        String customEmoji = plugin.getConfig().getString(path);
        
        if (customEmoji != null && !customEmoji.isEmpty()) {
            return customEmoji;
        }
        
        // Default emojis
        String defaultPath = "features.player-events.emojis." + eventType.toLowerCase();
        return plugin.getConfig().getString(defaultPath, "");
    }
    
    /**
     * Format player location
     */
    private String formatLocation(Player player) {
        return String.format("X: %d, Y: %d, Z: %d",
                player.getLocation().getBlockX(),
                player.getLocation().getBlockY(),
                player.getLocation().getBlockZ());
    }
    
    /**
     * Get server TPS
     */
    private double getTPS() {
        try {
            // Use reflection to get TPS from server
            Object server = plugin.getServer().getClass().getMethod("getServer").invoke(plugin.getServer());
            double[] tps = (double[]) server.getClass().getField("recentTps").get(server);
            return tps[0]; // 1 minute average
        } catch (Exception e) {
            return 20.0; // Default TPS
        }
    }
    
    /**
     * Get memory usage string
     */
    private String getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;
        
        return String.format("%dMB / %dMB", usedMemory, maxMemory);
    }
    
    /**
     * Get total loaded chunks
     */
    private int getLoadedChunks() {
        return plugin.getServer().getWorlds().stream()
                .mapToInt(world -> world.getLoadedChunks().length)
                .sum();
    }
    
    /**
     * Get total entity count
     */
    private int getEntityCount() {
        return plugin.getServer().getWorlds().stream()
                .mapToInt(world -> world.getEntities().size())
                .sum();
    }
    
    /**
     * Initialize color map
     */
    private Map<String, Color> initializeColorMap() {
        Map<String, Color> map = new HashMap<>();
        
        // Basic colors
        map.put("black", Color.BLACK);
        map.put("white", Color.WHITE);
        map.put("red", Color.RED);
        map.put("green", Color.GREEN);
        map.put("blue", Color.BLUE);
        map.put("yellow", Color.YELLOW);
        map.put("orange", Color.ORANGE);
        map.put("pink", Color.PINK);
        map.put("gray", Color.GRAY);
        map.put("grey", Color.GRAY);
        map.put("darkgray", Color.DARK_GRAY);
        map.put("darkgrey", Color.DARK_GRAY);
        map.put("cyan", Color.CYAN);
        map.put("magenta", Color.MAGENTA);
        
        // Discord colors
        map.put("blurple", new Color(88, 101, 242));
        map.put("greyple", new Color(153, 170, 181));
        map.put("dark", new Color(44, 47, 51));
        map.put("notquiteblack", new Color(35, 39, 42));
        
        return map;
    }
}