package com.xreatlabs.xdiscordultimate.listeners;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class ChatListener implements Listener {
    
    private final XDiscordUltimate plugin;
    private final List<String> filterWords;
    private final Pattern urlPattern;
    
    // Spam detection
    private final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastMessage = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> messageCount = new ConcurrentHashMap<>();
    
    public ChatListener(XDiscordUltimate plugin) {
        this.plugin = plugin;
        this.filterWords = plugin.getConfig().getStringList("features.moderation.filter-words");
        this.urlPattern = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        
        // Clean up old entries periodically to prevent memory leaks
        if (System.currentTimeMillis() % 1000 == 0) { // Clean up every second
            cleanupOldEntries();
        }
        
        // Update message count in stats
        plugin.getDatabaseManager().updatePlayerStats(player.getUniqueId(), "messages_sent", 1);
        
        // Check for auto-moderation if enabled
        if (plugin.getConfigManager().isSubFeatureEnabled("moderation", "auto-moderate")) {
            if (shouldFilterMessage(message, player)) {
                event.setCancelled(true);
                plugin.getMessageManager().sendError(player, "Your message was blocked by the chat filter.");
                
                // Log the filtered message
                plugin.getLogger().warning("Filtered message from " + player.getName() + ": " + message);
                
                // Log to moderation channel if available
                logModerationAction(player, message, "CHAT_FILTER");
                return;
            }
        }
        
        // Process Discord mentions (convert @username to Discord mention format)
        if (message.contains("@")) {
            message = processDiscordMentions(message);
            event.setMessage(message);
        }
        
        // Check for spam detection
        if (isSpamMessage(player, message)) {
            event.setCancelled(true);
            plugin.getMessageManager().sendError(player, "Please don't spam the chat!");
            return;
        }
        
        // Update last message time for spam detection
        updateLastMessageTime(player);
    }
    
    /**
     * Clean up old entries to prevent memory leaks
     */
    private void cleanupOldEntries() {
        long cutoff = System.currentTimeMillis() - 300000; // 5 minutes ago
        
        // Clean up old message times
        lastMessageTime.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        
        // Clean up message counts for players who haven't sent messages recently
        messageCount.entrySet().removeIf(entry -> {
            Long time = lastMessageTime.get(entry.getKey());
            return time == null || time < cutoff;
        });
        
        // Clean up last messages for players who haven't sent messages recently
        lastMessage.entrySet().removeIf(entry -> {
            Long time = lastMessageTime.get(entry.getKey());
            return time == null || time < cutoff;
        });
    }

    /**
     * Check if a message should be filtered
     */
    private boolean shouldFilterMessage(String message, Player player) {
        // Bypass filter for players with permission
        if (player.hasPermission("xdiscord.bypass.filter")) {
            return false;
        }
        
        if (message == null || message.isEmpty()) {
            return false;
        }
        
        String lowerMessage = message.toLowerCase();
        
        // Check filter words
        for (String word : filterWords) {
            if (word == null || word.isEmpty()) {
                continue;
            }
            
            if (word.startsWith("regex:")) {
                // Regex pattern
                String pattern = word.substring(6);
                try {
                    if (message.matches(pattern)) {
                        return true;
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Invalid regex pattern: " + pattern);
                }
            } else {
                // Simple word check
                if (lowerMessage.contains(word.toLowerCase())) {
                    return true;
                }
            }
        }
        
        // Check for spam (repeated characters)
        if (message.length() > 3) {
            char firstChar = message.charAt(0);
            boolean allSame = true;
            for (int i = 1; i < message.length(); i++) {
                if (message.charAt(i) != firstChar) {
                    allSame = false;
                    break;
                }
            }
            if (allSame) {
                return true;
            }
        }
        
        // Check for URLs if disabled
        if (!plugin.getConfig().getBoolean("features.chat.allow-urls", false)) {
            if (urlPattern.matcher(message).find()) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if message contains spam (repeated characters)
     */
    private boolean containsSpam(String message) {
        // Check for repeated characters (e.g., "aaaaaaa")
        return message.matches(".*([a-zA-Z])\\1{5,}.*");
    }
    
    /**
     * Check if message has excessive caps
     */
    private boolean isExcessiveCaps(String message) {
        if (message.length() < 5) {
            return false;
        }
        
        int capsCount = 0;
        int letterCount = 0;
        
        for (char c : message.toCharArray()) {
            if (Character.isLetter(c)) {
                letterCount++;
                if (Character.isUpperCase(c)) {
                    capsCount++;
                }
            }
        }
        
        if (letterCount == 0) {
            return false;
        }
        
        double capsPercentage = (double) capsCount / letterCount;
        return capsPercentage > 0.7; // More than 70% caps
    }
    
    /**
     * Process Discord mentions in the message
     */
    private String processDiscordMentions(String message) {
        // This would convert @username to proper Discord mention format
        // For now, just return the original message
        // In a full implementation, this would look up Discord IDs from usernames
        return message;
    }
    
    /**
     * Check if message is spam
     */
    private boolean isSpamMessage(Player player, String message) {
        UUID uuid = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        // Check for repeated messages
        String lastMsg = lastMessage.get(uuid);
        if (lastMsg != null && lastMsg.equals(message)) {
            return true;
        }
        
        // Check for message frequency (more than 3 messages in 5 seconds)
        Long lastTime = lastMessageTime.get(uuid);
        if (lastTime != null) {
            if (currentTime - lastTime < 1500) { // 1.5 seconds
                int count = messageCount.getOrDefault(uuid, 0) + 1;
                messageCount.put(uuid, count);
                
                if (count > 3) {
                    return true;
                }
            } else {
                messageCount.put(uuid, 1);
            }
        }
        
        return false;
    }
    
    /**
     * Update last message time for spam detection
     */
    private void updateLastMessageTime(Player player) {
        UUID uuid = player.getUniqueId();
        lastMessageTime.put(uuid, System.currentTimeMillis());
        
        // Clean up old entries (older than 1 minute)
        long cutoff = System.currentTimeMillis() - 60000;
        lastMessageTime.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        messageCount.entrySet().removeIf(entry -> {
            Long time = lastMessageTime.get(entry.getKey());
            return time == null || time < cutoff;
        });
    }
    
    /**
     * Log moderation action to Discord
     */
    private void logModerationAction(Player player, String message, String action) {
        try {
            // Log to database
            plugin.getDatabaseManager().logModerationAction(
                action,
                player.getUniqueId(),
                null, // No moderator for auto-actions
                "Auto-filtered: " + message,
                null
            );
            
            // TODO: Send to Discord moderation channel
            plugin.getLogger().info("Moderation action: " + action + " for " + player.getName() + ": " + message);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to log moderation action: " + e.getMessage());
        }
    }
}