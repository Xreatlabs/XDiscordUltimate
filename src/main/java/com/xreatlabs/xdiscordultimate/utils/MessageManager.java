package com.xreatlabs.xdiscordultimate.utils;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class MessageManager {
    
    private final XDiscordUltimate plugin;
    private FileConfiguration messagesConfig;
    private File messagesFile;
    private final String prefix;
    
    public MessageManager(XDiscordUltimate plugin) {
        this.plugin = plugin;
        this.prefix = "&8[&bXDiscord&8] ";
        loadMessages();
    }
    
    /**
     * Load messages configuration
     */
    private void loadMessages() {
        String language = plugin.getConfig().getString("general.language", "en_US");
        String fileName = "messages_" + language + ".yml";
        
        messagesFile = new File(plugin.getDataFolder(), fileName);
        
        // If language file doesn't exist, try default
        if (!messagesFile.exists()) {
            messagesFile = new File(plugin.getDataFolder(), "messages.yml");
            if (!messagesFile.exists()) {
                plugin.saveResource("messages.yml", false);
            }
        }
        
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        
        // Look for defaults in the jar
        InputStream defConfigStream = plugin.getResource("messages.yml");
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defConfigStream));
            messagesConfig.setDefaults(defConfig);
        }
    }
    
    /**
     * Reload messages
     */
    public void reload() {
        loadMessages();
    }
    
    /**
     * Get a message from the config
     */
    public String getMessage(String path) {
        String message = messagesConfig.getString(path);
        if (message == null) {
            plugin.getLogger().warning("Missing message: " + path);
            return "&cMissing message: " + path;
        }
        return colorize(message);
    }
    
    /**
     * Get a message with placeholders
     */
    public String getMessage(String path, Map<String, String> placeholders) {
        String message = getMessage(path);
        
        if (placeholders != null && !placeholders.isEmpty()) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    message = message.replace(entry.getKey(), entry.getValue());
                }
            }
        }
        
        return message;
    }
    
    /**
     * Get a message with a single placeholder
     */
    public String getMessage(String path, String placeholder, String value) {
        if (placeholder == null || value == null) {
            return getMessage(path);
        }
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put(placeholder, value);
        return getMessage(path, placeholders);
    }
    
    /**
     * Send a message to a command sender
     */
    public void sendMessage(CommandSender sender, String path) {
        sender.sendMessage(prefix + getMessage(path));
    }
    
    /**
     * Send a message with placeholders
     */
    public void sendMessage(CommandSender sender, String path, Map<String, String> placeholders) {
        sender.sendMessage(prefix + getMessage(path, placeholders));
    }
    
    /**
     * Send a message with a single placeholder
     */
    public void sendMessage(CommandSender sender, String path, String placeholder, String value) {
        sender.sendMessage(prefix + getMessage(path, placeholder, value));
    }
    
    /**
     * Send a raw message (already formatted)
     */
    public void sendRawMessage(CommandSender sender, String message) {
        sender.sendMessage(prefix + colorize(message));
    }
    
    /**
     * Send a message without prefix
     */
    public void sendMessageNoPrefix(CommandSender sender, String path) {
        sender.sendMessage(getMessage(path));
    }
    
    /**
     * Send a list of messages
     */
    public void sendMessageList(CommandSender sender, String path) {
        List<String> messages = messagesConfig.getStringList(path);
        for (String message : messages) {
            sender.sendMessage(colorize(message));
        }
    }
    
    /**
     * Send an error message
     */
    public void sendError(CommandSender sender, String message) {
        sender.sendMessage(prefix + "&c" + colorize(message));
    }
    
    /**
     * Send a success message
     */
    public void sendSuccess(CommandSender sender, String message) {
        sender.sendMessage(prefix + "&a" + colorize(message));
    }
    
    /**
     * Send a warning message
     */
    public void sendWarning(CommandSender sender, String message) {
        sender.sendMessage(prefix + "&e" + colorize(message));
    }
    
    /**
     * Send an info message
     */
    public void sendInfo(CommandSender sender, String message) {
        sender.sendMessage(prefix + "&b" + colorize(message));
    }
    
    /**
     * Send a title to a player
     */
    public void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        // For 1.16+, use the new method signature
        try {
            player.getClass().getMethod("sendTitle", String.class, String.class, int.class, int.class, int.class)
                .invoke(player, colorize(title), colorize(subtitle), fadeIn, stay, fadeOut);
        } catch (Exception e) {
            // Fallback for older versions
            try {
                player.getClass().getMethod("sendTitle", String.class, String.class)
                    .invoke(player, colorize(title), colorize(subtitle));
            } catch (Exception ex) {
                plugin.getLogger().warning("Could not send title to player: " + ex.getMessage());
            }
        }
    }
    
    /**
     * Send an action bar message to a player
     */
    public void sendActionBar(Player player, String message) {
        // Use Spigot's sendMessage with ChatMessageType for action bar
        try {
            Class<?> chatMessageTypeClass = Class.forName("net.md_5.bungee.api.ChatMessageType");
            Object actionBar = chatMessageTypeClass.getField("ACTION_BAR").get(null);
            Class<?> baseComponentClass = Class.forName("net.md_5.bungee.api.chat.BaseComponent");
            Class<?> textComponentClass = Class.forName("net.md_5.bungee.api.chat.TextComponent");
            
            Object textComponent = textComponentClass.getConstructor(String.class).newInstance(colorize(message));
            Object[] components = (Object[]) java.lang.reflect.Array.newInstance(baseComponentClass, 1);
            components[0] = textComponent;
            
            // Create array type manually for Java 8 compatibility
            Class<?> arrayType = java.lang.reflect.Array.newInstance(baseComponentClass, 0).getClass();
            player.spigot().getClass().getMethod("sendMessage", chatMessageTypeClass, arrayType)
                .invoke(player.spigot(), actionBar, components);
        } catch (Exception e) {
            // Fallback - just send as regular message
            player.sendMessage(colorize(message));
        }
    }
    
    /**
     * Broadcast a message to all players
     */
    public void broadcast(String path) {
        String message = prefix + getMessage(path);
        plugin.getServer().broadcastMessage(message);
    }
    
    /**
     * Broadcast a message with placeholders
     */
    public void broadcast(String path, Map<String, String> placeholders) {
        String message = prefix + getMessage(path, placeholders);
        plugin.getServer().broadcastMessage(message);
    }
    
    /**
     * Broadcast a message to players with permission
     */
    public void broadcastPermission(String permission, String path) {
        String message = prefix + getMessage(path);
        plugin.getServer().broadcast(message, permission);
    }
    
    /**
     * Convert color codes
     */
    public String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
    
    /**
     * Strip color codes
     */
    public String stripColors(String message) {
        return ChatColor.stripColor(message);
    }
    
    /**
     * Get the prefix
     */
    public String getPrefix() {
        return colorize(prefix);
    }
    
    /**
     * Format a Discord to Minecraft message
     */
    public String formatDiscordToMinecraft(String username, String message) {
        if (username == null || message == null) {
            return "";
        }
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%user%", username);
        placeholders.put("%message%", message);
        
        String format = plugin.getConfig().getString("messages.discord-to-minecraft", 
            "&7[&9Discord&7] &b%user%&7: &f%message%");
        
        if (format != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                format = format.replace(entry.getKey(), entry.getValue());
            }
        }
        
        return colorize(format != null ? format : "");
    }
    
    /**
     * Format a Minecraft to Discord message
     */
    public String formatMinecraftToDiscord(String playerName, String message) {
        if (playerName == null || message == null) {
            return "";
        }
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%player%", playerName);
        placeholders.put("%message%", message);
        
        String format = plugin.getConfig().getString("messages.minecraft-to-discord", 
            "**%player%**: %message%");
        
        if (format != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                format = format.replace(entry.getKey(), entry.getValue());
            }
        }
        
        return format != null ? format : "";
    }
    
    /**
     * Get no permission message
     */
    public String getNoPermissionMessage() {
        return getMessage("errors.no-permission");
    }
    
    /**
     * Get player not found message
     */
    public String getPlayerNotFoundMessage(String playerName) {
        return getMessage("errors.player-not-found", "%player%", playerName);
    }
    
    /**
     * Get invalid usage message
     */
    public String getInvalidUsageMessage(String usage) {
        return getMessage("errors.invalid-usage", "%usage%", usage);
    }
    
    /**
     * Check if a message path exists
     */
    public boolean hasMessage(String path) {
        return messagesConfig.contains(path);
    }
    
    /**
     * Set a message in the config
     */
    public void setMessage(String path, String message) {
        messagesConfig.set(path, message);
        saveMessages();
    }
    
    /**
     * Save messages config
     */
    private void saveMessages() {
        try {
            messagesConfig.save(messagesFile);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save messages.yml", e);
        }
    }
}