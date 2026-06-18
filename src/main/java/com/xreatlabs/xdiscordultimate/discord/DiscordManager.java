package com.xreatlabs.xdiscordultimate.discord;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.utils.DiscordRateLimiter;
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
    private final DiscordRateLimiter rateLimiter;
    private DiscordListener discordListener;

    public DiscordManager(XDiscordUltimate plugin) {
        this.plugin = plugin;
        // Rate limiter: 50 requests per minute per channel, 5 per second burst
        this.rateLimiter = new DiscordRateLimiter(50, 5);
    }
    
    /**
     * Validate Discord bot token format
     * Discord tokens follow the pattern: base64_encoded_string.base64_encoded_string.base64_encoded_string
     * or for newer bot tokens: BASE64_CHARACTERS.BASE64_CHARACTERS.BASE64_CHARACTERS
     */
    private boolean isValidTokenFormat(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }

        // Check for placeholder values
        if (token.contains("YOUR_") || token.equals("REPLACE_WITH_YOUR_BOT_TOKEN") ||
            token.equals("BOT_TOKEN_HERE") || token.equals("token")) {
            return false;
        }

        // Discord bot tokens are typically 59+ characters with dots separating parts
        // Format: base64.base64.base64 (approximately 24.34.28 characters or similar)
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return false;
        }

        // Each part should be base64-like (alphanumeric, +, /, =, -, _)
        // Discord token parts vary in length; the middle metadata section is often short
        for (String part : parts) {
            if (!part.matches("^[A-Za-z0-9+/=_\\-]+$")) {
                return false;
            }
        }

        return true;
    }

    /**
     * Initialize the Discord bot asynchronously. The JDA connection and blocking
     * awaitReady() call happen off the server thread, while the returned future is
     * completed on the main thread so callers can safely touch Bukkit state.
     */
    public CompletableFuture<Boolean> initialize() {
        String token = plugin.getConfigManager().getBotToken();

        if (token == null || token.isEmpty()) {
            plugin.getLogger().severe("Discord bot token is not configured!");
            plugin.getLogger().severe("Please set 'discord.bot-token' in config.yml");
            return CompletableFuture.completedFuture(false);
        }

        if (!isValidTokenFormat(token)) {
            plugin.getLogger().severe("Invalid Discord bot token format!");
            plugin.getLogger().severe("Tokens should follow the format: BASE64.BASE64.BASE64");
            plugin.getLogger().severe("Get your token from: https://discord.com/developers/applications");
            plugin.getLogger().severe("Current token (first 10 chars): " +
                (token.length() > 10 ? token.substring(0, 10) + "..." : token));
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
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
                discordListener = new DiscordListener(plugin);
                JDA builtJda = JDABuilder.createDefault(token)
                    .setActivity(Activity.playing("Minecraft"))
                    .setEnabledIntents(intents)
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .setChunkingFilter(ChunkingFilter.ALL)
                    .enableCache(CacheFlag.VOICE_STATE, CacheFlag.EMOJI, CacheFlag.ROLE_TAGS)
                    .disableCache(CacheFlag.SCHEDULED_EVENTS) // Explicitly disable scheduled events cache
                    .addEventListeners(discordListener)
                    .build();

                // Wait for ready off the server thread
                builtJda.awaitReady();
                this.jda = builtJda;
                return true;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to initialize Discord bot", e);
                return false;
            }
        }).thenApplyAsync(success -> {
            if (!success || jda == null) {
                return false;
            }

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
            
            // Register slash commands now that mainGuild is resolved
            if (discordListener != null) {
                discordListener.registerSlashCommands();
            }
            
            return true;
        }, command -> Bukkit.getScheduler().runTask(plugin, command));
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
        rateLimiter.clear();
    }

    /**
     * Send a message to a channel with rate limiting.
     * @param channelId Discord channel ID
     * @param message Message content
     * @return CompletableFuture that completes when sent
     */
    public CompletableFuture<Boolean> sendMessage(String channelId, String message) {
        if (!rateLimiter.tryAcquire("channel:" + channelId)) {
            plugin.getLogger().fine("Rate limit reached for channel: " + channelId);
            return CompletableFuture.completedFuture(false);
        }

        if (jda == null || !ready) {
            return CompletableFuture.completedFuture(false);
        }

        try {
            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel == null) {
                return CompletableFuture.completedFuture(false);
            }

            CompletableFuture<Boolean> future = new CompletableFuture<>();
            channel.sendMessage(message).queue(
                success -> future.complete(true),
                error -> {
                    future.complete(false);
                    // Reset rate limiter on rate limit error
                    if (error.toString().contains("429")) {
                        rateLimiter.reset("channel:" + channelId);
                    }
                }
            );
            return future;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to send message to channel " + channelId, e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Get the rate limiter for external use
     */
    public DiscordRateLimiter getRateLimiter() {
        return rateLimiter;
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
        return getTextChannelById(channelId);
    }

    // ============================================
    // CONFIGURED CHANNEL HELPERS
    // ============================================

    /**
     * Generic channel getter by config path
     * @param configPath The configuration path (e.g., "discord.channels.chat")
     * @return The TextChannel or null if not found
     */
    public TextChannel getChannel(String configPath) {
        String channelId = plugin.getConfig().getString(configPath, "");
        return getTextChannelById(channelId);
    }

    /**
     * Get the welcome channel for new Discord members
     */
    public TextChannel getWelcomeChannel() {
        return getChannel("discord.channels.welcome");
    }

    /**
     * Get the logs channel for server logs
     */
    public TextChannel getLogsChannel() {
        return getChannel("discord.channels.logs");
    }

    /**
     * Get the chat channel for Minecraft <-> Discord chat
     */
    public TextChannel getChatChannel() {
        return getChannel("discord.channels.chat");
    }

    /**
     * Get the events channel for player join/leave/death
     */
    public TextChannel getEventsChannel() {
        return getChannel("discord.channels.events");
    }

    /**
     * Get the tickets channel
     */
    public TextChannel getTicketsChannel() {
        return getChannel("discord.channels.tickets");
    }

    /**
     * Get the moderation channel for reports and mod logs
     */
    public TextChannel getModerationChannel() {
        return getChannel("discord.channels.moderation");
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