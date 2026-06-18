package com.xreatlabs.xdiscordultimate.modules.chatbridge;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.Module;
import com.xreatlabs.xdiscordultimate.utils.ColorUtils;
import com.xreatlabs.xdiscordultimate.utils.WebhookManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.user.UserTypingEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.awt.*;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatBridgeModule extends Module implements Listener {
    
    private ChatBridgeListener discordListener;
    private WebhookManager webhookManager;
    private String chatChannelName;
    private String eventsChannelId;  // Separate channel for join/leave/death
    private boolean enableMinecraftToDiscord;
    private boolean enableDiscordToMinecraft;
    private boolean useWebhook;
    private boolean autoCreateWebhook;
    private String webhookUrl;
    private String skinProvider;
    private boolean filterBotMessages;
    private String minecraftFormat;
    private String discordFormat;
    
    // Auto-managed webhook
    private net.dv8tion.jda.api.entities.Webhook managedWebhook;
    private String managedWebhookUrl;
    
    // Enhanced features
    private boolean showMentions;
    private boolean playMentionSound;
    private boolean convertEmojis;
    private boolean showTypingIndicator;
    private boolean stripDiscordFormatting;
    
    // Custom format options
    private boolean showPlayerPrefix;
    private boolean showPlayerSuffix;
    private String discordUsernameFormat;
    
    // Typing indicator tracking
    private final Map<String, Long> typingUsers = new ConcurrentHashMap<>();
    private static final long TYPING_TIMEOUT = 10000; // 10 seconds
    
    // Webhook URL pattern for validation
    private static final Pattern WEBHOOK_PATTERN = Pattern.compile("https://discord(?:app)?\\.com/api/webhooks/\\d+/[\\w-]+");
    private static final Pattern MENTION_PATTERN = Pattern.compile("<@!?(\\d+)>");
    private static final Pattern CHANNEL_MENTION_PATTERN = Pattern.compile("<#(\\d+)>");
    private static final Pattern ROLE_MENTION_PATTERN = Pattern.compile("<@&(\\d+)>");
    private static final Pattern EMOJI_PATTERN = Pattern.compile("<a?:(\\w+):(\\d+)>");
    private static final Pattern URL_PATTERN = Pattern.compile("(https?://\\S+)");
    
    public ChatBridgeModule(XDiscordUltimate plugin) {
        super(plugin);
    }
    
    @Override
    public String getName() {
        return "Chat Bridge";
    }
    
    @Override
    public String getDescription() {
        return "Bridges chat between Minecraft and Discord";
    }
    
    @Override
    protected void onEnable() {
        loadConfiguration();
        
        // Initialize webhook manager
        webhookManager = new WebhookManager(plugin);
        
        // Validate webhook URL if enabled
        if (useWebhook && webhookUrl != null && !webhookUrl.isEmpty()) {
            if (webhookManager.isValidWebhookUrl(webhookUrl)) {
                debug("Webhook mode enabled with skin provider: " + skinProvider);
            } else {
                warning("Invalid webhook URL! Falling back to regular messages.");
                useWebhook = false;
            }
        }

        // Register Minecraft events
        Bukkit.getPluginManager().registerEvents(this, plugin);

        info("Chat bridge module enabled");
    }
    
    @Override
    protected void onDisable() {
        // Unregister Discord listener
        if (discordListener != null && plugin.getDiscordManager() != null && plugin.getDiscordManager().isReady()) {
            plugin.getDiscordManager().getJDA().removeEventListener(discordListener);
        }
        
        // Shutdown webhook manager
        if (webhookManager != null) {
            webhookManager.shutdown();
        }
        
        // Unregister Minecraft events
        AsyncPlayerChatEvent.getHandlerList().unregister(this);
        
        info("Chat bridge module disabled");
    }
    
    @Override
    public void onDiscordReady() {
        // Register Discord listener when Discord is ready
        if (plugin.getDiscordManager() != null && plugin.getDiscordManager().isReady()) {
            discordListener = new ChatBridgeListener();
            plugin.getDiscordManager().getJDA().addEventListener(discordListener);
            info("Discord chat listener registered");

            // Setup webhook for DiscordSRV-style player avatars
            setupWebhook();
        }
    }
    
    /**
     * Setup webhook for chat messages (DiscordSRV-style player avatars)
     * This creates or finds an existing webhook in the chat channel
     */
    private void setupWebhook() {
        // If manual webhook URL is provided, use it
        if (webhookUrl != null && !webhookUrl.isEmpty() && webhookManager.isValidWebhookUrl(webhookUrl)) {
            managedWebhookUrl = webhookUrl;
            useWebhook = true;
            debug("Using manual webhook URL for chat bridge");
            return;
        }

        // Auto-create webhook if enabled
        if (!autoCreateWebhook) {
            debug("Auto-create webhook disabled, using embed mode");
            return;
        }
        
        TextChannel chatChannel = getChatChannel();
        if (chatChannel == null) {
            warning("Cannot auto-create webhook: chat channel not found");
            return;
        }
        
        // Check for existing webhook (Note: "discord" is not allowed in webhook names)
        final String WEBHOOK_NAME = "MC-Chat-Bridge";
        chatChannel.retrieveWebhooks().queue(webhooks -> {
            for (net.dv8tion.jda.api.entities.Webhook webhook : webhooks) {
                // Check for our webhook (also check old name for backwards compatibility)
                if (WEBHOOK_NAME.equals(webhook.getName()) || "XDiscordUltimate".equals(webhook.getName())) {
                    managedWebhook = webhook;
                    managedWebhookUrl = webhook.getUrl();
                    useWebhook = true;
                    debug("Found existing webhook for chat bridge in #" + chatChannel.getName());
                    return;
                }
            }
            
            // Create new webhook
            chatChannel.createWebhook(WEBHOOK_NAME).queue(
                webhook -> {
                    managedWebhook = webhook;
                    managedWebhookUrl = webhook.getUrl();
                    useWebhook = true;
                    info("Created webhook for chat bridge in #" + chatChannel.getName());
                    debug("Player skins will now show as message avatars!");
                },
                error -> {
                    warning("Failed to create webhook: " + error.getMessage());
                    warning("Bot needs 'Manage Webhooks' permission for player avatar feature");
                    debug("Falling back to embed mode (player avatars in embed)");
                }
            );
        }, error -> {
            warning("Failed to retrieve webhooks: " + error.getMessage());
        });
    }
    
    private void loadConfiguration() {
        // Get config section, with null safety
        org.bukkit.configuration.ConfigurationSection config = getConfig();
        
        if (config != null) {
            // Try chat-channel-id first
            chatChannelName = config.getString("chat-channel-id", "");
            
            // If empty, try chat-channel
            if (chatChannelName.isEmpty()) {
                chatChannelName = config.getString("chat-channel", "");
            }
            
            enableMinecraftToDiscord = config.getBoolean("minecraft-to-discord", true);
            enableDiscordToMinecraft = config.getBoolean("discord-to-minecraft", true);
            useWebhook = config.getBoolean("use-webhook", false);
            autoCreateWebhook = config.getBoolean("auto-create-webhook", true);
            webhookUrl = config.getString("webhook-url", "");
            skinProvider = config.getString("skin-provider", "mc-heads");
            filterBotMessages = config.getBoolean("filter-bot-messages", true);
            minecraftFormat = config.getString("minecraft-format", "**%player%**: %message%");
            discordFormat = config.getString("discord-format", "&7[&9Discord&7] &b%user%&7: &f%message%");
            
            // Enhanced features
            showMentions = config.getBoolean("show-mentions", true);
            playMentionSound = config.getBoolean("play-mention-sound", true);
            convertEmojis = config.getBoolean("convert-emojis", true);
            showTypingIndicator = config.getBoolean("show-typing-indicator", true);
            stripDiscordFormatting = config.getBoolean("strip-discord-formatting", false);
            
            // Custom format options
            showPlayerPrefix = config.getBoolean("show-player-prefix", true);
            showPlayerSuffix = config.getBoolean("show-player-suffix", false);
            discordUsernameFormat = config.getString("discord-username-format", "%prefix%%player%%suffix%");
        } else {
            warning("Config section 'features.chat-bridge' not found!");
            enableMinecraftToDiscord = true;
            enableDiscordToMinecraft = true;
            useWebhook = false;
            autoCreateWebhook = true;
            webhookUrl = "";
            skinProvider = "mc-heads";
            filterBotMessages = true;
            minecraftFormat = "**%player%**: %message%";
            discordFormat = "&7[&9Discord&7] &b%user%&7: &f%message%";
            showMentions = true;
            playMentionSound = true;
            convertEmojis = true;
            showTypingIndicator = true;
            stripDiscordFormatting = false;
            showPlayerPrefix = true;
            showPlayerSuffix = false;
            discordUsernameFormat = "%prefix%%player%%suffix%";
        }

        // Try global channel config as fallback
        if (chatChannelName == null || chatChannelName.isEmpty()) {
            chatChannelName = plugin.getConfig().getString("discord.channels.chat", "");
        }

        // Get events channel (for join/leave/death messages)
        eventsChannelId = plugin.getConfig().getString("discord.channels.events", "");
        
        // Final fallback if still empty - use channel name
        if (chatChannelName == null || chatChannelName.isEmpty()) {
            chatChannelName = "minecraft-chat";
            warning("Chat channel not configured! Set 'discord.channels.chat' in config.yml");
        }
        
        info("Chat channel configured: " + chatChannelName);
        if (eventsChannelId != null && !eventsChannelId.isEmpty()) {
            info("Events channel configured: " + eventsChannelId);
        }
        debug("Enhanced features: mentions=" + showMentions + ", emojis=" + convertEmojis);
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        debug("Chat event received from: " + event.getPlayer().getName());

        if (!enableMinecraftToDiscord) {
            debug("Minecraft to Discord is disabled, skipping");
            return;
        }

        Player player = event.getPlayer();
        String message = event.getMessage();

        debug("Sending message to Discord: " + message);

        // Send to Discord
        sendToDiscord(player, message);
    }
    
    private void sendToDiscord(Player player, String message) {
        // Get the effective webhook URL (auto-managed or manual)
        String effectiveWebhookUrl = managedWebhookUrl != null ? managedWebhookUrl : webhookUrl;

        // Get formatted display name with prefix/suffix
        String displayName = getFormattedPlayerName(player);

        // Get player avatar URL (Minecraft skin face)
        String avatarUrl = WebhookManager.getPlayerAvatarUrl(player.getUniqueId(), player.getName(), skinProvider);
        debug("[ChatBridge] Avatar URL: " + avatarUrl);
        debug("[ChatBridge] useWebhook=" + useWebhook + ", webhookUrl=" + (effectiveWebhookUrl != null ? "set" : "null"));

        // Apply configured Minecraft format
        String formattedMessage = minecraftFormat
            .replace("%player%", displayName)
            .replace("%message%", message);

        // Try webhook first so messages appear to come from the player
        if (useWebhook && effectiveWebhookUrl != null && !effectiveWebhookUrl.isEmpty()) {
            debug("[ChatBridge] Using WEBHOOK mode");
            webhookManager.sendMessage(effectiveWebhookUrl, displayName, avatarUrl, formattedMessage)
                .thenAccept(success -> {
                    if (success) {
                        debug("[ChatBridge] Webhook SUCCESS - skin should show!");
                    } else {
                        warning("[ChatBridge] Webhook FAILED, using fallback");
                        sendToDiscordFallback(player, message, avatarUrl);
                    }
                });
            return;
        }

        // Fallback to embed mode with avatar
        debug("[ChatBridge] Using EMBED FALLBACK mode");
        sendToDiscordFallback(player, message, avatarUrl);
    }
    
    /**
     * Get formatted player name with prefix/suffix from LuckPerms or Vault
     */
    private String getFormattedPlayerName(Player player) {
        String prefix = "";
        String suffix = "";
        
        // Try to get prefix/suffix from LuckPerms
        if (plugin.isLuckPermsEnabled() && plugin.getLuckPerms() != null) {
            try {
                net.luckperms.api.LuckPerms luckPerms = (net.luckperms.api.LuckPerms) plugin.getLuckPerms();
                net.luckperms.api.model.user.User user = luckPerms.getUserManager().getUser(player.getUniqueId());
                
                if (user != null) {
                    net.luckperms.api.cacheddata.CachedMetaData metaData = user.getCachedData().getMetaData();
                    
                    if (showPlayerPrefix && metaData.getPrefix() != null) {
                        prefix = stripMinecraftColors(metaData.getPrefix());
                    }
                    if (showPlayerSuffix && metaData.getSuffix() != null) {
                        suffix = stripMinecraftColors(metaData.getSuffix());
                    }
                }
            } catch (Exception e) {
                debug("Failed to get LuckPerms prefix/suffix: " + e.getMessage());
            }
        }
        
        // Format the display name
        String displayName = discordUsernameFormat
            .replace("%prefix%", prefix)
            .replace("%player%", player.getName())
            .replace("%suffix%", suffix)
            .trim();
        
        // Clean up any double spaces
        displayName = displayName.replaceAll("\\s+", " ").trim();
        
        // Discord webhook usernames have limits
        if (displayName.length() > 80) {
            displayName = displayName.substring(0, 80);
        }
        
        return displayName.isEmpty() ? player.getName() : displayName;
    }
    
    /**
     * Strip Minecraft color codes for Discord display
     */
    private String stripMinecraftColors(String text) {
        if (text == null) return "";
        // Remove &x, &a-f, &0-9, &k-o, &r, and section symbols
        return text.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "")
                   .replaceAll("[&§]x([&§][0-9a-fA-F]){6}", "")
                   .trim();
    }
    
    private void sendToDiscordFallback(Player player, String message, String avatarUrl) {
        TextChannel channel = getChatChannel();
        if (channel == null) {
            debug("Chat channel not found: " + chatChannelName);
            return;
        }

        // Get formatted display name with prefix/suffix
        String displayName = getFormattedPlayerName(player);

        // Apply configured Minecraft format
        String formattedMessage = minecraftFormat
            .replace("%player%", displayName)
            .replace("%message%", message);

        EmbedBuilder embed = new EmbedBuilder()
            .setAuthor(displayName, null, avatarUrl)
            .setDescription(formattedMessage)
            .setThumbnail(avatarUrl)
            .setColor(new Color(85, 255, 85))
            .setTimestamp(Instant.now());

        debug("[ChatBridge] Sending embed with avatar: " + avatarUrl);

        channel.sendMessageEmbeds(embed.build()).queue(
            success -> debug("[ChatBridge] Embed sent successfully with player skin!"),
            error -> warning("[ChatBridge] Failed to send embed: " + error.getMessage())
        );
    }
    
    private class ChatBridgeListener extends ListenerAdapter {
        
        @Override
        public void onUserTyping(UserTypingEvent event) {
            // Only process if typing indicator is enabled
            if (!showTypingIndicator) return;
            
            // Check if it's from the correct channel
            String channelId = event.getChannel().getId();
            if (!chatChannelName.isEmpty() && chatChannelName.matches("\\d+")) {
                if (!channelId.equals(chatChannelName)) {
                    return;
                }
            }
            
            // Ignore bots
            if (event.getUser().isBot()) return;
            
            String userName = event.getUser().getName();
            
            // Check if this user was already typing
            Long lastTyping = typingUsers.get(userName);
            long now = System.currentTimeMillis();
            
            // Only announce if user wasn't recently typing
            if (lastTyping == null || now - lastTyping > TYPING_TIMEOUT) {
                // Broadcast typing indicator to Minecraft
                String typingMessage = ColorUtils.parse("&7[&9Discord&7] &e" + userName + " &7is typing...");
                
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.sendMessage(typingMessage);
                    }
                });
                
                debug("Typing indicator: " + userName);
            }
            
            // Update last typing time
            typingUsers.put(userName, now);
            
            // Schedule cleanup
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                Long storedTime = typingUsers.get(userName);
                if (storedTime != null && System.currentTimeMillis() - storedTime >= TYPING_TIMEOUT) {
                    typingUsers.remove(userName);
                }
            }, 200L); // 10 seconds = 200 ticks
        }
        
        @Override
        public void onMessageReceived(MessageReceivedEvent event) {
            // Remove from typing list when they send a message
            typingUsers.remove(event.getAuthor().getName());
            // Check if it's from the correct channel (by ID first, then by name)
            String channelId = event.getChannel().getId();
            String channelName = event.getChannel().getName();
            
            // If we have a channel ID configured, use that
            if (!chatChannelName.isEmpty() && chatChannelName.matches("\\d+")) {
                if (!channelId.equals(chatChannelName)) {
                    return;
                }
            } else {
                // Fallback to name comparison
                if (!channelName.equalsIgnoreCase(chatChannelName)) {
                    return;
                }
            }
            
            // Ignore bot messages if configured
            if (filterBotMessages && event.getAuthor().isBot()) {
                return;
            }
            
            // Ignore webhook messages (our own messages)
            if (event.getMessage().isWebhookMessage()) {
                return;
            }
            
            // Ignore if Discord to Minecraft is disabled
            if (!enableDiscordToMinecraft) {
                return;
            }
            
            User author = event.getAuthor();
            Message discordMessage = event.getMessage();
            String message = discordMessage.getContentDisplay();
            
            // Build the full message content
            StringBuilder fullMessage = new StringBuilder();
            
            // Handle reply
            if (discordMessage.getReferencedMessage() != null) {
                Message repliedTo = discordMessage.getReferencedMessage();
                String repliedUser = repliedTo.getAuthor().getName();
                String repliedContent = repliedTo.getContentDisplay();
                if (repliedContent.length() > 30) {
                    repliedContent = repliedContent.substring(0, 30) + "...";
                }
                fullMessage.append("&8[Reply to ").append(repliedUser).append(": ")
                    .append(repliedContent).append("] &r");
            }
            
            // Add main message content
            fullMessage.append(message);
            
            // Handle attachments
            if (!discordMessage.getAttachments().isEmpty()) {
                for (Message.Attachment attachment : discordMessage.getAttachments()) {
                    if (fullMessage.length() > 0) {
                        fullMessage.append(" ");
                    }
                    if (attachment.isImage()) {
                        fullMessage.append("&b[Image: ").append(attachment.getFileName()).append("]&r");
                    } else if (attachment.isVideo()) {
                        fullMessage.append("&b[Video: ").append(attachment.getFileName()).append("]&r");
                    } else {
                        fullMessage.append("&b[File: ").append(attachment.getFileName()).append("]&r");
                    }
                }
            }
            
            // Handle stickers
            if (!discordMessage.getStickers().isEmpty()) {
                for (net.dv8tion.jda.api.entities.sticker.StickerItem sticker : discordMessage.getStickers()) {
                    if (fullMessage.length() > 0) {
                        fullMessage.append(" ");
                    }
                    fullMessage.append("&d[Sticker: ").append(sticker.getName()).append("]&r");
                }
            }
            
            // Don't relay empty messages
            String finalMessage = fullMessage.toString().trim();
            if (finalMessage.isEmpty()) {
                return;
            }
            
            // Format and send to Minecraft
            sendToMinecraft(author, finalMessage, event.getMember());
        }
    }
    
    private void sendToMinecraft(User author, String message, Member member) {
        // Get display name (nickname if available, otherwise username)
        String displayName = member != null && member.getNickname() != null ? 
            member.getNickname() : author.getName();
        
        // Get user's highest role color
        String roleColor = "&b"; // Default color
        if (member != null && member.getColor() != null) {
            java.awt.Color color = member.getColor();
            roleColor = getClosestMinecraftColor(color);
        }
        
        // Process message content
        String processedMessage = message;
        
        // Convert Discord mentions to readable names
        if (showMentions && member != null && member.getGuild() != null) {
            processedMessage = convertMentions(processedMessage, member.getGuild());
        }
        
        // Convert custom emojis to text
        if (convertEmojis) {
            processedMessage = convertEmojisToText(processedMessage);
        }
        
        // Strip Discord formatting if enabled
        if (stripDiscordFormatting) {
            processedMessage = stripDiscordMarkdown(processedMessage);
        } else {
            // Convert Discord formatting to Minecraft
            processedMessage = convertDiscordFormatting(processedMessage);
        }
        
        // Make URLs clickable (just highlight them)
        processedMessage = highlightUrls(processedMessage);
        
        // Format the message with role color
        String formattedMessage = discordFormat
            .replace("%user%", roleColor + displayName + "&r")
            .replace("%message%", processedMessage);
        
        // Apply color codes (supports gradients, hex colors, etc.)
        formattedMessage = ColorUtils.parse(formattedMessage);
        
        // Check for Minecraft player mentions and notify them
        final String finalMessage = formattedMessage;
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Broadcast to all players
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(finalMessage);
                
                // Check if this player was mentioned
                if (playMentionSound && wasPlayerMentioned(player, message)) {
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    player.sendMessage(ColorUtils.parse("<gradient:#FFD700:#FFA500>»</gradient> &eYou were mentioned in Discord!"));
                }
            }
        });
        
        debug("Relayed Discord message to Minecraft: " + displayName);
    }
    
    private String convertMentions(String message, net.dv8tion.jda.api.entities.Guild guild) {
        String result = message;
        
        // Convert user mentions
        Matcher userMatcher = MENTION_PATTERN.matcher(result);
        StringBuffer sb = new StringBuffer();
        while (userMatcher.find()) {
            String userId = userMatcher.group(1);
            Member mentionedMember = guild.getMemberById(userId);
            String replacement = mentionedMember != null ? 
                "&b@" + mentionedMember.getEffectiveName() + "&r" : "@unknown";
            userMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        userMatcher.appendTail(sb);
        result = sb.toString();
        
        // Convert channel mentions
        Matcher channelMatcher = CHANNEL_MENTION_PATTERN.matcher(result);
        sb = new StringBuffer();
        while (channelMatcher.find()) {
            String channelId = channelMatcher.group(1);
            TextChannel channel = guild.getTextChannelById(channelId);
            String replacement = channel != null ? "&9#" + channel.getName() + "&r" : "#unknown";
            channelMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        channelMatcher.appendTail(sb);
        result = sb.toString();
        
        // Convert role mentions
        Matcher roleMatcher = ROLE_MENTION_PATTERN.matcher(result);
        sb = new StringBuffer();
        while (roleMatcher.find()) {
            String roleId = roleMatcher.group(1);
            net.dv8tion.jda.api.entities.Role role = guild.getRoleById(roleId);
            String replacement = role != null ? "&d@" + role.getName() + "&r" : "@unknown-role";
            roleMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        roleMatcher.appendTail(sb);
        
        return sb.toString();
    }
    
    private String convertEmojisToText(String message) {
        // Convert custom Discord emojis to :name: format
        Matcher matcher = EMOJI_PATTERN.matcher(message);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String emojiName = matcher.group(1);
            matcher.appendReplacement(sb, "&e:" + emojiName + ":&r");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
    
    private String convertDiscordFormatting(String message) {
        // Convert Discord markdown to Minecraft color codes
        String result = message;
        result = result.replaceAll("\\*\\*\\*(.+?)\\*\\*\\*", "&l&o$1&r"); // Bold italic
        result = result.replaceAll("\\*\\*(.+?)\\*\\*", "&l$1&r");         // Bold
        result = result.replaceAll("\\*(.+?)\\*", "&o$1&r");               // Italic
        result = result.replaceAll("__(.+?)__", "&n$1&r");                 // Underline
        result = result.replaceAll("~~(.+?)~~", "&m$1&r");                 // Strikethrough
        result = result.replaceAll("`(.+?)`", "&7$1&r");                   // Code
        result = result.replaceAll("\\|\\|(.+?)\\|\\|", "&8[spoiler]&r");  // Spoiler
        return result;
    }
    
    private String stripDiscordMarkdown(String message) {
        String result = message;
        result = result.replaceAll("\\*{1,3}|_{1,2}|~~|`{1,3}|\\|\\|", "");
        return result;
    }
    
    private String highlightUrls(String message) {
        return URL_PATTERN.matcher(message).replaceAll("&9&n$1&r");
    }
    
    private boolean wasPlayerMentioned(Player player, String message) {
        String lowerMessage = message.toLowerCase();
        String playerName = player.getName().toLowerCase();
        return lowerMessage.contains("@" + playerName) || 
               lowerMessage.contains(playerName) ||
               lowerMessage.contains("@everyone") ||
               lowerMessage.contains("@here");
    }
    
    private String getClosestMinecraftColor(java.awt.Color color) {
        // Map Discord role colors to closest Minecraft colors
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        
        // Simple color matching based on dominant color
        if (r > 200 && g < 100 && b < 100) return "&c"; // Red
        if (r > 200 && g > 100 && b < 100) return "&6"; // Gold/Orange
        if (r > 200 && g > 200 && b < 100) return "&e"; // Yellow
        if (r < 100 && g > 200 && b < 100) return "&a"; // Green
        if (r < 100 && g > 200 && b > 200) return "&b"; // Aqua
        if (r < 100 && g < 100 && b > 200) return "&9"; // Blue
        if (r > 200 && g < 100 && b > 200) return "&d"; // Light Purple
        if (r > 150 && g > 150 && b > 150) return "&f"; // White
        if (r < 100 && g < 100 && b < 100) return "&8"; // Dark Gray
        
        return "&b"; // Default aqua
    }
    
    private TextChannel getChatChannel() {
        if (plugin.getDiscordManager() == null || !plugin.getDiscordManager().isReady()) {
            debug("Discord manager not ready");
            return null;
        }

        // Safeguard against a null or empty channel name
        if (chatChannelName == null || chatChannelName.isEmpty()) {
            debug("chatChannelName is not configured");
            return null;
        }
        
        debug("Looking for channel: " + chatChannelName);
        
        // First, try to resolve the channel by ID if the value is numeric
        if (chatChannelName.matches("\\d+")) {
            TextChannel channel = plugin.getDiscordManager().getTextChannelById(chatChannelName);
            if (channel != null) {
                debug("Found channel by ID: " + channel.getName());
                return channel;
            }
        }
        
        // Check main guild
        net.dv8tion.jda.api.entities.Guild guild = plugin.getDiscordManager().getMainGuild();
        if (guild == null) {
            warning("Main guild not found! Check your guild-id in config");
            return null;
        }
        
        // Look up the channel by name (case-insensitive)
        java.util.List<TextChannel> channels = guild.getTextChannelsByName(chatChannelName, true);
        
        if (!channels.isEmpty()) {
            TextChannel channel = channels.get(0);
            debug("Found channel by name: " + channel.getName() + " (ID: " + channel.getId() + ")");
            return channel;
        }
        
        // If not found, list available channels for debugging
        debug("Channel '" + chatChannelName + "' not found. Available channels:");
        for (TextChannel tc : guild.getTextChannels()) {
            debug("  - " + tc.getName() + " (ID: " + tc.getId() + ")");
        }
        
        warning("Could not find channel: " + chatChannelName);
        return null;
    }
    
    /**
     * Send a system message to Discord (like join/leave notifications)
     */
    public void sendSystemMessage(String message, Color color, String channelId) {
        sendSystemMessageWithPlayer(message, color, channelId, null);
    }
    
    /**
     * Send a system message with player avatar via webhook or embed
     */
    public void sendSystemMessageWithPlayer(String message, Color color, String channelId, Player player) {
        // Get the effective webhook URL (auto-managed or manual)
        String effectiveWebhookUrl = managedWebhookUrl != null ? managedWebhookUrl : webhookUrl;

        String webhookName = player != null ? player.getName() : "Server";
        String avatarUrl = player != null ? WebhookManager.getPlayerAvatarUrl(player.getUniqueId(), player.getName(), skinProvider) : null;

        debug("[Join/Leave] useWebhook=" + useWebhook + ", webhookUrl=" + (effectiveWebhookUrl != null ? "set" : "null") + ", player=" + (player != null ? player.getName() : "null"));

        // Try webhook - use player name/avatar when available so events look like they come from the player
        if (useWebhook && effectiveWebhookUrl != null && !effectiveWebhookUrl.isEmpty()) {
            debug("[Join/Leave] Sending via webhook");
            webhookManager.sendMessage(effectiveWebhookUrl, webhookName, avatarUrl, message)
                .thenAccept(success -> {
                    if (success) {
                        debug("[Join/Leave] Webhook SUCCESS!");
                    } else {
                        warning("[Join/Leave] Webhook FAILED, using fallback");
                        sendSystemMessageFallbackWithPlayer(message, color, channelId, player);
                    }
                });
            return;
        }

        // Use embed with player avatar (no webhook)
        debug("[Join/Leave] Using embed fallback");
        sendSystemMessageFallbackWithPlayer(message, color, channelId, player);
    }
    
    private void sendSystemMessageFallback(String message, Color color, String channelId) {
        sendSystemMessageFallbackWithPlayer(message, color, channelId, null);
    }
    
    private void sendSystemMessageFallbackWithPlayer(String message, Color color, String channelId, Player player) {
        TextChannel channel = null;
        if (channelId != null && !channelId.isEmpty()) {
            channel = plugin.getDiscordManager().getTextChannelById(channelId);
        }

        if (channel == null) {
            channel = getChatChannel();
        }

        if (channel == null) return;

        // Always use embed with player avatar when available
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(color != null ? color : Color.GRAY)
            .setDescription(message)
            .setTimestamp(Instant.now());
        
        // Add player avatar if available
        if (player != null) {
            String avatarUrl = WebhookManager.getPlayerAvatarUrl(player.getUniqueId(), player.getName(), skinProvider);
            embed.setThumbnail(avatarUrl);
            embed.setAuthor(player.getName(), null, avatarUrl);
        }
        
        channel.sendMessageEmbeds(embed.build()).queue();
    }

    public void sendSystemMessage(String message, Color color) {
        sendSystemMessage(message, color, null);
    }
    
    /**
     * Get the events channel ID (falls back to chat channel)
     */
    private String getEventsChannelId() {
        if (eventsChannelId != null && !eventsChannelId.isEmpty()) {
            return eventsChannelId;
        }
        // Fallback to chat channel
        return chatChannelName;
    }
    
    /**
     * Send a player join message to Discord as embed with player avatar
     */
    public void sendJoinMessage(Player player) {
        String message = plugin.getConfig().getString("messages.join-message", 
            "%player% joined the server");
        message = message.replace("%player%", player.getName());
        
        sendPlayerEventEmbed(player, message, new Color(0, 255, 0), "➡️ Player Joined");
    }
    
    /**
     * Send a player leave message to Discord as embed with player avatar
     */
    public void sendLeaveMessage(Player player) {
        String message = plugin.getConfig().getString("messages.leave-message", 
            "%player% left the server");
        message = message.replace("%player%", player.getName());
        
        sendPlayerEventEmbed(player, message, new Color(255, 0, 0), "⬅️ Player Left");
    }
    
    /**
     * Send a death message to Discord as embed with player avatar
     */
    public void sendDeathMessage(Player player, String deathMessage) {
        String message = plugin.getConfig().getString("messages.death-message", 
            "%player% %death_message%");
        message = message.replace("%player%", player.getName())
                        .replace("%death_message%", deathMessage);
        
        sendPlayerEventEmbed(player, message, new Color(255, 165, 0), "💀 Player Died");
    }
    
    /**
     * Send player event as embed with player avatar
     */
    private void sendPlayerEventEmbed(Player player, String message, Color color, String title) {
        TextChannel channel = null;
        String channelId = getEventsChannelId();
        
        if (channelId != null && !channelId.isEmpty()) {
            channel = plugin.getDiscordManager().getTextChannelById(channelId);
        }
        if (channel == null) {
            channel = getChatChannel();
        }
        if (channel == null) return;
        
        // Get player avatar from crafthead.net
        String avatarUrl = WebhookManager.getPlayerAvatarUrl(player.getUniqueId(), player.getName(), skinProvider);
        
        EmbedBuilder embed = new EmbedBuilder()
            .setAuthor(player.getName(), null, avatarUrl)
            .setDescription(message)
            .setThumbnail(avatarUrl)
            .setColor(color)
            .setFooter(title)
            .setTimestamp(Instant.now());
        
        channel.sendMessageEmbeds(embed.build()).queue(
            success -> debug("[Events] Sent " + title + " embed for " + player.getName()),
            error -> warning("[Events] Failed to send embed: " + error.getMessage())
        );
    }
    
    /**
     * Send server start message to Discord
     */
    public void sendServerStartMessage() {
        String message = plugin.getConfig().getString("messages.server-start", 
            ":white_check_mark: **Server has started!**");
        sendServerMessage(message, new Color(0, 255, 0));
    }
    
    /**
     * Send server stop message to Discord
     */
    public void sendServerStopMessage() {
        String message = plugin.getConfig().getString("messages.server-stop", 
            ":octagonal_sign: **Server is stopping...**");
        sendServerMessage(message, new Color(255, 0, 0));
    }
    
    /**
     * Send a server system message via webhook or embed
     */
    private void sendServerMessage(String message, Color color) {
        // Get the effective webhook URL
        String effectiveWebhookUrl = managedWebhookUrl != null ? managedWebhookUrl : webhookUrl;
        
        // Try webhook with server icon
        if (useWebhook && effectiveWebhookUrl != null && !effectiveWebhookUrl.isEmpty()) {
            String serverIcon = "https://crafthead.net/avatar/MHF_Steve/128"; // Default server icon
            webhookManager.sendMessage(effectiveWebhookUrl, "Server", serverIcon, message)
                .thenAccept(success -> {
                    if (!success) {
                        sendServerMessageFallback(message, color);
                    }
                });
            return;
        }
        
        sendServerMessageFallback(message, color);
    }
    
    private void sendServerMessageFallback(String message, Color color) {
        TextChannel channel = getChatChannel();
        if (channel == null) return;
        
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(color)
            .setDescription(message)
            .setTimestamp(Instant.now());
        
        channel.sendMessageEmbeds(embed.build()).queue();
    }
}