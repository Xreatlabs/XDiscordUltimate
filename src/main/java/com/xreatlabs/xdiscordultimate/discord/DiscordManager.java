package com.xreatlabs.xdiscordultimate.discord;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class DiscordManager {
    
    private final XDiscordUltimate plugin;
    private JDA jda;
    private Guild mainGuild;
    private boolean ready = false;
    private BukkitTask statusTask;
    
    public DiscordManager(XDiscordUltimate plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Initialize the Discord bot
     */
    public CompletableFuture<Boolean> initialize() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        String token = plugin.getConfigManager().getBotToken();
        if (token == null || token.isEmpty() || token.equals("YOUR_BOT_TOKEN")) {
            plugin.getLogger().severe("Discord bot token not configured!");
            future.complete(false);
            return future;
        }
        
        try {
            // Configure intents - need GUILD_PRESENCES for booster detection
            EnumSet<GatewayIntent> intents = EnumSet.of(
                GatewayIntent.GUILD_MEMBERS,
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.GUILD_MESSAGE_REACTIONS,
                GatewayIntent.GUILD_VOICE_STATES,
                GatewayIntent.GUILD_EMOJIS_AND_STICKERS,
                GatewayIntent.GUILD_PRESENCES,
                GatewayIntent.MESSAGE_CONTENT,
                GatewayIntent.DIRECT_MESSAGES
            );
            
            // Build JDA - only enable cache flags that have corresponding intents
            jda = JDABuilder.createDefault(token)
                .setActivity(Activity.playing("Minecraft"))
                .setEnabledIntents(intents)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .setChunkingFilter(ChunkingFilter.ALL)
                .enableCache(CacheFlag.VOICE_STATE, CacheFlag.EMOJI, CacheFlag.ROLE_TAGS)
                .disableCache(CacheFlag.SCHEDULED_EVENTS) // Explicitly disable scheduled events cache
                .addEventListeners(new DiscordListener(plugin))
                .build();
            
            // Wait for ready
            jda.awaitReady();
            
            // Get main guild - try multiple methods
            String guildId = plugin.getConfigManager().getGuildId();
            
            // Clean up guild ID (remove quotes, spaces, etc.)
            if (guildId != null) {
                guildId = guildId.trim().replace("\"", "").replace("'", "");
            }
            
            plugin.getLogger().info("Attempting to connect to guild ID: " + guildId);
            
            if (guildId != null && !guildId.isEmpty() && !guildId.equals("YOUR_GUILD_ID")) {
                // Method 1: Direct lookup by ID
                mainGuild = jda.getGuildById(guildId);
                
                // Method 2: If not found, list available guilds for debugging
                if (mainGuild == null) {
                    plugin.getLogger().warning("Could not find guild with ID: " + guildId);
                    plugin.getLogger().info("Bot is connected to " + jda.getGuilds().size() + " guild(s):");
                    for (Guild g : jda.getGuilds()) {
                        plugin.getLogger().info("  - " + g.getName() + " (ID: " + g.getId() + ")");
                    }
                    
                    // Method 3: If only in one guild, use that
                    if (jda.getGuilds().size() == 1) {
                        mainGuild = jda.getGuilds().get(0);
                        plugin.getLogger().info("Auto-selected the only available guild: " + mainGuild.getName());
                    }
                } else {
                    plugin.getLogger().info("Successfully connected to guild: " + mainGuild.getName());
                }
            } else {
                // No guild ID configured - try to auto-detect
                plugin.getLogger().warning("No guild ID configured!");
                if (jda.getGuilds().size() == 1) {
                    mainGuild = jda.getGuilds().get(0);
                    plugin.getLogger().info("Auto-selected guild: " + mainGuild.getName() + " (ID: " + mainGuild.getId() + ")");
                    plugin.getLogger().info("TIP: Add this to your config.yml: guild-id: \"" + mainGuild.getId() + "\"");
                } else if (jda.getGuilds().size() > 1) {
                    plugin.getLogger().warning("Bot is in multiple guilds. Please set 'guild-id' in config.yml:");
                    for (Guild g : jda.getGuilds()) {
                        plugin.getLogger().warning("  - " + g.getName() + " (ID: " + g.getId() + ")");
                    }
                } else {
                    plugin.getLogger().severe("Bot is not in any guilds! Invite the bot to your Discord server first.");
                }
            }
            
            // Update activity
            updateActivity();
            
            // Start status update task (every 60 seconds)
            startStatusUpdater();
            
            ready = true;
            plugin.getLogger().info("Discord bot connected successfully!");
            future.complete(true);
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize Discord bot", e);
            future.complete(false);
        }
        
        return future;
    }
    
    /**
     * Start the status updater task
     */
    private void startStatusUpdater() {
        if (statusTask != null) {
            statusTask.cancel();
        }
        statusTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            updateActivity();
        }, 1200L, 1200L); // Every 60 seconds
    }
    
    /**
     * Shutdown the Discord bot
     */
    public void shutdown() {
        if (statusTask != null) {
            statusTask.cancel();
            statusTask = null;
        }
        if (jda != null) {
            jda.shutdown();
            ready = false;
            plugin.getLogger().info("Discord bot disconnected");
        }
    }
    
    /**
     * Update bot activity
     */
    public void updateActivity() {
        if (jda == null) return;
        
        String type = plugin.getConfig().getString("discord.activity.type", "PLAYING");
        String text = plugin.getConfig().getString("discord.activity.text", "Minecraft");
        
        // Replace placeholders
        text = text.replace("%players%", String.valueOf(plugin.getServer().getOnlinePlayers().size()));
        text = text.replace("%max%", String.valueOf(plugin.getServer().getMaxPlayers()));
        
        Activity activity;
        switch (type.toUpperCase()) {
            case "WATCHING":
                activity = Activity.watching(text);
                break;
            case "LISTENING":
                activity = Activity.listening(text);
                break;
            case "COMPETING":
                activity = Activity.competing(text);
                break;
            default:
                activity = Activity.playing(text);
                break;
        }
        
        jda.getPresence().setActivity(activity);
    }
    
    /**
     * Get JDA instance
     */
    public JDA getJDA() {
        return jda;
    }
    
    /**
     * Get main guild
     */
    public Guild getMainGuild() {
        return mainGuild;
    }
    
    /**
     * Check if bot is ready
     */
    public boolean isReady() {
        return ready && jda != null && jda.getStatus() == JDA.Status.CONNECTED;
    }
    
    /**
     * Get text channel by name
     */
    public TextChannel getTextChannel(String name) {
        if (mainGuild == null) return null;
        
        return mainGuild.getTextChannelsByName(name, true).stream()
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Get text channel by ID
     */
    public TextChannel getTextChannelById(String id) {
        if (jda == null || id == null || id.isEmpty() || id.startsWith("YOUR_")) {
            return null;
        }
        try {
            return jda.getTextChannelById(id);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("'" + id + "' is not a valid Discord channel ID. Please check your config.yml.");
            return null;
        }
    }

    public TextChannel getConsoleChannel() {
        String channelId = plugin.getConfig().getString("discord-console.channel-id");
        if (channelId == null || channelId.isEmpty()) {
            // Try discord.channels.console as fallback
            channelId = plugin.getConfig().getString("discord.channels.console", "");
        }
        if (channelId == null || channelId.isEmpty()) {
            return null;
        }
        return getTextChannelById(channelId);
    }
    
    // ============================================
    // CONFIGURED CHANNEL HELPERS
    // ============================================
    
    /**
     * Get the welcome channel for new Discord members
     */
    public TextChannel getWelcomeChannel() {
        String channelId = plugin.getConfig().getString("discord.channels.welcome", "");
        return getTextChannelById(channelId);
    }
    
    /**
     * Get the logs channel for server logs
     */
    public TextChannel getLogsChannel() {
        String channelId = plugin.getConfig().getString("discord.channels.logs", "");
        return getTextChannelById(channelId);
    }
    
    /**
     * Get the chat channel for Minecraft <-> Discord chat
     */
    public TextChannel getChatChannel() {
        String channelId = plugin.getConfig().getString("discord.channels.chat", "");
        return getTextChannelById(channelId);
    }
    
    /**
     * Get the events channel for player join/leave/death
     */
    public TextChannel getEventsChannel() {
        String channelId = plugin.getConfig().getString("discord.channels.events", "");
        if (channelId == null || channelId.isEmpty()) {
            // Fallback to chat channel
            return getChatChannel();
        }
        return getTextChannelById(channelId);
    }
    
    /**
     * Get the tickets channel
     */
    public TextChannel getTicketsChannel() {
        String channelId = plugin.getConfig().getString("discord.channels.tickets", "");
        return getTextChannelById(channelId);
    }
    
    /**
     * Get the moderation channel for reports and mod logs
     */
    public TextChannel getModerationChannel() {
        String channelId = plugin.getConfig().getString("discord.channels.moderation", "");
        return getTextChannelById(channelId);
    }
    
    /**
     * Log which channels are configured (for debugging)
     */
    public void logConfiguredChannels() {
        plugin.getLogger().info("=== Configured Discord Channels ===");
        logChannel("welcome", getWelcomeChannel());
        logChannel("logs", getLogsChannel());
        logChannel("chat", getChatChannel());
        logChannel("events", getEventsChannel());
        logChannel("console", getConsoleChannel());
        logChannel("tickets", getTicketsChannel());
        logChannel("moderation", getModerationChannel());
        plugin.getLogger().info("===================================");
    }
    
    private void logChannel(String name, TextChannel channel) {
        if (channel != null) {
            plugin.getLogger().info("  " + name + ": #" + channel.getName() + " (" + channel.getId() + ")");
        } else {
            String configId = plugin.getConfig().getString("discord.channels." + name, "");
            if (configId != null && !configId.isEmpty()) {
                plugin.getLogger().warning("  " + name + ": INVALID ID '" + configId + "' - channel not found!");
            } else {
                plugin.getLogger().info("  " + name + ": Not configured");
            }
        }
    }
}